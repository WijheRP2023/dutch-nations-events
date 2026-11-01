package nl.dutchnations.server;

import com.google.gson.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import static nl.dutchnations.server.BattleshipSheets.map;

/** One-way public projection. Never reads private fleets and never copies source formulas. */
final class EventViewSync
{
    static final String BINDING = "DN_EVENT_VIEW_SOURCE";
    static final List<String> TABS = List.of("Tussenstand", "Team 1", "Team 2");
    private final String source;
    private final BattleshipSheets google;
    private String lastFingerprint = "";
    private long lastFullSync;
    private long lastLayoutSync, metadataAt, retryAfter;
    private JsonObject sourceMetadata;
    private List<Object> publishedPlan;
    private String publishedTarget = "";
    private String sourceCardMode = "", sourceCardFingerprint = "";
    private long sourceCardAt, sourceCardRetryAfter;
    static final int POLL_SECONDS = 5;
    volatile String lastChecked = "";
    volatile long durationMillis;
    volatile long lastUpdateMillis;
    volatile String lastSuccess = "", state = "WACHTEN";

    EventViewSync()
    {
        source = System.getenv("DN_BATTLESHIP_PUBLIC_SHEET");
        BattleshipSheets connection = null;
        try { if (source != null && System.getenv("DN_GOOGLE_SERVICE_ACCOUNT_JSON") != null)
            connection = new BattleshipSheets(System.getenv("DN_GOOGLE_SERVICE_ACCOUNT_JSON")); }
        catch (IOException ignored) { state = "GOOGLE_TOEGANG_ONTBREEKT"; }
        google = connection;
    }

    void tick()
    {
        if (google == null || source == null) return;
        if (System.currentTimeMillis() < retryAfter) return;
        long started = System.currentTimeMillis();
        String stage="BRON_LEZEN";
        try
        {
            if (sourceMetadata == null || started - metadataAt >= 60_000)
            {
                sourceMetadata=get(source,"","sheets(properties(title,sheetId,gridProperties(rowCount)))");
                metadataAt=started;
            }
            JsonObject sourceMeta=sourceMetadata;
            List<List<List<Object>>> values = google.batchValues(source, "Zeeslagdata!A6:B6", "Tussenstand!A1:Q75",
                "'Team 1'!A6:V"+rowCount(sourceMeta,"Team 1"), "'Team 2'!A6:V"+rowCount(sourceMeta,"Team 2"),
                "Borddata!C2:C50", "Drops!B2:B3000", "Drops!N2:N3000", "Instellingen!B10:F10",
                "Instellingen!A19:A"+rowCount(sourceMeta,"Instellingen"), "Instellingen!E19:E"+rowCount(sourceMeta,"Instellingen"),"Tussenstand!A82");
            String target = string(values.get(0), 0, 0);
            if (target.isEmpty()) { state = "NIET_INGESTELD"; return; }
            BattleshipSheets.id(target);
            if (target.equals(source) || target.equals(System.getenv("DN_BATTLESHIP_TEAM_ONE_SHEET"))
                || target.equals(System.getenv("DN_BATTLESHIP_TEAM_TWO_SHEET"))) throw new IOException("Ongeldig kijkbestand");
            String fingerprint = new Gson().toJson(values);
            lastChecked = Instant.now().toString();
            syncSourceCardVisibility(values,fingerprint,started);
            if (fingerprint.equals(lastFingerprint)&&System.currentTimeMillis()-lastFullSync<300_000) { state = "ACTUEEL"; return; }
            stage="KOPPELING_CONTROLEREN";
            JsonObject destination = get(target, "", "sheets(properties,merges,conditionalFormats,protectedRanges),developerMetadata");
            checkDestination(source, target, destination);
            List<String> ranges = new ArrayList<>(List.of("Tussenstand!A1:Q75", "'Team 1'!A1:V5", "'Team 2'!A1:V5"));
            List<List<Integer>> selected = List.of(selected(values.get(2)), selected(values.get(3)));
            for (int t=0;t<2;t++) ranges.addAll(rowRanges("Team " + (t+1), selected.get(t)));
            stage="WEERGAVE_LEZEN";
            JsonObject snapshot = new JsonObject(); JsonArray sheets = new JsonArray(); snapshot.add("sheets", sheets);
            for (int i=0;i<ranges.size();i+=60)
            {
                StringBuilder query = new StringBuilder();
                for (String range:ranges.subList(i,Math.min(i+60,ranges.size()))) query.append("&ranges=").append(enc(range));
                JsonObject part = get(source, query.toString(),
                    "sheets(properties,merges,data(startRow,startColumn,rowData(values(userEnteredValue,effectiveValue,formattedValue,effectiveFormat,dataValidation)),rowMetadata(pixelSize,hiddenByUser),columnMetadata(pixelSize,hiddenByUser)))");
                for (JsonElement sheet:part.getAsJsonArray("sheets")) sheets.add(sheet);
            }
            String time=DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss").withZone(ZoneId.of("Europe/Amsterdam")).format(Instant.now());
            stage="WEERGAVE_OPBOUWEN";
            List<Object> requests = plan(source, target, destination, snapshot, values, selected, time);
            boolean layoutRefresh = !target.equals(publishedTarget) || started-lastLayoutSync >= 300_000;
            List<Object> changes = layoutRefresh ? requests : incremental(publishedPlan, requests);
            stage="PUBLICEREN";
            if (!changes.isEmpty()) google.requestForView("POST", api(target)+":batchUpdate", map("requests",changes));
            if (changes == requests) lastLayoutSync=System.currentTimeMillis();
            publishedPlan=requests; publishedTarget=target;
            lastFingerprint = fingerprint; lastFullSync=System.currentTimeMillis(); lastSuccess = Instant.now().toString(); lastUpdateMillis=System.currentTimeMillis()-started; state = "ACTUEEL";
        }
        catch (Exception failure)
        {
            state = "FOUT_"+stage;
            sourceMetadata=null;
            String hint=failure.getMessage()!=null&&failure.getMessage().startsWith("Google-koppeling: HTTP ")?failure.getMessage():"";
            retryAfter=System.currentTimeMillis()+(hint.contains("429")?60_000:10_000);
            System.err.println("Kijkbestand niet ververst ("+stage+"). "+hint+" De laatst geldige weergave blijft behouden.");
        }
        finally { durationMillis=System.currentTimeMillis()-started; }
    }

    private void syncSourceCardVisibility(List<List<List<Object>>> values,String fingerprint,long started)
    {
        String mode=string(values.get(1),7,1);
        if(values.size()<11||!"DN_POINTS_CARDS_V1".equals(string(values.get(10),0,0))||!List.of("Bingo","Punten","Vier-op-een-rij","Zeeslag").contains(mode)||started<sourceCardRetryAfter)return;
        if(mode.equals(sourceCardMode)&&started-sourceCardAt<60_000&&(!mode.equals("Punten")||fingerprint.equals(sourceCardFingerprint)))return;
        try
        {
            for(JsonElement element:sourceMetadata.getAsJsonArray("sheets"))
            {
                JsonObject properties=element.getAsJsonObject().getAsJsonObject("properties");
                if(properties.get("title").getAsString().equals("Tussenstand"))
                {
                    google.request("POST",api(source)+":batchUpdate",map("requests",sourceCardVisibility(properties.get("sheetId").getAsInt(),mode)));
                    sourceCardMode=mode;sourceCardFingerprint=fingerprint;sourceCardAt=started;return;
                }
            }
        }
        catch(Exception ignored)
        {
            sourceCardRetryAfter=started+60_000;
            System.err.println("Bronbordopmaak niet vernieuwd; de ledenweergave blijft onafhankelijk synchroniseren.");
        }
    }

    /** Formatting only, gated by the installed panel marker; never changes source values or formulas. */
    static List<Object> sourceCardVisibility(int id,String mode)
    {
        List<Object> requests=new ArrayList<>();hide(requests,id,12,94);
        int[][] visible=mode.equals("Punten")?new int[][]{{82,94}}:mode.equals("Zeeslag")?new int[][]{{51,70}}:new int[][]{{12,14},{15,mode.equals("Bingo")?25:29}};
        for(int[] range:visible)requests.add(map("updateDimensionProperties",map("range",map("sheetId",id,"dimension","ROWS","startIndex",range[0],"endIndex",range[1]),"properties",map("hiddenByUser",false),"fields","hiddenByUser")));
        if(mode.equals("Punten"))hide(requests,id,84,85);
        return requests;
    }

    /** Merge neighbouring active rows: one grid read instead of one range per drop. */
    static List<String> rowRanges(String tab, List<Integer> rows)
    {
        List<String> ranges=new ArrayList<>();
        for(int i=0;i<rows.size();)
        {
            int start=rows.get(i),end=start;i++;
            while(i<rows.size()&&rows.get(i)<=end+4)end=rows.get(i++);
            ranges.add("'"+tab+"'!A"+start+":R"+end);
        }
        return ranges;
    }

    /** Keep unchanged cells (especially IMAGE formulas) and the entire layout intact. */
    static List<Object> incremental(List<Object> previous,List<Object> next)
    {
        // Merges can change cell geometry. Only those changes and periodic repair require a rebuild.
        if(previous==null)return next;
        List<Object> oldPlan=jsonPlan(previous),newPlan=jsonPlan(next);
        if(!operations(oldPlan,"mergeCells").equals(operations(newPlan,"mergeCells")))return next;
        SortedMap<Long,JsonObject> before=cells(oldPlan),after=cells(newPlan);
        SortedSet<Long> keys=new TreeSet<>(before.keySet());keys.addAll(after.keySet());
        List<Object> changes=new ArrayList<>();
        long first=-1,last=-1;JsonArray changed=new JsonArray();
        for(long key:keys)
        {
            JsonObject cell=after.getOrDefault(key,new JsonObject());
            if(cell.equals(before.getOrDefault(key,new JsonObject())))continue;
            if(first>=0&&(key!=last+1||(key>>>16)!=(last>>>16)))
            {
                addCells(changes,first,changed);changed=new JsonArray();first=-1;
            }
            if(first<0)first=key;last=key;changed.add(cell);
        }
        if(first>=0)addCells(changes,first,changed);
        // Selection/goal changes must update visibility and input protection, not erase the workbook.
        for(String operation:List.of("updateDimensionProperties","updateSheetProperties"))
            if(!operations(oldPlan,operation).equals(operations(newPlan,operation)))changes.addAll(operations(newPlan,operation));
        if(!operations(oldPlan,"addProtectedRange").equals(operations(newPlan,"addProtectedRange")))
        {
            changes.addAll(operations(newPlan,"deleteProtectedRange"));
            changes.addAll(operations(newPlan,"addProtectedRange"));
        }
        return changes;
    }

    private static void addCells(List<Object> changes,long first,JsonArray changed)
    {
        changes.add(map("updateCells",map("start",map("sheetId",(int)(first>>>32),"rowIndex",(int)((first>>>16)&65535),"columnIndex",(int)(first&65535)),
            "rows",List.of(map("values",changed)),"fields","userEnteredValue,userEnteredFormat,dataValidation")));
    }

    private static SortedMap<Long,JsonObject> cells(List<Object> plan)
    {
        SortedMap<Long,JsonObject> out=new TreeMap<>();
        for(JsonObject request:operations(plan,"updateCells"))
        {
            JsonObject u=request.getAsJsonObject("updateCells"),s=u.getAsJsonObject("start");
            int sheet=s.get("sheetId").getAsInt(),row=s.get("rowIndex").getAsInt(),col=s.get("columnIndex").getAsInt();
            JsonArray rows=u.getAsJsonArray("rows");
            for(int r=0;r<rows.size();r++)
            {
                JsonArray values=rows.get(r).getAsJsonObject().getAsJsonArray("values");
                for(int c=0;c<values.size();c++)out.put(((long)sheet<<32)|((long)(row+r)<<16)|(col+c),values.get(c).getAsJsonObject());
            }
        }
        return out;
    }

    private static List<JsonObject> operations(List<Object> plan,String type)
    {
        List<JsonObject> out=new ArrayList<>();Gson gson=new Gson();
        for(Object request:plan)
        {
            JsonObject r=request instanceof JsonObject?(JsonObject)request:gson.toJsonTree(request).getAsJsonObject();
            if(r.has(type))out.add(r);
        }
        return out;
    }

    private static List<Object> jsonPlan(List<Object> plan)
    { List<Object> out=new ArrayList<>();for(JsonElement r:new Gson().toJsonTree(plan).getAsJsonArray())out.add(r.getAsJsonObject());return out; }

    private JsonObject get(String file, String query, String fields) throws IOException
    { return google.request("GET",api(file)+"?fields="+enc(fields)+query,null,16_000_000); }
    private static String api(String id) { return "https://sheets.googleapis.com/v4/spreadsheets/"+BattleshipSheets.id(id); }
    private static String enc(String s) { return URLEncoder.encode(s,StandardCharsets.UTF_8); }
    private static String string(List<List<Object>> data,int r,int c)
    { return r<data.size()&&c<data.get(r).size()?String.valueOf(data.get(r).get(c)):""; }
    private static int rowCount(JsonObject meta,String name) throws IOException
    {
        for(JsonElement e:meta.getAsJsonArray("sheets"))
        { JsonObject p=e.getAsJsonObject().getAsJsonObject("properties"); if(name.equals(p.get("title").getAsString()))
          {int n=p.getAsJsonObject("gridProperties").get("rowCount").getAsInt();if(n>=6&&n<=6000)return n;} }
        throw new IOException("Ongeldige teampagina");
    }

    static List<Integer> selected(List<List<Object>> rows) throws IOException
    {
        List<Integer> out=new ArrayList<>();
        for(int i=0;i<rows.size();i++) if(rows.get(i).size()>17&&Boolean.TRUE.equals(rows.get(i).get(17))) out.add(i+6);
        if(out.size()>500) throw new IOException("Te veel actieve drops");
        return out;
    }

    static void checkDestination(String source,String target,JsonObject meta) throws IOException
    {
        if(source.equals(target)) throw new IOException("Bron en kijkbestand moeten verschillen");
        Set<String> found=new HashSet<>();
        for(JsonElement s:meta.getAsJsonArray("sheets")) found.add(s.getAsJsonObject().getAsJsonObject("properties").get("title").getAsString());
        if(!found.equals(new HashSet<>(TABS))||meta.getAsJsonArray("sheets").size()!=3) throw new IOException("Kijkbestand heeft andere tabbladen");
        boolean bound=false;
        if(meta.has("developerMetadata")) for(JsonElement e:meta.getAsJsonArray("developerMetadata"))
        {
            JsonObject m=e.getAsJsonObject();
            if(m.has("metadataKey")&&BINDING.equals(m.get("metadataKey").getAsString())&&m.has("metadataValue")
                &&source.equals(m.get("metadataValue").getAsString())) bound=true;
        }
        if(!bound) throw new IOException("Kijkbestand is niet aan deze bron gekoppeld");
    }

    static List<Object> plan(String source,String target,JsonObject destination,JsonObject snapshot,
        List<List<List<Object>>> values,List<List<Integer>> selected,String time) throws IOException
    {
        checkDestination(source,target,destination);
        Map<String,String> icons=new HashMap<>();
        for(int r=0;r<values.get(5).size();r++) icons.put(string(values.get(5),r,0),string(values.get(6),r,0));
        Map<String,Map<Integer,List<JsonObject>>> cells=new HashMap<>();
        Map<String,JsonObject> structure=new HashMap<>();
        Map<String,Map<Integer,JsonObject>> heights=new HashMap<>();
        Map<String,JsonArray> widths=new HashMap<>();
        for(JsonElement e:snapshot.getAsJsonArray("sheets"))
        {
            JsonObject sh=e.getAsJsonObject(); String name=sh.getAsJsonObject("properties").get("title").getAsString();
            if(!TABS.contains(name)) continue;
            structure.putIfAbsent(name,sh);
            Map<Integer,List<JsonObject>> rows=cells.computeIfAbsent(name,k->new HashMap<>());
            for(JsonElement g:array(sh,"data"))
            {
                JsonObject grid=g.getAsJsonObject();int start=grid.has("startRow")?grid.get("startRow").getAsInt():0;
                if(!widths.containsKey(name)&&array(grid,"columnMetadata").size()>0) widths.put(name,array(grid,"columnMetadata"));
                JsonArray dims=array(grid,"rowMetadata");
                for(int r=0;r<dims.size();r++)heights.computeIfAbsent(name,k->new HashMap<>()).put(start+r,dims.get(r).getAsJsonObject());
                JsonArray data=array(grid,"rowData");
                for(int r=0;r<data.size();r++)
                { List<JsonObject> list=new ArrayList<>(); for(JsonElement c:array(data.get(r).getAsJsonObject(),"values")) list.add(c.getAsJsonObject()); rows.put(start+r,list); }
            }
        }
        List<Object> requests=new ArrayList<>();
        String mode=string(values.get(1),7,1);
        boolean inputEnabled=values.size()>=10&&"REGISTRATIES_V1".equals(string(values.get(0),0,1));
        String epoch=inputEnabled?inputEpoch(source,values.get(7)):"";
        for(JsonElement e:destination.getAsJsonArray("sheets"))
        {
            JsonObject dest=e.getAsJsonObject(), prop=dest.getAsJsonObject("properties");String name=prop.get("title").getAsString();int id=prop.get("sheetId").getAsInt();
            if(!cells.containsKey(name)) throw new IOException("Onvolledige bronweergave");
            for(JsonElement pr:array(dest,"protectedRanges")) requests.add(map("deleteProtectedRange",map("protectedRangeId",pr.getAsJsonObject().get("protectedRangeId").getAsInt())));
            for(int i=array(dest,"conditionalFormats").size()-1;i>=0;i--) requests.add(map("deleteConditionalFormatRule",map("sheetId",id,"index",i)));
            requests.add(map("clearBasicFilter",map("sheetId",id)));
            requests.add(map("unmergeCells",map("range",map("sheetId",id))));
            requests.add(map("repeatCell",map("range",map("sheetId",id),"cell",map(),"fields","userEnteredValue,userEnteredFormat,dataValidation,note,textFormatRuns")));
            boolean board=name.equals("Tussenstand"); int team=name.equals("Team 2")?1:0;
            if(inputEnabled&&!board&&!selected.get(team).isEmpty())
            {
                int needed=selected.get(team).get(selected.get(team).size()-1),existing=prop.getAsJsonObject("gridProperties").get("rowCount").getAsInt();
                if(needed>existing)
                {
                    requests.add(map("updateSheetProperties",map("properties",map("sheetId",id,"gridProperties",map("rowCount",needed)),"fields","gridProperties.rowCount")));
                    prop.getAsJsonObject("gridProperties").addProperty("rowCount",needed);
                }
            }
            List<Integer> rowMap=new ArrayList<>(); for(int r=0;r<(board?75:5);r++) rowMap.add(r);
            if(!board) for(int r:selected.get(team))rowMap.add(r-1);
            List<Object> output=new ArrayList<>();
            List<Object> editable=new ArrayList<>();
            List<String> players=inputEnabled?teamPlayers(values.get(8+team),values.get(9-team)):List.of();
            for(int outRow=0;outRow<rowMap.size();outRow++)
            {
                int oldRow=rowMap.get(outRow); List<JsonObject> input=cells.get(name).getOrDefault(oldRow,List.of());List<Object> row=new ArrayList<>();
                for(int c=0;c<(board?17:22);c++) row.add(project(input.size()>c?input.get(c):new JsonObject(),board,oldRow,c,mode,values.get(4),icons));
                if(inputEnabled&&!board&&oldRow>=5)
                {
                    int goal=goal(input.size()>2?input.get(2):new JsonObject());
                    for(int c=3;c<10;c++)
                    {
                        @SuppressWarnings("unchecked") Map<String,Object> cell=(Map<String,Object>)row.get(c);
                        if(c-3<goal&&!players.isEmpty()&&!epoch.isEmpty()) cell.put("dataValidation",map("condition",map("type","ONE_OF_LIST","values",players.stream().map(p->map("userEnteredValue",p)).collect(java.util.stream.Collectors.toList())),"strict",true,"showCustomUi",true));
                    }
                    if(goal>0&&!players.isEmpty()&&!epoch.isEmpty())editable.add(map("sheetId",id,"startRowIndex",oldRow,"endRowIndex",oldRow+1,"startColumnIndex",3,"endColumnIndex",3+goal));
                }
                output.add(map("values",row));
            }
            if(board)
            {
                @SuppressWarnings("unchecked") List<Object> first=(List<Object>)((Map<?,?>)output.get(0)).get("values");
                first.set(0,map("userEnteredValue",map("stringValue","Stand bijgewerkt: "+time+" · alleen lezen"),"userEnteredFormat",map("textFormat",map("fontSize",9,"fontFamily","Arial"))));
                @SuppressWarnings("unchecked") List<Object> title=(List<Object>)((Map<?,?>)output.get(1)).get("values");
                title.set(0,map("userEnteredValue",map("stringValue",gameTitle(mode))));
                if(mode.equals("Punten"))pointCards(output,values.get(1),values.get(2),values.get(3),selected);
            }
            if(inputEnabled&&!board)
            {
                @SuppressWarnings("unchecked") List<Object> first=(List<Object>)((Map<?,?>)output.get(0)).get("values");
                first.set(0,map("userEnteredValue",map("stringValue","Leden kijken mee. Bewerkers kunnen spelers per drop invullen.")));
                @SuppressWarnings("unchecked") List<Object> guide=(List<Object>)((Map<?,?>)output.get(3)).get("values");
                String help=epoch.isEmpty()?"Vul eerst start- en einddatum met tijd in bij Instellingen in de basis.":players.isEmpty()?"Vul eerst unieke spelers voor dit team in bij Instellingen in de basis.":"Kies één speler per dropvak. De beveiligde koppeling werkt dit bij in de basis. Instellingen blijven daar.";
                guide.set(0,map("userEnteredValue",map("stringValue",help)));
                first.set(17,map("userEnteredValue",map("stringValue",epoch)));
                // Stable source row addresses prevent a queued edit from being assigned to another drop after selections change.
                for(int firstRow=0;firstRow<rowMap.size();)
                {
                    int end=firstRow+1;while(end<rowMap.size()&&rowMap.get(end)==rowMap.get(end-1)+1)end++;
                    requests.add(map("updateCells",map("start",map("sheetId",id,"rowIndex",rowMap.get(firstRow),"columnIndex",0),"rows",output.subList(firstRow,end),"fields","userEnteredValue,userEnteredFormat,dataValidation")));
                    firstRow=end;
                }
            }
            else requests.add(map("updateCells",map("start",map("sheetId",id,"rowIndex",0,"columnIndex",0),"rows",output,"fields","userEnteredValue,userEnteredFormat,dataValidation")));
            for(JsonElement m:array(structure.get(name),"merges"))
            {
                JsonObject merge=m.getAsJsonObject().deepCopy();int end=merge.get("endRowIndex").getAsInt();
                int start=merge.has("startRowIndex")?merge.get("startRowIndex").getAsInt():0;
                if(board&&start<=1&&end>1)continue;
                if(board&&mode.equals("Punten")&&start<24&&end>12)continue;
                if(end>(board?75:5))continue;merge.addProperty("sheetId",id);requests.add(map("mergeCells",map("range",merge,"mergeType","MERGE_ALL")));
            }
            requests.add(map("updateSheetProperties",map("properties",map("sheetId",id,"hidden",false),"fields","hidden")));
            JsonArray columns=widths.getOrDefault(name,new JsonArray());
            for(int c=0;c<columns.size();c++)
                if(columns.get(c).getAsJsonObject().has("pixelSize"))requests.add(map("updateDimensionProperties",map("range",map("sheetId",id,"dimension","COLUMNS","startIndex",c,"endIndex",c+1),"properties",map("pixelSize",columns.get(c).getAsJsonObject().get("pixelSize")),"fields","pixelSize")));
            int runStart=0,lastHeight=-1;
            for(int r=0;r<=rowMap.size();r++)
            {
                JsonObject dim=r<rowMap.size()?heights.getOrDefault(name,Map.of()).get(rowMap.get(r)):null;
                int height=dim!=null&&dim.has("pixelSize")?dim.get("pixelSize").getAsInt():21;
                if(r==rowMap.size()||height!=lastHeight)
                {
                    if(r>runStart&&!(inputEnabled&&!board))requests.add(map("updateDimensionProperties",map("range",map("sheetId",id,"dimension","ROWS","startIndex",runStart,"endIndex",r),"properties",map("pixelSize",lastHeight),"fields","pixelSize")));
                    runStart=r;lastHeight=height;
                }
            }
            requests.add(map("updateDimensionProperties",map("range",map("sheetId",id,"dimension","ROWS","startIndex",0,"endIndex",prop.getAsJsonObject("gridProperties").get("rowCount").getAsInt()),"properties",map("hiddenByUser",false),"fields","hiddenByUser")));
            if(!board)
            {
                int max=prop.getAsJsonObject("gridProperties").get("rowCount").getAsInt();
                if(inputEnabled)
                {
                    hide(requests,id,5,max);
                    for(int row:selected.get(team)) requests.add(map("updateDimensionProperties",map("range",map("sheetId",id,"dimension","ROWS","startIndex",row-1,"endIndex",row),"properties",map("hiddenByUser",false,"pixelSize",28),"fields","hiddenByUser,pixelSize")));
                }
                else if(rowMap.size()<max) requests.add(map("updateDimensionProperties",map("range",map("sheetId",id,"dimension","ROWS","startIndex",rowMap.size(),"endIndex",max),"properties",map("hiddenByUser",true),"fields","hiddenByUser")));
                requests.add(map("updateDimensionProperties",map("range",map("sheetId",id,"dimension","COLUMNS","startIndex",17,"endIndex",22),"properties",map("hiddenByUser",true),"fields","hiddenByUser")));
            }
            else
            {
                int max=prop.getAsJsonObject("gridProperties").get("rowCount").getAsInt();
                boardLayout(requests,id,mode,max);
            }
            Map<String,Object> protection=map("description","DN_EVENT_VIEW_READ_ONLY","range",map("sheetId",id),"warningOnly",!inputEnabled);
            if(inputEnabled){protection.put("editors",map("users",List.of("zeeslag-server@dutch-nations-zeeslag.iam.gserviceaccount.com")));if(!board)protection.put("unprotectedRanges",editable);}
            requests.add(map("addProtectedRange",map("protectedRange",protection)));
        }
        return requests;
    }

    /** Keep calculation rows intact, but show only the active game's cards and essential controls. */
    static void boardLayout(List<Object> requests,int id,String mode,int max)
    {
        // Source formatting can finish after its values have already changed. Never
        // inherit Zeeslag's narrow separator at I when the next board starts at I.
        if(mode.equals("Zeeslag"))
        {
            columnWidth(requests,id,0,8,60);columnWidth(requests,id,8,9,24);columnWidth(requests,id,9,17,60);
        }
        else
        {
            columnWidth(requests,id,0,7,82);columnWidth(requests,id,7,8,24);columnWidth(requests,id,8,15,82);columnWidth(requests,id,15,17,68);
        }
        hide(requests,id,0,1);
        hide(requests,id,2,12);        // Keep one game title above the cards; hide summaries and diagnostics.
        hide(requests,id,14,15);       // Explanatory legend.
        hide(requests,id,29,51);       // Instructions and inactive space below the drop boards.
        if(max>70)hide(requests,id,70,max);
        Map<String,Object> title=map("sheetId",id,"startRowIndex",1,"endRowIndex",2,"startColumnIndex",0,"endColumnIndex",mode.equals("Zeeslag")?17:15);
        requests.add(map("mergeCells",map("range",title,"mergeType","MERGE_ALL")));
        requests.add(map("repeatCell",map("range",title,"cell",map("userEnteredFormat",cardStyle(20,"#E7C55A",true,"#202329")),"fields","userEnteredFormat")));
        rowHeight(requests,id,1,48);
        if(mode.equals("Zeeslag"))
        {
            hide(requests,id,12,51);   // Its own team headers, boards and shot controls start at row 52.
        }
        else
        {
            if(max>51)hide(requests,id,51,max);
            if(mode.equals("Bingo"))hide(requests,id,25,29);
            if(mode.equals("Punten"))
            {
                hide(requests,id,24,29);
                for(int start:List.of(0,8))
                {
                    for(int row:List.of(12,13,15,16,17,18,19,22,23))mergeCard(requests,id,row,start,start+7);
                    for(int row:List.of(20,21)){mergeCard(requests,id,row,start,start+3);mergeCard(requests,id,row,start+3,start+7);}
                }
                int[][] heights={{12,42},{13,28},{15,26},{16,82},{17,34},{18,24},{19,12},{20,24},{21,40},{22,12},{23,32}};
                for(int[] height:heights)rowHeight(requests,id,height[0],height[1]);
            }
        }
    }

    static String gameTitle(String mode)
    {
        if(mode.equals("Punten"))return "PUNTENCOMPETITIE";
        if(mode.equals("Vier-op-een-rij"))return "VIER OP EEN RIJ";
        return mode.toUpperCase(Locale.ROOT);
    }
    private static void mergeCard(List<Object> requests,int id,int row,int start,int end)
    {requests.add(map("mergeCells",map("range",map("sheetId",id,"startRowIndex",row,"endRowIndex",row+1,"startColumnIndex",start,"endColumnIndex",end),"mergeType","MERGE_ALL")));}
    private static void rowHeight(List<Object> requests,int id,int row,int pixels)
    {requests.add(map("updateDimensionProperties",map("range",map("sheetId",id,"dimension","ROWS","startIndex",row,"endIndex",row+1),"properties",map("pixelSize",pixels),"fields","pixelSize")));}
    private static void columnWidth(List<Object> requests,int id,int start,int end,int pixels)
    {requests.add(map("updateDimensionProperties",map("range",map("sheetId",id,"dimension","COLUMNS","startIndex",start,"endIndex",end),"properties",map("pixelSize",pixels),"fields","pixelSize")));}
    private static Map<String,Object> rgb(String hex)
    {return map("red",Integer.parseInt(hex.substring(1,3),16)/255d,"green",Integer.parseInt(hex.substring(3,5),16)/255d,"blue",Integer.parseInt(hex.substring(5,7),16)/255d);}
    private static Map<String,Object> cardStyle(int size,String color,boolean bold,String background)
    {return map("backgroundColor",rgb(background),"horizontalAlignment","CENTER","verticalAlignment","MIDDLE","wrapStrategy","WRAP","textFormat",map("fontFamily","Arial","fontSize",size,"bold",bold,"foregroundColor",rgb(color)));}
    private static double metric(List<List<Object>> rows,int row,int column) throws IOException
    {
        if(row>=rows.size()||column>=rows.get(row).size())return 0;
        Object value=rows.get(row).get(column);if(value==null||value.equals(""))return 0;
        if(!(value instanceof Number)||!Double.isFinite(((Number)value).doubleValue())||((Number)value).doubleValue()<0)throw new IOException("Ongeldige puntenstatistiek");
        return ((Number)value).doubleValue();
    }
    private static String scoreText(double number)
    {java.text.NumberFormat f=java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag("nl-NL"));f.setMaximumFractionDigits(2);return f.format(number);}
    @SuppressWarnings("unchecked")
    private static void cardRow(List<Object> output,int row,int start,int width,Object value,Map<String,Object> style)
    {
        List<Object> cells=(List<Object>)((Map<?,?>)output.get(row)).get("values");
        for(int col=start;col<start+width;col++)cells.set(col,map("userEnteredFormat",style));
        Map<String,Object> cell=(Map<String,Object>)cells.get(start);
        if(value instanceof Number)cell.put("userEnteredValue",map("numberValue",value));
        else if(value!=null&&!value.equals(""))cell.put("userEnteredValue",map("stringValue",value.toString()));
    }
    /** Generated from public calculated totals only: no roster, payment details or source formulas. */
    static void pointCards(List<Object> output,List<List<Object>> board,List<List<Object>> one,List<List<Object>> two,List<List<Integer>> selected) throws IOException
    {
        double[] points={metric(board,4,6),metric(board,5,6)};double total=points[0]+points[1];
        if(!Double.isFinite(total))throw new IOException("Ongeldige puntensom");
        for(int team=0;team<2;team++)
        {
            int start=team*8;String color=team==0?"#A52D42":"#245C88",background="#252A32";
            for(int row=12;row<24;row++)cardRow(output,row,start,7,null,cardStyle(11,"#E9EDF2",false,background));
            String name=string(board,4+team,1);if(name.isEmpty())name="Team "+(team+1);
            double own=points[team],other=points[1-team],share=total==0?0:own/total,drops=0;
            for(int sourceRow:selected.get(team))drops+=metric(team==0?one:two,sourceRow-6,10);
            cardRow(output,12,start,7,name,cardStyle(17,"#FFFFFF",true,color));
            cardRow(output,13,start,7,own==other?"GELIJKE STAND":own>other?"1E PLAATS":"2E PLAATS",cardStyle(11,"#E7C55A",true,background));
            cardRow(output,15,start,7,"TOTAALPUNTEN",cardStyle(11,"#B6C0CD",true,background));
            Map<String,Object> number=cardStyle(36,"#FFFFFF",true,background);number.put("numberFormat",map("type","NUMBER","pattern",own==Math.rint(own)?"#,##0":"#,##0.00"));
            cardRow(output,16,start,7,own,number);
            int filled=(int)Math.round(share*30);
            Map<String,Object> bar=cardStyle(19,team==0?"#EF7188":"#6FBCF2",false,background);
            bar.put("textFormat",map("fontFamily","Consolas","fontSize",19,"foregroundColor",rgb(team==0?"#EF7188":"#6FBCF2")));
            cardRow(output,17,start,7,"█".repeat(filled)+"░".repeat(30-filled),bar);
            cardRow(output,18,start,7,total==0?"Nog geen punten behaald":String.format(Locale.forLanguageTag("nl-NL"),"%.1f%% van alle behaalde punten",share*100),cardStyle(10,"#B6C0CD",false,background));
            cardRow(output,20,start,3,"DOELEN BEHAALD",cardStyle(10,"#B6C0CD",true,background));
            cardRow(output,20,start+3,4,"DROPS GEREGISTREERD",cardStyle(10,"#B6C0CD",true,background));
            cardRow(output,21,start,3,scoreText(metric(board,4+team,3))+" / "+scoreText(metric(board,4+team,4)),cardStyle(20,"#FFFFFF",true,background));
            cardRow(output,21,start+3,4,drops,cardStyle(20,"#FFFFFF",true,background));
            cardRow(output,23,start,7,own==other?"Teams staan gelijk":scoreText(Math.abs(own-other))+(own>other?" punten voorsprong":" punten achter"),cardStyle(11,"#E7C55A",true,background));
        }
    }

    static List<String> teamPlayers(List<List<Object>> own,List<List<Object>> other)
    {
        List<String> out=new ArrayList<>();Map<String,Integer> counts=new HashMap<>();
        for(List<List<Object>> team:List.of(own,other))for(List<Object> row:team)if(!row.isEmpty())counts.merge(String.valueOf(row.get(0)).trim().toLowerCase(Locale.ROOT),1,Integer::sum);
        for(List<Object> row:own)if(!row.isEmpty()){String player=String.valueOf(row.get(0)).trim();if(player.matches("[A-Za-z0-9 _-]{1,12}")&&counts.getOrDefault(player.toLowerCase(Locale.ROOT),0)==1)out.add(player);}
        return out;
    }
    static String inputEpoch(String source,List<List<Object>> period)
    {
        try{double start=Double.parseDouble(string(period,0,0)),end=Double.parseDouble(string(period,0,4));return start>0&&end>start?source+"|"+Math.round(start*86400)+"|"+Math.round(end*86400):"";}
        catch(NumberFormatException ignored){return "";}
    }
    private static int goal(JsonObject cell)
    {try{double n=cell.getAsJsonObject("effectiveValue").get("numberValue").getAsDouble();return n>=1&&n<=7&&n==Math.floor(n)?(int)n:0;}catch(RuntimeException ignored){return 0;}}

    private static void hide(List<Object> r,int id,int start,int end)
    { r.add(map("updateDimensionProperties",map("range",map("sheetId",id,"dimension","ROWS","startIndex",start,"endIndex",end),"properties",map("hiddenByUser",true),"fields","hiddenByUser"))); }

    static Map<String,Object> project(JsonObject input,boolean board,int row,int col,String mode,List<List<Object>> tiles,Map<String,String> icons) throws IOException
    {
        Map<String,Object> out=map();
        if(input.has("effectiveFormat"))out.put("userEnteredFormat",input.get("effectiveFormat"));
        JsonObject value=input.has("effectiveValue")?input.getAsJsonObject("effectiveValue"):new JsonObject();
        if(value.has("errorValue"))throw new IOException("De bron bevat een rekenfout");
        if(value.size()>0)out.put("userEnteredValue",value);
        String formula=input.has("userEnteredValue")&&input.getAsJsonObject("userEnteredValue").has("formulaValue")?input.getAsJsonObject("userEnteredValue").get("formulaValue").getAsString():"";
        if(board&&formula.contains("IMAGE(")&&(mode.equals("Bingo")||mode.equals("Vier-op-een-rij")))
        {
            Matcher modes=Pattern.compile("NOT\\(OR\\(([^)]*)\\)\\)").matcher(formula);
            if(modes.find()&&!modes.group(1).contains("\""+mode+"\""))return out;
            Matcher index=Pattern.compile("IF\\(Borddata!\\$Z\\$2=\"Bingo\";([0-9]+);([0-9]+)\\)").matcher(formula);
            if(index.find())
            {
                int tile=Integer.parseInt(index.group(mode.equals("Bingo")?1:2));
                String url=icons.getOrDefault(string(tiles,tile-1,0),"");
                if(url.matches("https://(?:oldschool\\.runescape\\.wiki|static\\.runelite\\.net|secure\\.runescape\\.com)/[^\"\\s]+"))
                    out.put("userEnteredValue",map("formulaValue","=IMAGE(\""+url+"\";1)"));
            }
        }
        if(formula.startsWith("=HYPERLINK("))
        {
            Matcher link=Pattern.compile("HYPERLINK\\(\"([^\"]+)\";\"([^\"]+)\"\\)").matcher(formula);
            if(link.find()&&(link.group(1).matches("#gid=(1058026847|1726059411|1726059412)(?:&range=[A-Z0-9:]+)?")
                ||link.group(1).startsWith("https://docs.google.com/document/d/"))) out.put("userEnteredValue",map("formulaValue","=HYPERLINK(\""+link.group(1)+"\";\""+link.group(2)+"\")"));
            else out.remove("userEnteredValue");
        }
        if(board&&row==64&&(col==3||col==4||col==7||col==12||col==13||col==16))out.remove("userEnteredValue");
        if(board&&row==70&&col==0)out.put("userEnteredValue",map("stringValue","Teamleiders schieten in het basisbestand. Dit kijkbestand is alleen-lezen."));
        if(!board&&col==0&&row==0)out.put("userEnteredValue",map("stringValue","Alleen-lezen ledenweergave. Registraties worden in het basisbestand bijgehouden."));
        if(!board&&col==0&&row==1&&value.has("stringValue"))out.put("userEnteredValue",map("stringValue",value.get("stringValue").getAsString().replace("teamoverzicht & invoer","dropvoortgang")));
        if(!board&&col==0&&row==3)out.put("userEnteredValue",map("stringValue","Hier zie je de automatisch bijgewerkte teamvoortgang. Spelleiders registreren drops in het basisbestand."));
        return out;
    }
    private static JsonArray array(JsonObject o,String key){return o!=null&&o.has(key)?o.getAsJsonArray(key):new JsonArray();}
}

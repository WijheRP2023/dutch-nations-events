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
    static final int POLL_SECONDS = 10;
    volatile String lastChecked = "";
    volatile long durationMillis;
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
                sourceMetadata=get(source,"","sheets(properties(title,gridProperties(rowCount)))");
                metadataAt=started;
            }
            JsonObject sourceMeta=sourceMetadata;
            List<List<List<Object>>> values = google.batchValues(source, "Zeeslagdata!A6", "Tussenstand!A1:Q75",
                "'Team 1'!A6:V"+rowCount(sourceMeta,"Team 1"), "'Team 2'!A6:V"+rowCount(sourceMeta,"Team 2"),
                "Borddata!C2:C50", "Drops!B2:B3000", "Drops!N2:N3000");
            String target = string(values.get(0), 0, 0);
            if (target.isEmpty()) { state = "NIET_INGESTELD"; return; }
            BattleshipSheets.id(target);
            if (target.equals(source) || target.equals(System.getenv("DN_BATTLESHIP_TEAM_ONE_SHEET"))
                || target.equals(System.getenv("DN_BATTLESHIP_TEAM_TWO_SHEET"))) throw new IOException("Ongeldig kijkbestand");
            String fingerprint = new Gson().toJson(values);
            lastChecked = Instant.now().toString();
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
            lastFingerprint = fingerprint; lastFullSync=System.currentTimeMillis(); lastSuccess = Instant.now().toString(); state = "ACTUEEL";
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
        if(previous==null||!layout(previous).equals(layout(next)))return next;
        Gson gson=new Gson(); Map<Integer,JsonObject> old=new HashMap<>();
        for(Object request:previous)
        {
            JsonObject r=gson.toJsonTree(request).getAsJsonObject();
            if(r.has("updateCells")){JsonObject u=r.getAsJsonObject("updateCells");old.put(u.getAsJsonObject("start").get("sheetId").getAsInt(),u);}
        }
        List<Object> changes=new ArrayList<>();
        for(Object request:next)
        {
            JsonObject r=gson.toJsonTree(request).getAsJsonObject();if(!r.has("updateCells"))continue;
            JsonObject u=r.getAsJsonObject("updateCells"),start=u.getAsJsonObject("start");int id=start.get("sheetId").getAsInt();
            JsonObject before=old.get(id);if(before==null)return next;
            JsonArray rows=u.getAsJsonArray("rows"),oldRows=before.getAsJsonArray("rows");
            if(rows.size()!=oldRows.size())return next;
            for(int row=0;row<rows.size();row++)
            {
                JsonArray cells=rows.get(row).getAsJsonObject().getAsJsonArray("values"),oldCells=oldRows.get(row).getAsJsonObject().getAsJsonArray("values");
                if(cells.size()!=oldCells.size())return next;
                for(int col=0;col<cells.size();)
                {
                    if(cells.get(col).equals(oldCells.get(col))){col++;continue;}
                    int first=col;JsonArray changed=new JsonArray();
                    do{changed.add(cells.get(col++));}while(col<cells.size()&&!cells.get(col).equals(oldCells.get(col)));
                    changes.add(map("updateCells",map("start",map("sheetId",id,"rowIndex",start.get("rowIndex").getAsInt()+row,"columnIndex",start.get("columnIndex").getAsInt()+first),
                        "rows",List.of(map("values",changed)),"fields",u.get("fields"))));
                }
            }
        }
        return changes;
    }

    private static JsonArray layout(List<Object> plan)
    {
        JsonArray out=new JsonArray();Gson gson=new Gson();
        for(Object request:plan)
        {
            JsonObject r=gson.toJsonTree(request).getAsJsonObject();
            if(r.has("updateDimensionProperties")||r.has("mergeCells")||r.has("updateSheetProperties"))out.add(r);
        }
        return out;
    }

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
            List<Integer> rowMap=new ArrayList<>(); for(int r=0;r<(board?75:5);r++) rowMap.add(r);
            if(!board) for(int r:selected.get(team))rowMap.add(r-1);
            List<Object> output=new ArrayList<>();
            for(int outRow=0;outRow<rowMap.size();outRow++)
            {
                int oldRow=rowMap.get(outRow); List<JsonObject> input=cells.get(name).getOrDefault(oldRow,List.of());List<Object> row=new ArrayList<>();
                for(int c=0;c<(board?17:22);c++) row.add(project(input.size()>c?input.get(c):new JsonObject(),board,oldRow,c,mode,values.get(4),icons));
                output.add(map("values",row));
            }
            if(board)
            {
                @SuppressWarnings("unchecked") List<Object> first=(List<Object>)((Map<?,?>)output.get(0)).get("values");
                first.set(0,map("userEnteredValue",map("stringValue","Stand bijgewerkt: "+time+" · alleen lezen"),"userEnteredFormat",map("textFormat",map("fontSize",9,"fontFamily","Arial"))));
            }
            requests.add(map("updateCells",map("start",map("sheetId",id,"rowIndex",0,"columnIndex",0),"rows",output,"fields","userEnteredValue,userEnteredFormat,dataValidation")));
            for(JsonElement m:array(structure.get(name),"merges"))
            {
                JsonObject merge=m.getAsJsonObject().deepCopy();int end=merge.get("endRowIndex").getAsInt();
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
                    if(r>runStart)requests.add(map("updateDimensionProperties",map("range",map("sheetId",id,"dimension","ROWS","startIndex",runStart,"endIndex",r),"properties",map("pixelSize",lastHeight),"fields","pixelSize")));
                    runStart=r;lastHeight=height;
                }
            }
            requests.add(map("updateDimensionProperties",map("range",map("sheetId",id,"dimension","ROWS","startIndex",0,"endIndex",prop.getAsJsonObject("gridProperties").get("rowCount").getAsInt()),"properties",map("hiddenByUser",false),"fields","hiddenByUser")));
            if(!board)
            {
                int max=prop.getAsJsonObject("gridProperties").get("rowCount").getAsInt();
                if(rowMap.size()<max) requests.add(map("updateDimensionProperties",map("range",map("sheetId",id,"dimension","ROWS","startIndex",rowMap.size(),"endIndex",max),"properties",map("hiddenByUser",true),"fields","hiddenByUser")));
                requests.add(map("updateDimensionProperties",map("range",map("sheetId",id,"dimension","COLUMNS","startIndex",17,"endIndex",22),"properties",map("hiddenByUser",true),"fields","hiddenByUser")));
            }
            else
            {
                int max=prop.getAsJsonObject("gridProperties").get("rowCount").getAsInt();
                if(max>75)hide(requests,id,75,max);
                hide(requests,id,33,50);
                if(!mode.equals("Zeeslag")) hide(requests,id,51,75);
                if(mode.equals("Zeeslag")||mode.equals("Punten"))hide(requests,id,15,33);
            }
            requests.add(map("addProtectedRange",map("protectedRange",map("description","DN_EVENT_VIEW_READ_ONLY","range",map("sheetId",id),"warningOnly",true))));
        }
        return requests;
    }

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

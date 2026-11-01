package nl.dutchnations.server;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class EventViewSyncTest
{
    private static final String SOURCE="source________________________",TARGET="viewer________________________";
    @Test public void independentGoogleReadsRunConcurrentlyWithoutReorderingResults() throws Exception
    {
        ExecutorService executor=Executors.newFixedThreadPool(2);CountDownLatch bothStarted=new CountDownLatch(2);
        try
        {
            EventViewSync.GoogleRead first=() -> {bothStarted.countDown();try{if(!bothStarted.await(2,TimeUnit.SECONDS))throw new java.io.IOException("Reads ran sequentially");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.io.IOException(e);}return json("{'first':true}");};
            EventViewSync.GoogleRead second=() -> {bothStarted.countDown();try{if(!bothStarted.await(2,TimeUnit.SECONDS))throw new java.io.IOException("Reads ran sequentially");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.io.IOException(e);}return json("{'second':true}");};
            List<JsonObject> result=EventViewSync.parallelReads(executor,first,second);
            assertTrue(result.get(0).get("first").getAsBoolean());assertTrue(result.get(1).get("second").getAsBoolean());
        }
        finally{executor.shutdownNow();}
    }
    @Test public void failedReadNeverReturnsPartialPublicationData() throws Exception
    {
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try
        {
            try{EventViewSync.parallelReads(executor,() -> {throw new java.io.IOException("binding denied");},() -> json("{'public':true}"));fail();}
            catch(java.io.IOException expected){assertEquals("binding denied",expected.getMessage());}
            try{EventViewSync.parallelReads(executor,() -> json("{'public':true}"),() -> {throw new java.io.IOException("source failed");});fail();}
            catch(java.io.IOException expected){assertEquals("source failed",expected.getMessage());}
        }
        finally{executor.shutdownNow();}
    }
    @Test public void interruptedReadPreservesInterruptAndDoesNotPublish() throws Exception
    {
        ExecutorService executor=Executors.newFixedThreadPool(2);CountDownLatch block=new CountDownLatch(1);
        try
        {
            Thread.currentThread().interrupt();
            try{EventViewSync.parallelReads(executor,() -> {try{block.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.io.IOException(e);}return json("{}");},() -> json("{}"));fail();}
            catch(java.io.IOException expected){assertTrue(Thread.currentThread().isInterrupted());}
        }
        finally{Thread.interrupted();block.countDown();executor.shutdownNow();}
    }
    private static JsonObject json(String s){return JsonParser.parseString(s.replace('\'', '"')).getAsJsonObject();}
    private static JsonObject destination()
    {return json("{'sheets':[{'properties':{'sheetId':1,'title':'Tussenstand','gridProperties':{'rowCount':100}}},{'properties':{'sheetId':2,'title':'Team 1','gridProperties':{'rowCount':100}}},{'properties':{'sheetId':3,'title':'Team 2','gridProperties':{'rowCount':100}}}], 'developerMetadata':[{'metadataKey':'DN_EVENT_VIEW_SOURCE','metadataValue':'"+SOURCE+"'}]}");}
    private static Map<String,Object> update(int sheet,String... values)
    {
        List<Object> cells=new ArrayList<>();for(String v:values)cells.add(BattleshipSheets.map("userEnteredValue",BattleshipSheets.map("stringValue",v)));
        return BattleshipSheets.map("updateCells",BattleshipSheets.map("start",BattleshipSheets.map("sheetId",sheet,"rowIndex",5,"columnIndex",0),
            "rows",List.of(BattleshipSheets.map("values",cells)),"fields","userEnteredValue,userEnteredFormat,dataValidation"));
    }
    private static boolean hidden(JsonArray requests,int oneBasedRow)
    {
        boolean hidden=false;
        for(JsonElement el:requests)
        {
            JsonObject request=el.getAsJsonObject();if(!request.has("updateDimensionProperties"))continue;
            JsonObject update=request.getAsJsonObject("updateDimensionProperties"),range=update.getAsJsonObject("range"),properties=update.getAsJsonObject("properties");
            if(!properties.has("hiddenByUser")||!range.get("dimension").getAsString().equals("ROWS"))continue;
            if(oneBasedRow-1>=range.get("startIndex").getAsInt()&&oneBasedRow-1<range.get("endIndex").getAsInt())hidden=properties.get("hiddenByUser").getAsBoolean();
        }
        return hidden;
    }
    @Test public void allGameLayoutsHideSummaryAndInstructionsButKeepPlayableCards()
    {
        for(String mode:List.of("Bingo","Punten","Vier-op-een-rij","Zeeslag"))
        {
            List<Object> plan=new ArrayList<>();EventViewSync.boardLayout(plan,1,mode,1007);
            JsonArray requests=new Gson().toJsonTree(plan).getAsJsonArray();
            for(int row:List.of(1,3,4,5,6,8,9,10,11,12,15,30,31,32,34,50,51,71,75,1007))assertTrue(mode+" row "+row,hidden(requests,row));
            assertFalse(mode+" title",hidden(requests,2));
            if(mode.equals("Zeeslag"))
            {
                assertTrue(hidden(requests,13));assertTrue(hidden(requests,17));
                for(int row:List.of(52,53,54,61,63,64,65,66,68))assertFalse("Zeeslag controls "+row,hidden(requests,row));
            }
            else
            {
                assertFalse(hidden(requests,13));assertFalse(hidden(requests,17));assertTrue(hidden(requests,52));
                if(mode.equals("Bingo")){assertFalse(hidden(requests,14));assertFalse(hidden(requests,25));assertTrue(hidden(requests,26));}
                if(mode.equals("Vier-op-een-rij")){assertFalse(hidden(requests,14));assertFalse(hidden(requests,29));}
                if(mode.equals("Punten")){for(int row:List.of(14,16,18,19,21,22,24))assertFalse(hidden(requests,row));assertTrue(hidden(requests,25));}
            }
            assertFalse(new Gson().toJson(plan).contains("deleteDimension"));
        }
    }
    @Test public void pointsUseTwoReadableCardsInsteadOfTheHiddenCalculationSummary()
    {
        List<Object> plan=new ArrayList<>();EventViewSync.boardLayout(plan,1,"Punten",100);
        JsonArray requests=new Gson().toJsonTree(plan).getAsJsonArray();int cards=0;
        for(JsonElement el:requests)
        {
            JsonObject request=el.getAsJsonObject();if(!request.has("mergeCells"))continue;
            JsonObject range=request.getAsJsonObject("mergeCells").getAsJsonObject("range");
            if(range.get("startRowIndex").getAsInt()!=16)continue;
            assertEquals(16,range.get("startRowIndex").getAsInt());assertEquals(17,range.get("endRowIndex").getAsInt());
            assertEquals(7,range.get("endColumnIndex").getAsInt()-range.get("startColumnIndex").getAsInt());cards++;
        }
        assertEquals(2,cards);assertTrue(new Gson().toJson(plan).contains("82"));
        assertFalse(hidden(requests,13));assertFalse(hidden(requests,17));
    }
    @Test public void everyGameHasAnAutomaticVisibleTitle()
    {
        assertEquals("BINGO",EventViewSync.gameTitle("Bingo"));assertEquals("ZEESLAG",EventViewSync.gameTitle("Zeeslag"));
        assertEquals("VIER OP EEN RIJ",EventViewSync.gameTitle("Vier-op-een-rij"));assertEquals("PUNTENCOMPETITIE",EventViewSync.gameTitle("Punten"));
    }
    private static int[] boardWidths(List<Object> plan)
    {
        int[] widths=new int[17];Arrays.fill(widths,60);widths[8]=24;
        for(JsonElement element:new Gson().toJsonTree(plan).getAsJsonArray())
        {
            JsonObject request=element.getAsJsonObject();if(!request.has("updateDimensionProperties"))continue;
            JsonObject update=request.getAsJsonObject("updateDimensionProperties"),range=update.getAsJsonObject("range"),properties=update.getAsJsonObject("properties");
            if(!range.get("dimension").getAsString().equals("COLUMNS")||!properties.has("pixelSize"))continue;
            for(int c=range.get("startIndex").getAsInt();c<Math.min(17,range.get("endIndex").getAsInt());c++)widths[c]=properties.get("pixelSize").getAsInt();
        }
        return widths;
    }
    @Test public void gameWidthsOverrideLateSourceFormattingAndRestoreBothTeams()
    {
        for(String mode:List.of("Bingo","Punten","Vier-op-een-rij","Zeeslag"))
        {
            List<Object> plan=new ArrayList<>();EventViewSync.boardLayout(plan,1,mode,1007);
            int[] widths=boardWidths(plan);
            for(int col=0;col<17;col++)
            {
                int expected=mode.equals("Zeeslag")?(col==8?24:60):(col==7?24:col>=15?68:82);
                assertEquals(mode+" column "+col,expected,widths[col]);
            }
            if(!mode.equals("Zeeslag")){assertEquals(82,widths[0]);assertEquals(82,widths[8]);}
        }
    }
    @Test public void seaToBingoUpdatesWidthsEvenWhenCellValuesAreUnchanged()
    {
        List<Object> sea=new ArrayList<>(),bingo=new ArrayList<>();
        EventViewSync.boardLayout(sea,1,"Zeeslag",1007);EventViewSync.boardLayout(bingo,1,"Bingo",1007);
        int[] widths=boardWidths(EventViewSync.incremental(sea,bingo));
        assertEquals(82,widths[0]);assertEquals(24,widths[7]);assertEquals(82,widths[8]);
    }
    @Test public void sourceCardsOnlyChangeVisibilityOfTheInstalledBoardArea()
    {
        for(String mode:List.of("Bingo","Punten","Vier-op-een-rij","Zeeslag"))
        {
            JsonArray requests=new Gson().toJsonTree(EventViewSync.sourceCardVisibility(1058026847,mode)).getAsJsonArray();
            for(JsonElement element:requests)
            {
                JsonObject request=element.getAsJsonObject();assertEquals(1,request.size());assertTrue(request.has("updateDimensionProperties"));
                JsonObject update=request.getAsJsonObject("updateDimensionProperties"),range=update.getAsJsonObject("range");
                assertEquals("hiddenByUser",update.get("fields").getAsString());assertEquals("ROWS",range.get("dimension").getAsString());
                assertTrue(range.get("startIndex").getAsInt()>=12);assertTrue(range.get("endIndex").getAsInt()<=94);
            }
            assertEquals(mode.equals("Punten"),!hidden(requests,83));
            assertEquals(mode.equals("Zeeslag"),!hidden(requests,52));
            assertEquals(mode.equals("Bingo")||mode.equals("Vier-op-een-rij"),!hidden(requests,17));
        }
    }
    private static List<Object> emptyBoard()
    {
        List<Object> output=new ArrayList<>();
        for(int row=0;row<75;row++){List<Object> cells=new ArrayList<>();for(int col=0;col<17;col++)cells.add(new LinkedHashMap<>());output.add(BattleshipSheets.map("values",cells));}
        return output;
    }
    private static JsonObject cardCell(List<Object> output,int row,int col)
    {return new Gson().toJsonTree(output).getAsJsonArray().get(row).getAsJsonObject().getAsJsonArray("values").get(col).getAsJsonObject();}
    @Test public void pointsCardsUseCalculatedTotalsShareRankAndPublicDropCounts() throws Exception
    {
        List<List<Object>> board=new ArrayList<>();for(int row=0;row<6;row++)board.add(new ArrayList<>(Collections.nCopies(7,"")));
        board.get(4).set(1,"Team Rood");board.get(5).set(1,"Team Blauw");board.get(4).set(6,120.5d);board.get(5).set(6,79.5d);
        board.get(4).set(3,4);board.get(4).set(4,52);board.get(5).set(3,3);board.get(5).set(4,52);
        List<Object> one=new ArrayList<>(Collections.nCopies(11,"")),two=new ArrayList<>(Collections.nCopies(11,""));one.set(10,9);two.set(10,7);one.set(3,"PRIVATE_PLAYER_DO_NOT_COPY");
        List<Object> output=emptyBoard();EventViewSync.pointCards(output,board,List.of(one),List.of(two),List.of(List.of(6),List.of(6)));
        assertEquals(120.5d,cardCell(output,16,0).getAsJsonObject("userEnteredValue").get("numberValue").getAsDouble(),0);
        assertEquals(79.5d,cardCell(output,16,8).getAsJsonObject("userEnteredValue").get("numberValue").getAsDouble(),0);
        assertEquals("1E PLAATS",cardCell(output,13,0).getAsJsonObject("userEnteredValue").get("stringValue").getAsString());
        assertEquals("2E PLAATS",cardCell(output,13,8).getAsJsonObject("userEnteredValue").get("stringValue").getAsString());
        assertEquals("4 / 52",cardCell(output,21,0).getAsJsonObject("userEnteredValue").get("stringValue").getAsString());
        assertEquals(9,cardCell(output,21,3).getAsJsonObject("userEnteredValue").get("numberValue").getAsInt());
        assertEquals("41 punten voorsprong",cardCell(output,23,0).getAsJsonObject("userEnteredValue").get("stringValue").getAsString());
        String bar=cardCell(output,17,0).getAsJsonObject("userEnteredValue").get("stringValue").getAsString();assertEquals(30,bar.length());assertEquals(18,bar.chars().filter(c->c=='█').count());
        assertFalse(new Gson().toJson(output).contains("PRIVATE_PLAYER_DO_NOT_COPY"));assertFalse(new Gson().toJson(output).contains("formulaValue"));
    }
    @Test public void zeroPointsShowATieWithoutFalseProgressOrDivisionErrors() throws Exception
    {
        List<Object> output=emptyBoard();EventViewSync.pointCards(output,List.of(),List.of(),List.of(),List.of(List.of(),List.of()));
        String json=new Gson().toJson(output);assertFalse(json.contains("NaN"));assertFalse(json.contains("Infinity"));assertTrue(json.contains("Nog geen punten behaald"));
        assertEquals("░".repeat(30),cardCell(output,17,0).getAsJsonObject("userEnteredValue").get("stringValue").getAsString());
        assertEquals("GELIJKE STAND",cardCell(output,13,8).getAsJsonObject("userEnteredValue").get("stringValue").getAsString());
    }
    @Test public void unchangedUpdatesDoNotWriteOrReloadImages()
    {
        List<Object> plan=List.of(update(1,"IMAGE","same"),update(2,"player"));
        assertTrue(EventViewSync.incremental(plan,plan).isEmpty());
    }
    @Test public void onlyChangedCellsAreWrittenAndClearedWithTheirFormats()
    {
        List<Object> before=List.of(update(1,"IMAGE","old","old2","stable","old3"),update(2,"stable"));
        List<Object> after=List.of(update(1,"IMAGE","new","","stable","new3"),update(2,"stable"));
        JsonArray changes=new Gson().toJsonTree(EventViewSync.incremental(before,after)).getAsJsonArray();
        assertEquals(2,changes.size());
        JsonObject first=changes.get(0).getAsJsonObject().getAsJsonObject("updateCells");
        assertEquals(1,first.getAsJsonObject("start").get("columnIndex").getAsInt());
        assertEquals(5,first.getAsJsonObject("start").get("rowIndex").getAsInt());
        assertEquals(2,first.getAsJsonArray("rows").get(0).getAsJsonObject().getAsJsonArray("values").size());
        assertTrue(first.get("fields").getAsString().contains("userEnteredFormat"));
        assertFalse(changes.toString().contains("IMAGE"));
        assertFalse(changes.toString().contains("repeatCell"));
    }
    @Test public void layoutChangesOrFirstSyncUseFullAtomicPlan()
    {
        List<Object> before=List.of(update(1,"same"));
        List<Object> after=List.of(update(1,"same"),BattleshipSheets.map("mergeCells",BattleshipSheets.map("range",BattleshipSheets.map("sheetId",1))));
        assertSame(after,EventViewSync.incremental(before,after));
        assertSame(before,EventViewSync.incremental(null,before));
        assertSame(after,EventViewSync.incremental(List.of(update(2,"same")),after));
    }
    @Test public void changedTeamAndBoardCellsAreInTheSameBatch()
    {
        List<Object> changes=EventViewSync.incremental(List.of(update(1,"open"),update(2,"0"),update(3,"0")),
            List.of(update(1,"claimed"),update(2,"1"),update(3,"0")));
        assertEquals(2,changes.size());
        assertTrue(new Gson().toJson(changes).contains("claimed"));
    }
    @Test public void addingSelectedRowsDoesNotEraseOrReloadTheExistingBoard()
    {
        List<Object> before=List.of(update(1,"IMAGE","same"),update(2,"player"));
        Map<String,Object> added=update(2,"NEW_DROP");
        @SuppressWarnings("unchecked") Map<String,Object> u=(Map<String,Object>)added.get("updateCells");
        u.put("start",BattleshipSheets.map("sheetId",2,"rowIndex",700,"columnIndex",0));
        List<Object> next=List.of(update(1,"IMAGE","same"),update(2,"player"),added,
            BattleshipSheets.map("updateDimensionProperties",BattleshipSheets.map("range",BattleshipSheets.map("sheetId",2,"startIndex",700,"endIndex",701),"properties",BattleshipSheets.map("hiddenByUser",false),"fields","hiddenByUser")));
        List<Object> delta=EventViewSync.incremental(before,next);
        String result=new Gson().toJson(delta);
        assertNotSame(next,delta);assertFalse(result.contains("IMAGE"));assertFalse(result.contains("player"));
        assertFalse(result.contains("repeatCell"));assertFalse(result.contains("unmergeCells"));
        assertTrue(result.contains("NEW_DROP"));assertTrue(result.contains("hiddenByUser"));
    }
    @Test public void removingSelectedRowsExplicitlyClearsTheirOldValues()
    {
        List<Object> next=List.of(update(1,"unchanged"));
        JsonArray changes=new Gson().toJsonTree(EventViewSync.incremental(List.of(update(1,"unchanged"),update(2,"old player")),next)).getAsJsonArray();
        assertEquals(1,changes.size());
        JsonObject update=changes.get(0).getAsJsonObject().getAsJsonObject("updateCells");
        assertEquals(2,update.getAsJsonObject("start").get("sheetId").getAsInt());
        assertEquals(0,update.getAsJsonArray("rows").get(0).getAsJsonObject().getAsJsonArray("values").get(0).getAsJsonObject().size());
    }
    @Test public void changingGoalUpdatesProtectionWithoutReloadingImages()
    {
        Object oldProtection=BattleshipSheets.map("addProtectedRange",BattleshipSheets.map("protectedRange",BattleshipSheets.map("unprotectedRanges",List.of("D:E"))));
        Object newProtection=BattleshipSheets.map("addProtectedRange",BattleshipSheets.map("protectedRange",BattleshipSheets.map("unprotectedRanges",List.of("D:F"))));
        Object deletion=BattleshipSheets.map("deleteProtectedRange",BattleshipSheets.map("protectedRangeId",123));
        String delta=new Gson().toJson(EventViewSync.incremental(List.of(update(1,"IMAGE"),oldProtection),List.of(update(1,"IMAGE"),deletion,newProtection)));
        assertTrue(delta.contains("deleteProtectedRange"));assertTrue(delta.contains("D:F"));assertFalse(delta.contains("IMAGE"));
    }
    @Test public void regroupingAdjacentSourceRangesDoesNotRewriteEqualCells()
    {
        Map<String,Object> joined=update(2,"one");
        @SuppressWarnings("unchecked") Map<String,Object> u=(Map<String,Object>)joined.get("updateCells");
        u.put("rows",List.of(BattleshipSheets.map("values",List.of(BattleshipSheets.map("userEnteredValue",BattleshipSheets.map("stringValue","one")))),
            BattleshipSheets.map("values",List.of(BattleshipSheets.map("userEnteredValue",BattleshipSheets.map("stringValue","two"))))));
        Map<String,Object> second=update(2,"two");
        @SuppressWarnings("unchecked") Map<String,Object> s=(Map<String,Object>)second.get("updateCells");
        s.put("start",BattleshipSheets.map("sheetId",2,"rowIndex",6,"columnIndex",0));
        assertTrue(EventViewSync.incremental(List.of(joined),List.of(update(2,"one"),second)).isEmpty());
    }
    @Test public void neighbouringDropRowsUseBoundedCombinedRanges()
    {
        assertEquals(List.of("'Team 1'!A6:R12","'Team 1'!A90:R91"),EventViewSync.rowRanges("Team 1",List.of(6,7,11,12,90,91)));
        assertTrue(EventViewSync.rowRanges("Team 2",List.of()).isEmpty());
    }
    @Test public void inputRosterRejectsDuplicatesCrossTeamAndInvalidNames()
    {
        assertEquals(List.of("heavenskill"),EventViewSync.teamPlayers(List.of(List.of("heavenskill"),List.of("Duplicate"),List.of("DUPLICATE"),List.of("Other team"),List.of("=FORMULA")),List.of(List.of("Other team"))));
    }
    @Test public void inputGenerationChangesOnResetAndPeriodChanges()
    {
        assertEquals("",EventViewSync.inputEpoch(SOURCE,List.of()));
        assertEquals("",EventViewSync.inputEpoch(SOURCE,List.of(List.of(46000d,"","","",45000d))));
        assertNotEquals(EventViewSync.inputEpoch(SOURCE,List.of(List.of(46000d,"","","",47000d))),EventViewSync.inputEpoch(SOURCE,List.of(List.of(46001d,"","","",47000d))));
    }
    @Test public void sparseIncrementalUpdatesKeepStableSourceRowAddresses()
    {
        Map<String,Object> a=update(2,"old"),b=update(2,"new");
        @SuppressWarnings("unchecked") Map<String,Object> oldUpdate=(Map<String,Object>)a.get("updateCells");
        @SuppressWarnings("unchecked") Map<String,Object> newUpdate=(Map<String,Object>)b.get("updateCells");
        oldUpdate.put("start",BattleshipSheets.map("sheetId",2,"rowIndex",600,"columnIndex",0));newUpdate.put("start",oldUpdate.get("start"));
        List<Object> delta=EventViewSync.incremental(List.of(update(2,"header"),a),List.of(update(2,"header"),b));
        assertEquals(1,delta.size());assertTrue(new Gson().toJson(delta).contains("600"));
    }
    @Test public void editableModeUsesStableRowsLimitedSlotsAndProtectedOutputs() throws Exception
    {
        JsonObject snapshot=destination().deepCopy();snapshot.remove("developerMetadata");
        for(JsonElement e:snapshot.getAsJsonArray("sheets"))
        {
            JsonObject s=e.getAsJsonObject();JsonArray rows=new JsonArray();
            for(int r=0;r<8;r++)rows.add(json("{'values':[{'effectiveValue':{'stringValue':'DROP-1'}},{},{'effectiveValue':{'numberValue':2}}]}"));
            JsonObject grid=new JsonObject();grid.add("rowData",rows);JsonArray grids=new JsonArray();grids.add(grid);s.add("data",grids);
        }
        List<List<Object>> board=new ArrayList<>();for(int r=0;r<8;r++)board.add(List.of("",r==7?"Bingo":""));
        List<List<List<Object>>> values=List.of(List.of(List.of(TARGET,"REGISTRATIES_V1")),board,List.of(),List.of(),List.of(),List.of(),List.of(),
            List.of(List.of(46000d,"","","",47000d)),List.of(List.of("Alice")),List.of(List.of("Bob")));
        JsonArray requests=new Gson().toJsonTree(EventViewSync.plan(SOURCE,TARGET,destination(),snapshot,values,List.of(List.of(8),List.of(8)),"now")).getAsJsonArray();
        int protectedInputs=0;boolean stableRow=false,epoch=false;
        for(JsonElement el:requests)
        {
            JsonObject r=el.getAsJsonObject();
            if(r.has("addProtectedRange")){
                JsonObject p=r.getAsJsonObject("addProtectedRange").getAsJsonObject("protectedRange");assertFalse(p.get("warningOnly").getAsBoolean());
                if(p.has("unprotectedRanges")){JsonObject free=p.getAsJsonArray("unprotectedRanges").get(0).getAsJsonObject();assertEquals(7,free.get("startRowIndex").getAsInt());assertEquals(3,free.get("startColumnIndex").getAsInt());assertEquals(5,free.get("endColumnIndex").getAsInt());protectedInputs++;}
            }
            if(r.has("updateCells")){
                JsonObject u=r.getAsJsonObject("updateCells"),start=u.getAsJsonObject("start");
                if(start.get("sheetId").getAsInt()!=1&&start.get("rowIndex").getAsInt()==7){stableRow=true;assertTrue(u.toString().contains("ONE_OF_LIST"));}
                if(start.get("sheetId").getAsInt()!=1&&start.get("rowIndex").getAsInt()==0)epoch|=u.toString().contains(SOURCE+"|");
            }
        }
        assertEquals(2,protectedInputs);assertTrue(stableRow);assertTrue(epoch);
    }
    @Test public void rejectsSourceOrUnboundDestination() throws Exception
    {
        try{EventViewSync.checkDestination(SOURCE,SOURCE,destination());fail();}catch(java.io.IOException expected){}
        JsonObject m=destination();m.remove("developerMetadata");
        try{EventViewSync.checkDestination(SOURCE,TARGET,m);fail();}catch(java.io.IOException expected){}
    }
    @Test public void refusesAnyExtraHiddenSettingsOrFleetTab() throws Exception
    {
        JsonObject m=destination();m.getAsJsonArray("sheets").add(json("{'properties':{'title':'Instellingen'}}"));
        try{EventViewSync.checkDestination(SOURCE,TARGET,m);fail();}catch(java.io.IOException expected){}
    }
    @Test public void onlySelectedRowsIncludingNewRowsArePublished() throws Exception
    {
        List<List<Object>> rows=new ArrayList<>();for(int i=0;i<2000;i++)rows.add(new ArrayList<>());
        rows.set(1999,new ArrayList<>(Collections.nCopies(18,"")));rows.get(1999).set(17,true);
        assertEquals(List.of(2005),EventViewSync.selected(rows));
    }
    @Test public void fullPvmSelectionAbove500IsSupported() throws Exception
    {
        List<List<Object>> rows=new ArrayList<>();
        for(int i=0;i<695;i++){List<Object> row=new ArrayList<>(Collections.nCopies(18,""));row.set(17,true);rows.add(row);}
        assertEquals(695,EventViewSync.selected(rows).size());
    }
    @Test public void enforcesBoundedMaximumActiveGoals() throws Exception
    {
        List<List<Object>> rows=new ArrayList<>();for(int i=0;i<EventViewSync.MAX_ACTIVE_DROPS;i++){List<Object> row=new ArrayList<>(Collections.nCopies(18,""));row.set(17,true);rows.add(row);}
        assertEquals(EventViewSync.MAX_ACTIVE_DROPS,EventViewSync.selected(rows).size());
        rows.add(new ArrayList<>(rows.get(0)));
        try{EventViewSync.selected(rows);fail();}catch(java.io.IOException expected){}
    }
    @Test public void computedValuesAndFormatsButNotSourceFormulasAreCopied() throws Exception
    {
        JsonObject c=json("{'userEnteredValue':{'formulaValue':'=Instellingen!A17'},'effectiveValue':{'stringValue':'Team Rood'},'effectiveFormat':{'textFormat':{'bold':true}}}");
        String out=new Gson().toJson(EventViewSync.project(c,false,5,1,"Bingo",List.of(),Map.of()));
        assertTrue(out.contains("Team Rood"));assertTrue(out.contains("userEnteredFormat"));assertFalse(out.contains("Instellingen"));assertFalse(out.contains("formulaValue"));
    }
    @Test public void imagesBecomeStandaloneTrustedImageFormulas() throws Exception
    {
        JsonObject c=new JsonObject(),v=new JsonObject();c.add("userEnteredValue",v);
        v.addProperty("formulaValue","=IF(NOT(OR(Borddata!$Z$2=\"Bingo\";Borddata!$Z$2=\"Vier-op-een-rij\"));\"\";IMAGE(INDEX(Borddata!$C$2:$C$50;IF(Borddata!$Z$2=\"Bingo\";1;1))))");
        String out=new Gson().toJson(EventViewSync.project(c,true,15,0,"Bingo",List.of(List.of("Dragon spear")),Map.of("Dragon spear","https://static.runelite.net/cache/item/icon/1249.png")));
        assertTrue(out.contains("IMAGE"));assertFalse(out.contains("Borddata"));
        assertFalse(new Gson().toJson(EventViewSync.project(c,true,15,0,"Bingo",List.of(List.of("Dragon spear")),Map.of("Dragon spear","https://evil.test/secret"))).contains("evil"));
        v.addProperty("formulaValue",v.get("formulaValue").getAsString().replace("=\"Bingo\";Borddata!$Z$2=\"Vier-op-een-rij\"","=\"Vier-op-een-rij\""));
        assertFalse(new Gson().toJson(EventViewSync.project(c,true,15,5,"Bingo",List.of(List.of("Dragon spear")),Map.of("Dragon spear","https://static.runelite.net/cache/item/icon/1249.png"))).contains("IMAGE"));
    }
    @Test public void privateLinksAndPendingShotsAreNeverPublished() throws Exception
    {
        JsonObject c=json("{'effectiveValue':{'stringValue':'PRIVATE'}}"), formula=new JsonObject();
        formula.addProperty("formulaValue","=HYPERLINK(\"#gid=1726059420\";\"Settings\")");c.add("userEnteredValue",formula);
        assertFalse(new Gson().toJson(EventViewSync.project(c,false,2,8,"Zeeslag",List.of(),Map.of())).contains("PRIVATE"));
        JsonObject pending=json("{'effectiveValue':{'stringValue':'A1'}}");
        assertFalse(new Gson().toJson(EventViewSync.project(pending,true,64,3,"Zeeslag",List.of(),Map.of())).contains("A1"));
    }
    @Test public void sourceErrorsDoNotOverwriteLastGoodView() throws Exception
    {
        try{EventViewSync.project(json("{'effectiveValue':{'errorValue':{'type':'REF'}}}"),true,1,1,"Bingo",List.of(),Map.of());fail();}
        catch(java.io.IOException expected){}
    }
    @Test public void inactiveConditionalHyperlinkDoesNotReplaceTheActiveGameResult() throws Exception
    {
        JsonObject c=json("{'effectiveValue':{'stringValue':'0 bingo-lijnen'}}"),f=new JsonObject();
        f.addProperty("formulaValue","=IF(FALSE;HYPERLINK(\"#gid=1058026847\";\"Borden en schoten hieronder\");\"0 bingo-lijnen\")");c.add("userEnteredValue",f);
        String out=new Gson().toJson(EventViewSync.project(c,true,13,0,"Bingo",List.of(),Map.of()));
        assertTrue(out.contains("0 bingo-lijnen"));assertFalse(out.contains("Borden en schoten hieronder"));
    }
    @Test public void publicTeamPagesDoNotTellReadersToEnterPlayers() throws Exception
    {
        String out=new Gson().toJson(EventViewSync.project(json("{'effectiveValue':{'stringValue':'Kies per drop een speler'}}"),false,3,0,"Bingo",List.of(),Map.of()));
        assertTrue(out.contains("Spelleiders registreren"));assertFalse(out.contains("Kies per drop"));
    }
    @Test public void allFourGamesBuildExactlyThreeOneWayPublicPages() throws Exception
    {
        for(String mode:List.of("Bingo","Punten","Vier-op-een-rij","Zeeslag"))
        {
            JsonObject snapshot=destination().deepCopy();snapshot.remove("developerMetadata");
            for(JsonElement e:snapshot.getAsJsonArray("sheets"))
            {
                JsonObject s=e.getAsJsonObject();JsonArray data=new JsonArray();s.add("data",data);
                JsonObject grid=new JsonObject();data.add(grid);JsonArray rows=new JsonArray();grid.add("rowData",rows);
                for(int r=0;r<6;r++)rows.add(json("{'values':[{'effectiveValue':{'stringValue':'PUBLIC'}}]}"));
            }
            List<List<Object>> board=new ArrayList<>();for(int r=0;r<8;r++)board.add(List.of("",r==7?mode:""));
            List<List<List<Object>>> values=List.of(List.of(),board,List.of(),List.of(),List.of(),List.of(),List.of());
            List<Object> requests=EventViewSync.plan(SOURCE,TARGET,destination(),snapshot,values,List.of(List.of(6),List.of(6)),"28-10-2026 20:00:00");
            String out=new Gson().toJson(requests);
            assertTrue(out.contains("PUBLIC"));assertTrue(out.contains("alleen lezen"));
            assertFalse(out.contains("Instellingen"));assertFalse(out.contains("Vloot"));assertFalse(out.contains("insertSheet"));
            long updates=requests.stream().map(x->(Map<?,?>)x).filter(x->x.containsKey("updateCells")).count();assertEquals(3,updates);
            if(mode.equals("Zeeslag"))assertTrue(out.contains("Teamleiders schieten in het basisbestand"));
        }
    }
}

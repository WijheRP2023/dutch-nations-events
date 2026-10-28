package nl.dutchnations.server;

import com.google.gson.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class EventViewSyncTest
{
    private static final String SOURCE="source________________________",TARGET="viewer________________________";
    private static JsonObject json(String s){return JsonParser.parseString(s.replace('\'', '"')).getAsJsonObject();}
    private static JsonObject destination()
    {return json("{'sheets':[{'properties':{'sheetId':1,'title':'Tussenstand','gridProperties':{'rowCount':100}}},{'properties':{'sheetId':2,'title':'Team 1','gridProperties':{'rowCount':100}}},{'properties':{'sheetId':3,'title':'Team 2','gridProperties':{'rowCount':100}}}], 'developerMetadata':[{'metadataKey':'DN_EVENT_VIEW_SOURCE','metadataValue':'"+SOURCE+"'}]}");}
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
    @Test public void enforcesMaximum500Goals() throws Exception
    {
        List<List<Object>> rows=new ArrayList<>();for(int i=0;i<501;i++){List<Object> row=new ArrayList<>(Collections.nCopies(18,""));row.set(17,true);rows.add(row);}
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

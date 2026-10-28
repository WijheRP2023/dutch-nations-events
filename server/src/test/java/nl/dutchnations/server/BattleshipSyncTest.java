package nl.dutchnations.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class BattleshipSyncTest
{
    @Test public void protectsOnlyOwnShotFieldsOnTussenstandWithoutExampleTab() throws Exception
    {
        com.google.gson.JsonObject meta=com.google.gson.JsonParser.parseString("{\"properties\":{\"timeZone\":\"Europe/Amsterdam\"},\"sheets\":[{\"properties\":{\"sheetId\":1,\"title\":\"Tussenstand\"}},{\"properties\":{\"sheetId\":2,\"title\":\"Zeeslagdata\"}}]}").getAsJsonObject();
        com.google.gson.JsonArray plan=new com.google.gson.Gson().toJsonTree(BattleshipSheets.publicProtectionPlan(meta,Set.of("red@test"),Set.of("blue@test"),"server@test")).getAsJsonArray();
        assertEquals(6,plan.size());
        for(int i=2;i<6;i++)
        {
            com.google.gson.JsonObject rule=plan.get(i).getAsJsonObject().getAsJsonObject("addProtectedRange").getAsJsonObject("protectedRange");
            com.google.gson.JsonObject range=rule.getAsJsonObject("range");
            assertEquals(1,range.get("sheetId").getAsInt());assertEquals(64,range.get("startRowIndex").getAsInt());assertEquals(65,range.get("endRowIndex").getAsInt());
            String editors=rule.getAsJsonObject("editors").toString();
            assertTrue(editors.contains(i<4?"red@test":"blue@test"));assertFalse(editors.contains(i<4?"blue@test":"red@test"));
            assertEquals(new int[]{3,7,12,16}[i-2],range.get("startColumnIndex").getAsInt());
        }
        com.google.gson.JsonArray free=plan.get(0).getAsJsonObject().getAsJsonObject("addProtectedRange").getAsJsonObject("protectedRange").getAsJsonArray("unprotectedRanges");assertEquals(4,free.size());
    }
    @Test public void eightByEightBoardClearsLegacyTransportEdges()
    {
        BattleshipGame game=BattleshipGameTest.game();
        game.fire(0,"H8",BattleshipGameTest.all(),500);
        List<List<?>> sheet=BattleshipSync.sheetBoard(game,0);
        assertEquals(8,game.publicBoard(0).size());
        assertEquals(8,game.publicBoard(0).get(0).size());
        assertEquals("MIS",sheet.get(7).get(7));
        for(int y=0;y<10;y++) for(int x=0;x<10;x++) if(y>=8||x>=8) assertEquals("",sheet.get(y).get(x));
    }
    static List<List<Object>> rows()
    {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("Drop (bewerkbaar)","Team","Doel","Behaald","Nog nodig","Status","Drop-ID","Board-vak"));
        rows.add(List.of("Whip","Rood",3d,3d,0,"Klaar","DROP-1",1));
        rows.add(List.of("Whip","Blauw",3d,1d,2,"Nog nodig","DROP-1",1));
        return rows;
    }
    static List<List<Object>> teamRows(int obtained)
    {
        List<Object> header=new ArrayList<>(java.util.Collections.nCopies(18,""));
        header.set(0,"Drop-ID");header.set(1,"Drop");header.set(2,"Doel");header.set(10,"Behaald");header.set(17,"Actief");
        List<Object> row=new ArrayList<>(java.util.Collections.nCopies(18,""));
        row.set(0,"DROP-1");row.set(1,"Whip");row.set(2,3d);row.set(10,(double)obtained);row.set(17,true);
        return new ArrayList<>(List.of(header,row));
    }
    @Test public void readsEachTeamPageAndNotTheScreenshotHelper()
    {
        BattleshipSync.Progress p=BattleshipSync.teamProgress(teamRows(3),teamRows(1),"Rood","Blauw");
        assertEquals(Set.of("DROP-1"),p.completed.get(0));assertTrue(p.completed.get(1).isEmpty());assertEquals(Integer.valueOf(3),p.goals.get("DROP-1"));
        List<List<Object>> inactive=teamRows(3);List<Object> hidden=new ArrayList<>(inactive.get(1));hidden.set(17,false);hidden.set(0,"ignored");inactive.add(hidden);
        assertEquals(1,BattleshipSync.teamProgress(inactive,teamRows(1),"Rood","Blauw").goals.size());
        inactive.add(inactive.get(1));BattleshipGameTest.rejects(()->BattleshipSync.teamProgress(inactive,teamRows(1),"Rood","Blauw"));
        BattleshipGameTest.rejects(()->BattleshipSync.teamProgress(teamRows(4),teamRows(1),"Rood","Blauw"));
    }
    @Test public void quantityThreeStillEarnsJustOneShot()
    { BattleshipSync.Progress p = BattleshipSync.progress(rows(),"Rood","Blauw"); assertEquals(Set.of("DROP-1"),p.completed.get(0)); assertTrue(p.completed.get(1).isEmpty()); }
    @Test public void malformedOrDifferentGoalListsFailClosed()
    {
        List<List<Object>> a = rows(); a.add(a.get(1)); BattleshipGameTest.rejects(() -> BattleshipSync.progress(a,"Rood","Blauw"));
        List<List<Object>> b = rows(); b.remove(2); BattleshipGameTest.rejects(() -> BattleshipSync.progress(b,"Rood","Blauw"));
        BattleshipGameTest.rejects(() -> BattleshipSync.progress(rows(),"Rood","rood"));
    }
    @Test public void googleDatesUseAmsterdamNotHostTimeZone()
    { assertEquals(java.time.Instant.parse("2026-01-01T11:00:00Z").toEpochMilli(), BattleshipSync.date(46023.5)); BattleshipGameTest.rejects(() -> BattleshipSync.date(Double.NaN)); }
    @Test public void otherTeamsCannotShareTheSamePrivateReaders() throws IOException
    {
        BattleshipSheets.Access a = new BattleshipSheets.Access(), b = new BattleshipSheets.Access();
        a.owners.add("owner@test"); b.owners.add("owner@test"); a.readers.add("owner@test"); b.readers.add("owner@test");
        BattleshipSheets.separated(a,b);
        a.readers.add("captain@test"); b.readers.add("captain@test");
        try { BattleshipSheets.separated(a,b); fail(); } catch (IOException expected) { }
    }
    @Test public void googleFileIdsCannotBeUrlsOrContainRequestSyntax()
    { assertEquals("Abc_12345678901234567890",BattleshipSheets.id("Abc_12345678901234567890")); for(String s:List.of("https://evil.test/x","../x","abc?access_token=foo")) BattleshipGameTest.rejects(()->BattleshipSheets.id(s)); }
}

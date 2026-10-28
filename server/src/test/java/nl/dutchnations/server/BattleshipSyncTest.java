package nl.dutchnations.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class BattleshipSyncTest
{
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

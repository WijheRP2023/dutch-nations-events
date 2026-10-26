package nl.dutchnations.server;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class SheetTeamRosterTest
{
    private static final String ID = "12345678901234567890";
    private static final String BASE = "https://docs.google.com/spreadsheets/d/" + ID + "/edit";
    private static String csv(String rows)
    { return "\"\",\"\"\nTeam 1,Team 2\nSpeler (in-game),Speler (in-game)\n" + rows; }

    @Test public void teamSlotUsesNameFromEventNotSheetTitle() throws Exception
    {
        SheetTeamRoster roster = SheetTeamRoster.parseCsv(csv("heavenskill,Second player\n"));
        assertEquals("Rode draken", roster.teamFor("heavenskill", "Rode draken; Blauwe leeuwen"));
        assertEquals("Blauwe leeuwen", roster.teamFor("Second player", "Rode draken; Blauwe leeuwen"));
    }
    @Test public void normalizesRsnAndNeverGuessesDuplicates() throws Exception
    {
        SheetTeamRoster roster = SheetTeamRoster.parseCsv(csv(" One_player ,Other Player\nDuplicate,duplicate\nAgain,\nAGAIN,\n"));
        assertEquals("Rood", roster.teamFor("ONE\u00a0PLAYER", "Rood;Blauw"));
        assertEquals("Blauw", roster.teamFor("other_player", "Rood;Blauw"));
        for (String rsn : new String[]{"duplicate", "again", "absent", "", null})
            assertEquals(SheetTeamRoster.UNKNOWN, roster.teamFor(rsn, "Rood;Blauw"));
        assertEquals(SheetTeamRoster.UNKNOWN, roster.teamFor("One player", "Rood"));
        assertEquals(SheetTeamRoster.UNKNOWN, roster.teamFor("One player", "1;2;3"));
    }
    @Test public void missingOrDifferentLayoutFailsClosed() throws Exception
    {
        for (String body : new String[]{"Speler,Team\nAlice,Rood\n", "<html>Login</html>",
            "Speler (in-game),Speler (in-game)\nAlice,Bob,Extra\n"})
        {
            try { SheetTeamRoster.parseCsv(body); fail(body); }
            catch (IOException expected) { }
        }
    }
    @Test public void sourceMustBeSameWorkbookForBothTeams()
    {
        assertEquals(ID, SheetTeamRoster.sourceId(BASE+"?gid=1",BASE+"?gid=2",null));
        assertEquals(ID, SheetTeamRoster.sourceId(null,null,BASE+"?gid=3"));
        assertNull(SheetTeamRoster.sourceId(BASE+"?gid=1","",BASE));
        assertNull(SheetTeamRoster.sourceId(BASE+"?gid=1",BASE.replace(ID,"98765432109876543210")+"?gid=2",BASE));
        assertNull(SheetTeamRoster.sourceId(null,null,"https://attacker.example/"));
    }
    @Test public void cacheDoesNotMixEventsAndDoesNotKeepStaleTeamOnFailure() throws Exception
    {
        AtomicInteger calls = new AtomicInteger();
        SheetTeamRoster.Lookup lookup = new SheetTeamRoster.Lookup(id -> {
            if (calls.incrementAndGet() > 1) throw new IOException("offline");
            return SheetTeamRoster.parseCsv(csv("Alice,Bob\n"));
        });
        assertEquals("Blauw",lookup.teamFor(ID,"Bob","Rood;Blauw",1000));
        assertEquals("Tigers",lookup.teamFor(ID,"Bob","Lions;Tigers",2000));
        assertEquals(1,calls.get());
        assertEquals(SheetTeamRoster.UNKNOWN,lookup.teamFor(ID,"Bob","Rood;Blauw",31001));
        assertEquals(SheetTeamRoster.UNKNOWN,lookup.teamFor(ID,"Bob","Rood;Blauw",32000));
        assertEquals(2,calls.get());
    }
    @Test public void discordMessageIncludesServerTeamForTestAndNormalDrops()
    {
        DutchNationsApi.DropTestReport report = new DutchNationsApi.DropTestReport();
        report.rsn="Bob"; report.item="Blue moon helm"; report.quantity=1;
        assertEquals("**Eventdrop (test)**\nSpeler: Bob\nTeam: Blauwe leeuwen\nItem: Blue moon helm\nAantal: 1",
            DutchNationsApi.dropMessage(report,"Blauwe leeuwen",true));
        assertTrue(DutchNationsApi.dropMessage(report,SheetTeamRoster.UNKNOWN,false).contains("\nTeam onbekend\n"));
        assertFalse(DutchNationsApi.dropMessage(report,null,false).contains("Team"));
        assertTrue(DutchNationsApi.dropMessage(report,"**Rood**",false).contains("Team: \\*\\*Rood\\*\\*"));
    }
}

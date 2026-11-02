package nl.dutchnations.server;

import java.io.IOException;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class TeamDropPagesTest
{
    private static final String BASE = "https://docs.google.com/spreadsheets/d/12345678901234567890/edit";
    private static String csv(String team, String rows)
    {
        return team + " — teamoverzicht & invoer\nDrop-ID,Drop,Doel,Behaald,Status,Actief\n" + rows;
    }
    @Test public void validatesBothDistinctPagesAndOnlyClanEvents()
    {
        assertNull(EventTeams.validatePages("CLAN_EVENT", "Rood;Blauw", BASE+"?gid=1", BASE+"#gid=2"));
        assertNull(EventTeams.validatePages("BOSS", null, null, null));
        assertNotNull(EventTeams.validatePages("BOSS", "Rood;Blauw", BASE+"?gid=1", BASE+"?gid=2"));
        assertNotNull(EventTeams.validatePages("CLAN_EVENT", "Rood;Blauw", BASE+"?gid=1", ""));
        assertNotNull(EventTeams.validatePages("CLAN_EVENT", "Rood;Blauw", BASE+"?gid=1", BASE+"#gid=1"));
        assertNotNull(EventTeams.validatePages("CLAN_EVENT", "1;2;3", BASE+"?gid=1", BASE+"?gid=2"));
        assertNull(GoogleSheetDrops.tabKey(BASE+"?gid=1#gid=2"));
        assertNull(GoogleSheetDrops.tabKey("https://attacker.example/"+BASE+"?gid=1"));
    }
    @Test public void readsOnlySelectedUnfinishedAndUnclaimedGoals() throws Exception
    {
        Set<String> items = GoogleSheetDrops.parseTeamInputCsv(csv("Rood",
            "DROP-1,Dragon claws,5,4,Nog nodig,TRUE\n" +
            "DROP-2,Blue moon helm,1,1,Klaar,TRUE\n" +
            "DROP-3,Fish barrel,1,0,Van Blauw,TRUE\n" +
            "DROP-4,Abyssal whip,1,0,Niet geselecteerd,FALSE\n"), "Rood");
        assertEquals(Set.of("Dragon claws"), items);
    }
    @Test public void finishedOrEmptySelectionIsValid() throws Exception
    {
        assertTrue(GoogleSheetDrops.parseTeamInputCsv(csv("Rood", "DROP-1,Dragon claws,5,5,Klaar,TRUE\n"), "Rood").isEmpty());
        assertTrue(GoogleSheetDrops.parseTeamInputCsv(csv("Rood", ""), "Rood").isEmpty());
    }
    @Test public void badTeamOrLayoutFailsClosed() throws Exception
    {
        for (String csv : new String[]{csv("Blauw", ""), "Drop,Doel,Behaald\nDragon claws,1,0\n",
            csv("Rood", "DROP-1,Dragon claws,8,0,Nog nodig,TRUE\n"),
            csv("Rood", "DROP-1,Dragon claws,5,6,Klaar,TRUE\n"),
            csv("Rood", "DROP-1,Dragon claws,5,0,#REF!,TRUE\n"),
            csv("Rood", "DROP-1,Dragon claws,5,0,Nog nodig,TRUE\nDROP-1,Dragon claws,5,0,Nog nodig,TRUE\n")})
        {
            try { GoogleSheetDrops.parseTeamInputCsv(csv,"Rood"); fail(csv); }
            catch (IOException expected) { }
        }
    }

    @Test public void fourApprovedPiecesCloseEveryItemInThatTeamsGroups() throws Exception
    {
        String groups = "DROP-497,Blood Moon pieces,4,%s,%s,TRUE\n"
            + "DROP-498,Blue Moon pieces,4,%s,%s,TRUE\n"
            + "DROP-499,Guthan pieces,4,%s,%s,TRUE\n";
        String closed = String.format(groups, 4,"Klaar",4,"Klaar",4,"Klaar");
        String open = String.format(groups, 3,"Nog nodig",0,"Nog nodig",1,"Nog nodig");
        assertTrue(GoogleSheetDrops.parseTeamInputCsv(csv("Rood",closed),"Rood").isEmpty());
        Set<String> blue = GoogleSheetDrops.parseTeamInputCsv(csv("Blauw",open),"Blauw");
        assertEquals(12,blue.size());
        assertTrue(blue.contains("Dual macuahuitl"));
        assertTrue(blue.contains("Blue moon spear"));
        assertTrue(blue.contains("Guthan's warspear"));
        // Which individual pieces were obtained is intentionally not required.
        assertTrue(GoogleSheetDrops.parseTeamInputCsv(csv("Blauw",closed),"Blauw").isEmpty());
    }

    @Test public void aKnownPlayersTeamChoosesOnlyItsOwnPage() throws Exception
    {
        DutchNationsApi.Event event = new DutchNationsApi.Event();
        event.type="CLAN_EVENT";event.teamNames="Rood;Blauw";
        event.teamOneDropListUrl=BASE+"?gid=1";event.teamTwoDropListUrl=BASE+"?gid=2";
        assertEquals(event.teamOneDropListUrl,DutchNationsApi.teamDropPage(event,"Rood"));
        assertEquals(event.teamTwoDropListUrl,DutchNationsApi.teamDropPage(event,"Blauw"));
        for(String team:new String[]{null,"",SheetTeamRoster.UNKNOWN,"Andere ploeg"})
        {
            try { DutchNationsApi.teamDropPage(event,team);fail(String.valueOf(team)); }
            catch(IOException expected) { }
        }
    }

    @Test public void viewerTeamPageUsesTheSameClosedGroupRules() throws Exception
    {
        String csv="Rood — dropvoortgang\nDrop-ID,Drop,Doel,Behaald,Status,Actief\n"
            + "DROP-497,Blood Moon pieces,4,4,Klaar,TRUE\n";
        assertTrue(GoogleSheetDrops.parseTeamInputCsv(csv,"Rood").isEmpty());
        csv=csv.replace("4,4,Klaar","4,3,Nog nodig");
        assertEquals(4,GoogleSheetDrops.parseTeamInputCsv(csv,"Rood").size());
    }

    @Test public void onlyVerifiedViewerBindingMapsRosterBackToBasis()
    {
        assertEquals("basis",EventViewSync.rosterSource("basis","viewer","viewer"));
        assertEquals("basis",EventViewSync.rosterSource("basis","viewer","basis"));
        assertEquals("other",EventViewSync.rosterSource("basis","viewer","other"));
        assertEquals("viewer",EventViewSync.rosterSource("basis","","viewer"));
        assertNull(EventViewSync.rosterSource("basis","viewer",null));
    }
}

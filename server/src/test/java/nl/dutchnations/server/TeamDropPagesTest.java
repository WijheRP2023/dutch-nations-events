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
}

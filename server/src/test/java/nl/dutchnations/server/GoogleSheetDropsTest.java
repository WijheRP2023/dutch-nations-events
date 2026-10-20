package nl.dutchnations.server;

import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class GoogleSheetDropsTest
{
    @Test public void readsOnlyKnownSheetLinks()
    {
        String id = "1CbSSkKPdpghw9mhFW1yPkv7cXtZrrgEA8r49Yr_nQdQ";
        assertEquals(id, GoogleSheetDrops.sheetId("https://docs.google.com/spreadsheets/d/" + id + "/edit?gid=1#gid=1"));
        assertNull(GoogleSheetDrops.sheetId("http://docs.google.com/spreadsheets/d/" + id));
        assertNull(GoogleSheetDrops.sheetId("https://docs.google.com.evil.example/spreadsheets/d/" + id));
        assertNull(GoogleSheetDrops.sheetId("https://user@docs.google.com/spreadsheets/d/" + id));
    }

    @Test public void readsNamesFromTheExampleLayout() throws Exception
    {
        String json = "{\"values\":[[\"Drop (bewerkbaar)\",\"Doel\"],[\"Abyssal whip\",\"5\"],[\"Dragon pickaxe\",\"2\"],[],[\"Karil's leathertop\",\"3\"]]}";
        assertEquals(3, GoogleSheetDrops.parse(json).size());
        assertTrue(GoogleSheetDrops.parse(json).contains("Abyssal whip"));
    }

    @Test public void readsPublicCsvWithoutAnyGoogleCredentials() throws Exception
    {
        String csv = "Drop (bewerkbaar),Doel\r\n\"Blue moon helm\",3\r\n\"Karil's leathertop\",2\r\n";
        assertTrue(GoogleSheetDrops.parseCsv(csv).contains("Blue moon helm"));
        assertEquals(2, GoogleSheetDrops.parseCsv(csv).size());
    }

    @Test public void findsTheDropColumnsAnywhereInTheChosenTab() throws Exception
    {
        String csv = "Titel,,,\nNotities,,,\n,,,\nX,Drop (bewerkbaar),Doel,Extra\n1,Blue moon helm,3,ok\n";
        assertTrue(GoogleSheetDrops.parseCsv(csv).contains("Blue moon helm"));
    }

    @Test public void rejectsUnexpectedOrAmbiguousSheet()
    {
        for (String json : new String[] {"{}", "{\"values\":[[\"Naam\",\"Doel\"],[\"Whip\",\"1\"]]}",
            "{\"values\":[[\"Drop (bewerkbaar)\",\"Doel\"],[\"Whip\",\"1\"],[\"whip\",\"1\"]]}"})
        {
            try { GoogleSheetDrops.parse(json); fail("Unexpected data must fail closed"); }
            catch (IOException expected) { }
        }
    }
}

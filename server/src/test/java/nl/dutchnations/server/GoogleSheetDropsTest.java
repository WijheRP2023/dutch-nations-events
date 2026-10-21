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

    @Test public void readsPvmWarItemNameLayoutAndSkipsSevenTimesObtainedDrops() throws Exception
    {
        String csv = "Titel,,,,\nItem name,Points,#1 obtained by,#2 obtained by,#3 obtained by,#4 obtained by,#5 obtained by,#6 obtained by,#7 obtained by\n"
            + "Blue moon helm,5,Robin,Koen,Stefan,A,B,C,D\n"
            + "Abyssal whip,5,Robin,Koen,Stefan,A,B,C,\n"
            + "Dragon claws,5,,,,,,,\n";
        assertFalse(GoogleSheetDrops.parseCsv(csv).contains("Blue moon helm"));
        assertTrue(GoogleSheetDrops.parseCsv(csv).contains("Abyssal whip"));
        assertTrue(GoogleSheetDrops.parseCsv(csv).contains("Dragon claws"));
    }

    @Test public void completedPvmWarSheetReturnsAnEmptyList() throws Exception
    {
        String csv = "Item name,#1 obtained by,#2 obtained by,#3 obtained by,#4 obtained by,#5 obtained by,#6 obtained by,#7 obtained by\n"
            + "Blue moon helm,A,B,C,D,E,F,G\n";
        assertTrue(GoogleSheetDrops.parseCsv(csv).isEmpty());
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

    @Test public void readsBingoTasksAndRespectsCompletionCheckboxes() throws Exception
    {
        String csv = ",,Aantal\nItem,,1,2,3\n3x ancient icon,FALSE\nany inquisitor piece,FALSE\nLightbearer,TRUE\n,,Shard 1,Shard 2\n";
        java.util.Set<String> rules = GoogleSheetDrops.parseCsv(csv);
        assertEquals(2, rules.size());
        assertTrue(rules.contains("@contains:3x ancient icon"));
        assertTrue(rules.contains("@namecontains:inquisitor"));
        assertTrue(rules.stream().anyMatch(rule -> GoogleSheetDrops.matchesItem(rule, "Ancient icon")));
        assertTrue(rules.stream().anyMatch(rule -> GoogleSheetDrops.matchesItem(rule, "Inquisitor's hauberk")));
        assertFalse(rules.stream().anyMatch(rule -> GoogleSheetDrops.matchesItem(rule, "Lightbearer")));
    }

    @Test public void bingoCanBeReusedWithClearedCheckboxesAndWithoutItemHeader() throws Exception
    {
        assertTrue(GoogleSheetDrops.parseCsv(",,Aantal\n,,1,2\nLightbearer,TRUE\n").isEmpty());
        assertTrue(GoogleSheetDrops.parseCsv(",,Aantal\n,,1,2\nLightbearer,FALSE\n").contains("@contains:Lightbearer"));
    }

    @Test public void matchesOnlyLiteralNamesOrExplicitNameFragments()
    {
        assertTrue(GoogleSheetDrops.matchesItem("@contains:3x occult necklace", "Occult necklace"));
        assertFalse(GoogleSheetDrops.matchesItem("@contains:3x occult necklaces", "Occult necklace"));
        assertTrue(GoogleSheetDrops.matchesItem("@namecontains:inquisitor", "Inquisitor’s great helm"));
        assertFalse(GoogleSheetDrops.matchesItem("@namecontains:inquisitor", "Torva full helm"));
        assertFalse(GoogleSheetDrops.matchesItem("@namecontains:inquisitor", "Notinquisitor helm"));
        assertFalse(GoogleSheetDrops.matchesItem("@contains:any nex unique", "Nihil horn"));
        assertFalse(GoogleSheetDrops.matchesItem("Dragon claws", "Dragon claw"));
    }
}

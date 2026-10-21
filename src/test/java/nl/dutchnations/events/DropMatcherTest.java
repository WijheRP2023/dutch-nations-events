package nl.dutchnations.events;

import java.time.OffsetDateTime;
import org.junit.Test;
import static org.junit.Assert.*;

public class DropMatcherTest
{
    @Test public void onlyMatchesSelectedItemsDuringEvent()
    {
        OffsetDateTime now = OffsetDateTime.now();
        ClanFeed.ClanEvent event = new ClanFeed.ClanEvent();
        event.startsAt = now.minusMinutes(1).toString(); event.endsAt = now.plusMinutes(1).toString();
        event.dropItems = "Dragon claws; Twisted bow";
        assertTrue(DropMatcher.matches(event, "dragon CLAWS", now));
        assertFalse(DropMatcher.matches(event, "Dragon claw", now));
        assertFalse(DropMatcher.matches(event, "Twisted bow", now.plusMinutes(2)));
        assertFalse(DropMatcher.matches(event, "Twisted bow", now.minusMinutes(2)));
        assertFalse(DropMatcher.matches(event, null, now));
        event.dropItems = "Karil’s coif";
        assertTrue(DropMatcher.matches(event, "Karil's coif", now));
    }

    @Test public void sourceSnapshotContainsBothItemAndBarrowsNames() throws Exception
    {
        try (java.io.InputStream stream = getClass().getResourceAsStream("/pvm-war-2026-drops.txt"))
        {
            assertNotNull(stream);
            String list = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(list.contains("Dragon claws"));
            assertTrue(list.contains("Karil’s coif"));
            assertTrue(list.length() < 12000);
        }
    }

    @Test public void sheetNamesOverrideManualNamesAndRequireAnActiveEvent()
    {
        OffsetDateTime now = OffsetDateTime.now();
        ClanFeed.ClanEvent event = new ClanFeed.ClanEvent();
        event.startsAt = now.minusMinutes(1).toString(); event.endsAt = now.plusMinutes(1).toString();
        event.dropItems = "Old item";
        assertTrue(DropMatcher.matches(event, "Abyssal whip; Dragon pickaxe", "abyssal whip", now));
        assertFalse(DropMatcher.matches(event, "Abyssal whip", "Old item", now));
        assertFalse(DropMatcher.matches(event, null, "Abyssal whip", now));
        assertFalse(DropMatcher.matches(event, "Abyssal whip", "Abyssal whip", now.plusMinutes(2)));
    }

    @Test public void onlyAcceptsRealGoogleSheetsLinks()
    {
        String id = "1CbSSkKPdpghw9mhFW1yPkv7cXtZrrgEA8r49Yr_nQdQ";
        assertTrue(GoogleSheetLink.supported("https://docs.google.com/spreadsheets/d/" + id + "/edit?gid=1"));
        assertFalse(GoogleSheetLink.supported("https://docs.google.com.evil.test/spreadsheets/d/" + id));
        assertFalse(GoogleSheetLink.supported("http://docs.google.com/spreadsheets/d/" + id));
    }

    @Test public void bingoUsesLiteralFullNamesAndExplicitNameFragments()
    {
        assertTrue(DropMatcher.matchesItem("@contains:3x occult necklace", "Occult necklace"));
        assertFalse(DropMatcher.matchesItem("@contains:3x occult necklaces", "Occult necklace"));
        assertTrue(DropMatcher.matchesItem("@namecontains:inquisitor", "Inquisitor’s great helm"));
        assertFalse(DropMatcher.matchesItem("@namecontains:inquisitor", "Torva full helm"));
        assertFalse(DropMatcher.matchesItem("@namecontains:inquisitor", "Notinquisitor helm"));
        assertFalse(DropMatcher.matchesItem("@contains:any nex unique", "Nihil horn"));
        assertFalse(DropMatcher.matchesItem("Dragon claws", "Dragon claw"));
    }

    @Test public void captureEligibilityStopsExactlyAtTheEndOfTheEvent()
    {
        OffsetDateTime start = OffsetDateTime.parse("2026-10-21T20:00:00+02:00");
        OffsetDateTime end = start.plusHours(1);
        ClanFeed.ClanEvent event = new ClanFeed.ClanEvent();
        event.startsAt = start.toString(); event.endsAt = end.toString(); event.dropItems = "Blue moon helm";
        assertFalse(DropMatcher.matches(event, "Blue moon helm", start.minusNanos(1)));
        assertTrue(DropMatcher.matches(event, "Blue moon helm", start));
        assertTrue(DropMatcher.matches(event, "Blue moon helm", end.minusNanos(1)));
        assertFalse(DropMatcher.matches(event, "Blue moon helm", end));
    }
}

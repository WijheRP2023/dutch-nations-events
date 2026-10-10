package nl.dutchnations.server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DutchNationsApiSecurityTest
{
    @Test
    public void onlyRevealsBossCodewordWhileEventIsActive() throws Exception
    {
        Path directory = Files.createTempDirectory("dutch-nations-api-test");
        DutchNationsApi.Store store = new DutchNationsApi.Store(directory.resolve("state.json"));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        DutchNationsApi.Event future = event("future", now.plusHours(1), now.plusHours(2), "GEHEIM");
        DutchNationsApi.Event active = event("active", now.minusMinutes(1), now.plusHours(1), "ACTIEF");
        store.addEvent(future);
        store.addEvent(active);

        DutchNationsApi.Feed feed = store.feed();
        assertEquals("", feed.events.get(0).codeword);
        assertEquals("ACTIEF", feed.events.get(1).codeword);
    }

    @Test
    public void detectsOverlappingEventsButNotAdjacentEvents() throws Exception
    {
        Path directory = Files.createTempDirectory("dutch-nations-conflict-test");
        DutchNationsApi.Store store = new DutchNationsApi.Store(directory.resolve("state.json"));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
        DutchNationsApi.Event existing = event("existing", now, now.plusHours(2), "GEHEIM");
        store.addEvent(existing);
        assertEquals("existing", store.findConflict(event("overlap", now.plusMinutes(30), now.plusHours(3), "")).id);
        assertEquals(null, store.findConflict(event("adjacent", now.plusHours(2), now.plusHours(3), "")));
    }

    @Test
    public void roleOverviewContainsMetadataButNoTokenHashes() throws Exception
    {
        Path directory = Files.createTempDirectory("dutch-nations-role-overview-test");
        DutchNationsApi.Store store = new DutchNationsApi.Store(directory.resolve("state.json"));
        store.saveRole("Koenb1", "MANAGER", "secret-hash");
        DutchNationsApi.Member member = store.roles().stream().filter(value -> "Koenb1".equals(value.rsn)).findFirst().get();
        assertEquals("MANAGER", member.role);
        assertTrue(member.updatedAt != null && !member.updatedAt.isEmpty());
    }
    @Test
    public void removesExpiredEventsRegardlessOfType() throws Exception
    {
        Path directory = Files.createTempDirectory("dutch-nations-expired-test");
        DutchNationsApi.Store store = new DutchNationsApi.Store(directory.resolve("state.json"));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        DutchNationsApi.Event expired = event("expired", now.minusHours(2), now.minusMinutes(1), "");
        expired.type = "MASS";
        store.addEvent(expired);
        store.removeExpired();
        assertTrue(store.feed().events.isEmpty());
    }
    @Test
    public void administratorCanManageEventsWithoutOwnerRights()
    {
        DutchNationsApi.Actor administrator = new DutchNationsApi.Actor("admin", "ADMINISTRATOR");
        assertTrue(administrator.administrator());
        assertTrue(administrator.canManageEvents());
        assertFalse(administrator.owner());
    }

    @Test
    public void learnerHostCanOnlyManageLearnerEvents()
    {
        DutchNationsApi.Actor learnerHost = new DutchNationsApi.Actor("teacher", "TEACHER");
        assertTrue(learnerHost.canManageEvents());
        assertTrue(learnerHost.canManageEventType("LEARNER"));
        assertFalse(learnerHost.canManageEventType("BOSS"));
        assertFalse(learnerHost.canManageEventType("MASS"));
    }
    @Test
    public void assignedRolesCanBeDetectedAndRemoved() throws Exception
    {
        Path directory = Files.createTempDirectory("dutch-nations-role-test");
        DutchNationsApi.Store store = new DutchNationsApi.Store(directory.resolve("state.json"));
        store.saveRole("Koenb1", "MANAGER", "hash");
        assertTrue(store.hasAssignedRole("koenb1"));
        store.removeRole("KOENB1");
        assertFalse(store.hasAssignedRole("Koenb1"));
    }

    @Test
    public void roleTokenSurvivesServerRestart() throws Exception
    {
        Path directory = Files.createTempDirectory("dutch-nations-role-restart-test");
        Path state = directory.resolve("state.json");
        DutchNationsApi.Store beforeRestart = new DutchNationsApi.Store(state);
        beforeRestart.saveRole("Q-Dance Jr", "ADMINISTRATOR", "current-token-hash");

        DutchNationsApi.Store afterRestart = new DutchNationsApi.Store(state);
        DutchNationsApi.Actor actor = afterRestart.actorForHash("current-token-hash");

        assertTrue(actor != null);
        assertEquals("Q-Dance Jr", actor.rsn);
        assertTrue(actor.administrator());
    }

    @Test
    public void learnerDiscordMessageContainsDescriptionAndPreparation() throws Exception
    {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        DutchNationsApi.Event event = event("learner", now.plusDays(1), now.plusDays(1).plusHours(2), "");
        event.type = "LEARNER";
        event.title = "CoX Learner";
        event.discordDescription = "We oefenen de basis samen.";
        event.checklist = "Gear;Voice-chat";
        event.requiredPlugins = "Quest Helper;Tile Packs";
        event.strategyWikiUrl = "https://oldschool.runescape.wiki/w/Chambers_of_Xeric/Strategies";
        event.youtubeUrl = "https://www.youtube.com/watch?v=test";
        Method formatter = DutchNationsApi.class.getDeclaredMethod("discordEventText", DutchNationsApi.Event.class);
        formatter.setAccessible(true);
        String text = (String) formatter.invoke(null, event);
        assertTrue(text.contains("We oefenen de basis samen."));
        assertTrue(text.contains("**Voorbereiden**"));
        assertTrue(text.contains("• Plug-ins: Quest Helper, Tile Packs"));
        assertTrue(text.contains("• Wiki: https://oldschool.runescape.wiki/w/Chambers_of_Xeric/Strategies"));
        assertTrue(text.contains("• YouTube: https://www.youtube.com/watch?v=test"));
    }

    @Test
    public void longDiscordMessageIsSplitWithoutLosingText() throws Exception
    {
        String longText = "a".repeat(4500);
        Method splitter = DutchNationsApi.class.getDeclaredMethod("splitDiscordMessage", String.class);
        splitter.setAccessible(true);
        @SuppressWarnings("unchecked") List<String> parts = (List<String>) splitter.invoke(null, longText);
        assertEquals(3, parts.size());
        assertTrue(parts.stream().allMatch(part -> part.length() <= 2000));
        assertEquals(longText, String.join("", parts));
    }
    private static DutchNationsApi.Event event(String id, OffsetDateTime start, OffsetDateTime end, String codeword)
    {
        DutchNationsApi.Event event = new DutchNationsApi.Event();
        event.id = id;
        event.type = "BOSS";
        event.title = "Boss event";
        event.startsAt = start.toString();
        event.endsAt = end.toString();
        event.world = "";
        event.host = "";
        event.description = "";
        event.codeword = codeword;
        return event;
    }
}

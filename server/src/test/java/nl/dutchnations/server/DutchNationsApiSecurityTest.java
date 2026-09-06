package nl.dutchnations.server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

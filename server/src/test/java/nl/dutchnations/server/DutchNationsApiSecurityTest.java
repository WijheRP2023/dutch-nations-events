package nl.dutchnations.server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

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

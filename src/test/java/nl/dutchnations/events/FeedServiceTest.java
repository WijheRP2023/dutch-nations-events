package nl.dutchnations.events;

import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import org.junit.Test;
import static org.junit.Assert.*;

public class FeedServiceTest
{
    private final FeedService service = new FeedService(new OkHttpClient(), new Gson());
    @Test public void acceptsTimedEvent()
    {
        ClanFeed f = service.parse("{\"updatedAt\":\"2026-07-20T18:00:00Z\",\"events\":[{\"id\":\"1\",\"type\":\"BOSS\",\"title\":\"CoX\",\"codeword\":\"ORANJE\",\"startsAt\":\"2026-08-02T20:00:00+02:00\",\"endsAt\":\"2026-08-02T22:00:00+02:00\"}]}");
        assertNotNull(f); assertEquals("", f.events.get(0).codeword);
    }
    @Test public void acceptsLearnerChecklist()
    {
        ClanFeed feed = service.parse("{\"updatedAt\":\"2026-07-20T18:00:00Z\",\"events\":[{\"id\":\"1\",\"type\":\"LEARNER\",\"title\":\"ToA\",\"world\":\"366\",\"checklist\":\"Gear;Voice-chat\",\"startsAt\":\"2026-08-02T20:00:00+02:00\",\"endsAt\":\"2026-08-02T22:00:00+02:00\"}]} ");
        assertNotNull(feed); assertEquals("Gear;Voice-chat", feed.events.get(0).checklist);
    }
    @Test public void rejectsEndBeforeStart()
    {
        assertNull(service.parse("{\"updatedAt\":\"2026-07-20T18:00:00Z\",\"events\":[{\"id\":\"1\",\"type\":\"BOSS\",\"title\":\"CoX\",\"codeword\":\"X\",\"startsAt\":\"2026-08-02T22:00:00+02:00\",\"endsAt\":\"2026-08-02T20:00:00+02:00\"}]}"));
    }
}

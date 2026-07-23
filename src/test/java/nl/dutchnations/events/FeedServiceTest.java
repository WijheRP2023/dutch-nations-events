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
        ClanFeed feed = service.parse("{\"updatedAt\":\"2026-07-20T18:00:00Z\",\"events\":[{\"id\":\"1\",\"type\":\"LEARNER\",\"title\":\"ToA\",\"world\":\"366\",\"checklist\":\"Gear;Voice-chat\",\"requiredPlugins\":\"Tile Packs;Quest Helper\",\"startsAt\":\"2026-08-02T20:00:00+02:00\",\"endsAt\":\"2026-08-02T22:00:00+02:00\"}]} ");
        assertNotNull(feed); assertEquals("Gear;Voice-chat", feed.events.get(0).checklist);
        assertEquals("Tile Packs;Quest Helper", feed.events.get(0).requiredPlugins);
    }
    @Test public void acceptsMassPreparationAndPlugins()
    {
        ClanFeed feed = service.parse("{\"updatedAt\":\"2026-07-20T18:00:00Z\",\"events\":[{\"id\":\"mass-1\",\"type\":\"MASS\",\"title\":\"Clan mass\",\"world\":\"366\",\"checklist\":\"Voice-chat;Gear\",\"requiredPlugins\":\"Tile Packs\",\"strategyWikiUrl\":\"https://oldschool.runescape.wiki/w/Tombs_of_Amascut/Strategies\",\"startsAt\":\"2026-08-03T20:00:00+02:00\",\"endsAt\":\"2026-08-03T22:00:00+02:00\"}]}");
        assertNotNull(feed); assertTrue(feed.events.get(0).supportsPreparation());
        assertEquals("Voice-chat;Gear", feed.events.get(0).checklist);
        assertEquals("Tile Packs", feed.events.get(0).requiredPlugins);
        assertEquals("https://oldschool.runescape.wiki/w/Tombs_of_Amascut/Strategies", feed.events.get(0).strategyWikiUrl);
    }
    @Test public void rejectsEndBeforeStart()
    {
        assertNull(service.parse("{\"updatedAt\":\"2026-07-20T18:00:00Z\",\"events\":[{\"id\":\"1\",\"type\":\"BOSS\",\"title\":\"CoX\",\"codeword\":\"X\",\"startsAt\":\"2026-08-02T22:00:00+02:00\",\"endsAt\":\"2026-08-02T20:00:00+02:00\"}]}"));
    }
}

package nl.dutchnations.events;

import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import org.junit.Test;
import static org.junit.Assert.*;

public class FeedServiceTest
{
    private final FeedService service = new FeedService(new OkHttpClient(), new Gson());

    @Test public void normalOwnerCanSaveLiveScreenshotChannelWithoutTestFlags() throws Exception
    {
        assertFalse(Boolean.getBoolean("dutch.nations.local"));
        assertFalse(Boolean.getBoolean("dutch.nations.dropTest"));
        java.util.concurrent.atomic.AtomicReference<okhttp3.Request> request = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> result = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch finished = new java.util.concurrent.CountDownLatch(1);
        String channel = "https://discord.com/channels/123/456";
        OkHttpClient isolated = new OkHttpClient.Builder().addInterceptor(chain ->
        {
            request.set(chain.request());
            return new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
                .body(okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"), "{\"dropChannelUrl\":\"" + channel + "\"}")).build();
        }).build();
        new FeedService(isolated, new Gson()).saveDropChannel(channel, "test-only-owner", new FeedService.AnnouncementChannelListener()
        {
            @Override public void success(String url) { result.set(url); finished.countDown(); }
            @Override public void failure(String message) { result.set(message); finished.countDown(); }
        });
        assertTrue(finished.await(5, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(channel, result.get());
        assertEquals("https://dutch-nations-events.onrender.com/api/drop-channel", request.get().url().toString());
        assertEquals("POST", request.get().method());
        assertEquals("https://dutch-nations-events.onrender.com/api/drop-test", FeedService.DROP_TEST_URL);
    }
    @Test public void acceptsTimedEvent()
    {
        ClanFeed f = service.parse("{\"updatedAt\":\"2026-07-20T18:00:00Z\",\"events\":[{\"id\":\"1\",\"type\":\"BOSS\",\"title\":\"CoX\",\"codeword\":\"ORANJE\",\"startsAt\":\"2026-08-02T20:00:00+02:00\",\"endsAt\":\"2026-08-02T22:00:00+02:00\"}]}");
        assertNotNull(f); assertEquals("", f.events.get(0).codeword);
    }
    @Test public void neverKeepsFutureBossCodewordInClientFeed()
    {
        String futureStart = java.time.OffsetDateTime.now().plusHours(1).toString();
        String futureEnd = java.time.OffsetDateTime.now().plusHours(2).toString();
        ClanFeed feed = service.parse("{\"updatedAt\":\"" + java.time.OffsetDateTime.now() +
            "\",\"events\":[{\"id\":\"future\",\"type\":\"BOSS\",\"title\":\"Future boss\",\"codeword\":\"GEHEIM\",\"startsAt\":\"" +
            futureStart + "\",\"endsAt\":\"" + futureEnd + "\"}]}");
        assertNotNull(feed);
        assertEquals("", feed.events.get(0).codeword);
    }
    @Test public void acceptsLearnerChecklist()
    {
        ClanFeed feed = service.parse("{\"updatedAt\":\"2026-07-20T18:00:00Z\",\"events\":[{\"id\":\"1\",\"type\":\"LEARNER\",\"title\":\"ToA\",\"world\":\"366\",\"checklist\":\"Gear;Voice-chat\",\"requiredPlugins\":\"Tile Packs;Quest Helper\",\"startsAt\":\"2026-08-02T20:00:00+02:00\",\"endsAt\":\"2026-08-02T22:00:00+02:00\"}]} ");
        assertNotNull(feed); assertEquals("Gear;Voice-chat", feed.events.get(0).checklist);
        assertEquals("Tile Packs;Quest Helper", feed.events.get(0).requiredPlugins);
    }
    @Test public void acceptsLearnerTemplates()
    {
        ClanFeed feed = service.parse("{\"updatedAt\":\"2026-07-20T18:00:00Z\",\"learnerTemplates\":[{\"id\":\"toa\",\"name\":\"ToA learner\",\"title\":\"ToA learner\",\"discordDescription\":\"Welkom learner!\",\"checklist\":\"Gear;Voice\",\"requiredPlugins\":\"Quest Helper\",\"strategyWikiUrl\":\"https://oldschool.runescape.wiki/w/Tombs_of_Amascut/Strategies\"}],\"events\":[]}");
        assertNotNull(feed);
        assertEquals(1, feed.learnerTemplates.size());
        assertEquals("ToA learner", feed.learnerTemplates.get(0).name);
        assertEquals("Welkom learner!", feed.learnerTemplates.get(0).discordDescription);
    }
    @Test public void acceptsMultilineTemplateDiscordDescription()
    {
        ClanFeed feed = service.parse("{\"updatedAt\":\"2026-07-20T18:00:00Z\",\"learnerTemplates\":[{\"id\":\"cox\",\"name\":\"CoX learner\",\"title\":\"CoX learner\",\"discordDescription\":\"Regel een\\n\\nRegel twee\"}],\"events\":[]}");
        assertNotNull(feed);
        assertEquals("Regel een\n\nRegel twee", feed.learnerTemplates.get(0).discordDescription);
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

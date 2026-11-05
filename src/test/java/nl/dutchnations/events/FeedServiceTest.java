package nl.dutchnations.events;

import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import org.junit.Test;
import static org.junit.Assert.*;

public class FeedServiceTest
{
    @Test public void ownerCanSetOrClearOnlyTheRequestedTeamChannel() throws Exception
    {
        for(String value:new String[]{"https://discord.com/channels/123/789",""})
        {
            java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<String> saved=new java.util.concurrent.atomic.AtomicReference<>();
            OkHttpClient client=new OkHttpClient.Builder().addInterceptor(chain->{
                assertEquals("Bearer owner-test",chain.request().header("Authorization"));
                okio.Buffer buffer=new okio.Buffer();chain.request().body().writeTo(buffer);
                com.google.gson.JsonObject body=new Gson().fromJson(buffer.readUtf8(),com.google.gson.JsonObject.class);
                assertEquals(3,body.get("team").getAsInt());assertEquals(value,body.get("url").getAsString());
                return new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
                    .body(okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"),"{\"team\":3,\"dropChannelUrl\":"+new Gson().toJson(value)+"}")).build();
            }).build();
            new FeedService(client,new Gson()).saveTeamDropChannel(3,value,"owner-test",new FeedService.AnnouncementChannelListener(){
                @Override public void success(String url){saved.set(url);done.countDown();}
                @Override public void failure(String message){saved.set("ERROR: "+message);done.countDown();}
            });
            assertTrue(done.await(5,java.util.concurrent.TimeUnit.SECONDS));assertEquals(value,saved.get());
        }
    }
    @Test public void eventDraftRetainsThirdTeamPageInTheJsonSentToTheServer()
    {
        EventDraft draft=new EventDraft();draft.teamNames="Rood;Blauw;Groen";draft.teamThreeDropListUrl="https://docs.google.com/spreadsheets/d/12345678901234567890/edit?gid=3";
        ClanFeed.ClanEvent decoded=new Gson().fromJson(new Gson().toJson(draft),ClanFeed.ClanEvent.class);
        assertEquals(draft.teamThreeDropListUrl,decoded.teamThreeDropListUrl);assertEquals(draft.teamNames,decoded.teamNames);
    }
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
        assertEquals("https://dutch-nations-events.onrender.com/api/event-drops", FeedService.DROP_TEST_URL);
        assertEquals("Bearer test-only-owner", request.get().header("Authorization"));
    }

    @Test public void memberCanSendDropWithoutManagementToken() throws Exception
    {
        java.util.concurrent.atomic.AtomicReference<okhttp3.Request> request = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> error = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch finished = new java.util.concurrent.CountDownLatch(1);
        OkHttpClient isolated = new OkHttpClient.Builder().addInterceptor(chain ->
        {
            request.set(chain.request());
            return new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
                .body(okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"), "{\"delivered\":true}")).build();
        }).build();
        new FeedService(isolated, new Gson()).testDrop(new FeedService.DropTestReport(), new FeedService.DropTestListener()
        {
            @Override public void success(boolean delivered) { if (!delivered) error.set("not delivered"); finished.countDown(); }
            @Override public void failure(String message) { error.set(message); finished.countDown(); }
        });
        assertTrue(finished.await(5, java.util.concurrent.TimeUnit.SECONDS));
        assertNull(error.get());
        assertEquals("/api/event-drops", request.get().url().encodedPath());
        assertNull(request.get().header("Authorization"));
    }

    @Test public void memberCanFetchDropListWithoutManagementToken() throws Exception
    {
        java.util.concurrent.atomic.AtomicReference<okhttp3.Request> request = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> items = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch finished = new java.util.concurrent.CountDownLatch(1);
        OkHttpClient isolated = new OkHttpClient.Builder().addInterceptor(chain ->
        {
            request.set(chain.request());
            return new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
                .body(okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"), "{\"source\":\"PVM_WAR_2026\",\"items\":[\"Blue moon helm\"]}")).build();
        }).build();
        new FeedService(isolated, new Gson()).fetchSheetDrops("event-1", new FeedService.DropListListener()
        {
            @Override public void success(String source, String result) { items.set(result); finished.countDown(); }
            @Override public void failure(String message) { items.set(message); finished.countDown(); }
        });
        assertTrue(finished.await(5, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals("Blue moon helm", items.get());
        assertEquals("/api/event-drop-list", request.get().url().encodedPath());
        assertEquals("event-1", request.get().url().queryParameter("eventId"));
        assertNull(request.get().header("Authorization"));
    }
    @Test public void acceptsTimedEvent()
    {
        ClanFeed f = service.parse("{\"updatedAt\":\"2026-07-20T18:00:00Z\",\"events\":[{\"id\":\"1\",\"type\":\"BOSS\",\"title\":\"CoX\",\"codeword\":\"ORANJE\",\"startsAt\":\"2026-08-02T20:00:00+02:00\",\"endsAt\":\"2026-08-02T22:00:00+02:00\"}]}");
        assertNotNull(f); assertEquals("", f.events.get(0).codeword);
    }

    @Test public void keepsTeamDropPagesAndOwnerChannelSettingsInFeed()
    {
        String start = java.time.OffsetDateTime.now().plusHours(1).toString();
        String end = java.time.OffsetDateTime.now().plusHours(2).toString();
        String sheet = "https://docs.google.com/spreadsheets/d/12345678901234567890/edit?gid=";
        ClanFeed feed = service.parse("{\"teamOneDropChannelUrl\":\"https://discord.com/channels/123/456\","
            + "\"teamTwoDropChannelUrl\":\"https://discord.com/channels/123/789\",\"teamThreeDropChannelUrl\":\"\","
            + "\"events\":[{\"id\":\"teams\",\"type\":\"CLAN_EVENT\",\"title\":\"Bingo\",\"startsAt\":\"" + start
            + "\",\"endsAt\":\"" + end + "\",\"teamNames\":\"Rood; Blauw\",\"teamOneDropListUrl\":\"" + sheet
            + "1\",\"teamTwoDropListUrl\":\"" + sheet + "2\",\"teamThreeDropListUrl\":\"\"}]}");
        assertNotNull(feed);
        assertEquals("https://discord.com/channels/123/456", feed.teamOneDropChannelUrl);
        assertEquals("https://discord.com/channels/123/789", feed.teamTwoDropChannelUrl);
        assertEquals("", feed.teamThreeDropChannelUrl);
        assertEquals("Rood; Blauw", feed.events.get(0).teamNames);
        assertEquals(sheet + "1", feed.events.get(0).teamOneDropListUrl);
        assertEquals(sheet + "2", feed.events.get(0).teamTwoDropListUrl);
        assertEquals("", feed.events.get(0).teamThreeDropListUrl);
    }

    @Test public void freshTeamCheckSendsPlayerAndAcceptsACompletedEmptyList() throws Exception
    {
        java.util.concurrent.atomic.AtomicReference<okhttp3.Request> request=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> result=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch finished=new java.util.concurrent.CountDownLatch(1);
        OkHttpClient isolated=new OkHttpClient.Builder().addInterceptor(chain -> {
            request.set(chain.request());
            return new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
                .body(okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"),
                    "{\"source\":\"https://docs.google.com/spreadsheets/d/12345678901234567890/edit?gid=1\",\"items\":[]}")).build();
        }).build();
        new FeedService(isolated,new Gson()).fetchSheetDrops("event-1","One Player",true,new FeedService.DropListListener() {
            @Override public void success(String source,String items) {result.set(items);finished.countDown();}
            @Override public void failure(String message) {result.set(message);finished.countDown();}
        });
        assertTrue(finished.await(5,java.util.concurrent.TimeUnit.SECONDS));
        assertEquals("",result.get());
        assertEquals("One Player",request.get().url().queryParameter("rsn"));
        assertEquals("true",request.get().url().queryParameter("fresh"));
        assertNull(request.get().header("Authorization"));
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

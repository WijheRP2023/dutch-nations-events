package nl.dutchnations.server;

import com.sun.net.httpserver.HttpServer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import org.junit.Test;
import static org.junit.Assert.*;

public class DropTestAuthorizationTest
{
    @Test public void onlyOwnerCanSubmitAndRejectedDropDoesNotChangeState() throws Exception
    {
        Path path = Files.createTempDirectory("dutch-drop-auth-test").resolve("state.json");
        DutchNationsApi.Store store = new DutchNationsApi.Store(path);
        String adminToken = "local-test-admin";
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(adminToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hash = new StringBuilder();
        for (byte value : digest) hash.append(String.format("%02x", value));
        store.saveRole("Admin", "ADMINISTRATOR", hash.toString());
        DutchNationsApi.Event event = new DutchNationsApi.Event();
        event.id = "test-event";
        event.startsAt = java.time.OffsetDateTime.now().minusHours(1).toString();
        event.endsAt = java.time.OffsetDateTime.now().plusHours(1).toString();
        event.codeword = "LOKAAL-TESTWOORD";
        // This test verifies authorization and the unavailable-bot path. It must
        // not depend on the finished public PvM War page being available.
        event.dropSource = "";
        event.dropItems = "Dragon claws;Twisted bow";
        store.addEvent(event);
        Constructor<DutchNationsApi> constructor = DutchNationsApi.class.getDeclaredConstructor(DutchNationsApi.Store.class, String.class);
        constructor.setAccessible(true);
        DutchNationsApi api = constructor.newInstance(store, "local-test-owner");
        Method handler = DutchNationsApi.class.getDeclaredMethod("dropTest", com.sun.net.httpserver.HttpExchange.class);
        handler.setAccessible(true);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/drop-test", exchange -> {
            try { handler.invoke(api, exchange); }
            catch (ReflectiveOperationException ex) { throw new java.io.IOException(ex); }
        });
        Method settings = DutchNationsApi.class.getDeclaredMethod("dropChannel", com.sun.net.httpserver.HttpExchange.class);
        settings.setAccessible(true);
        server.createContext("/api/drop-channel", exchange -> {
            try { settings.invoke(api, exchange); }
            catch (ReflectiveOperationException ex) { throw new java.io.IOException(ex); }
        });
        server.start();
        try
        {
            URI url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/drop-test");
            String body = "{\"eventId\":\"test-event\",\"rsn\":\"Q-Dance Jr\",\"item\":\"Dragon claws\",\"quantity\":1,\"screenshot\":\"iVBORw0KGgo=\"}";
            assertEquals(405, request(url, null, body, false));
            assertEquals(403, request(url, null, body, true));
            assertEquals(403, request(url, adminToken, body, true));
            assertEquals(400, request(url, "local-test-owner", "{}", true));
            assertEquals(409, request(url, "local-test-owner", body.replace("Dragon claws", "Other item"), true));
            assertEquals(409, request(url, "local-test-owner", body, true));
            URI settingUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/drop-channel");
            String setting = "{\"url\":\"https://discord.com/channels/1239712812822167688/1548661330209280060\"}";
            assertEquals(403, request(settingUrl, adminToken, setting, true));
            assertEquals(200, request(settingUrl, "local-test-owner", setting, true));
            assertEquals(405, request(settingUrl, "local-test-owner", "", false));
            assertEquals("https://discord.com/channels/1239712812822167688/1548661330209280060", store.dropChannelUrl());
            assertEquals(store.dropChannelUrl(), new DutchNationsApi.Store(path).dropChannelUrl());
            byte[] before = Files.readAllBytes(path);
            assertEquals(409, request(url, "local-test-owner", body, true));
            assertArrayEquals(before, Files.readAllBytes(path));
            DutchNationsApi.Store afterRestart = new DutchNationsApi.Store(path);
            assertEquals("LOKAAL-TESTWOORD", afterRestart.event("test-event").codeword);
            assertEquals("ADMINISTRATOR", afterRestart.actorForHash(hash.toString()).role);
        }
        finally { server.stop(0); }
    }

    @Test public void onlyActiveConfiguredEventItemsAreEligible()
    {
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        DutchNationsApi.Event event = new DutchNationsApi.Event();
        event.startsAt = now.minusMinutes(1).toString();
        event.endsAt = now.plusMinutes(1).toString();
        event.dropSource = "PVM_WAR_2026";
        event.dropItems = "Dragon claws;Osmumten’s fang";
        assertTrue(DutchNationsApi.dropTestEligible(event, "dragon claws", now));
        assertTrue(DutchNationsApi.dropTestEligible(event, "Osmumten's fang", now));
        assertFalse(DutchNationsApi.dropTestEligible(event, "Twisted bow", now));
        assertFalse(DutchNationsApi.dropTestEligible(event, "Dragon claws", now.plusMinutes(2)));
        event.dropSource = "";
        assertFalse(DutchNationsApi.dropTestEligible(event, "Dragon claws", now));
    }

    private static int request(URI url, String token, String body, boolean post) throws Exception
    {
        HttpRequest.Builder request = HttpRequest.newBuilder(url);
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (post) request.POST(HttpRequest.BodyPublishers.ofString(body)); else request.GET();
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}

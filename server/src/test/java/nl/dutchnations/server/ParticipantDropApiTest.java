package nl.dutchnations.server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParticipantDropApiTest
{
    @Test public void memberEndpointsRequireActiveEventsButNotManagementTokens() throws Exception
    {
        DutchNationsApi.Store store = new DutchNationsApi.Store(Files.createTempDirectory("participant-drop-api").resolve("state.json"));
        OffsetDateTime now = OffsetDateTime.now();
        DutchNationsApi.Event event = new DutchNationsApi.Event();
        event.id="event"; event.type="BOSS"; event.title="Test";
        event.startsAt=now.minusMinutes(1).toString(); event.endsAt=now.plusMinutes(1).toString();
        store.addEvent(event);
        Constructor<DutchNationsApi> constructor = DutchNationsApi.class.getDeclaredConstructor(DutchNationsApi.Store.class, String.class);
        constructor.setAccessible(true);
        DutchNationsApi api = constructor.newInstance(store, "test-only-owner");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        route(server, "/api/event-drop-list", api, "googleDropList", true);
        route(server, "/api/event-drops", api, "dropTest", true);
        route(server, "/api/drop-list", api, "googleDropList", false);
        route(server, "/api/drop-test", api, "dropTest", false);
        Method channel = DutchNationsApi.class.getDeclaredMethod("dropChannel", HttpExchange.class);
        channel.setAccessible(true);
        server.createContext("/api/drop-channel", exchange -> {
            try { channel.invoke(api, exchange); } catch(Exception ex) { throw new java.io.IOException(ex); }
        });
        server.start();
        try
        {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            assertEquals(403, get(base+"/api/drop-list?eventId=event").statusCode());
            assertEquals(403, post(base+"/api/drop-test", "{}").statusCode());
            assertEquals(403, post(base+"/api/drop-channel", "{}").statusCode());
            assertEquals(409, get(base+"/api/event-drop-list?eventId=missing").statusCode());
            assertEquals(404, get(base+"/api/event-drop-list?eventId=event").statusCode());
            assertEquals(400, post(base+"/api/event-drops", "{}").statusCode());
            String png = java.util.Base64.getEncoder().encodeToString(png());
            String report="{\"eventId\":\"event\",\"rsn\":\"Member\",\"item\":\"Blue moon helm\",\"quantity\":1,\"screenshot\":\""+png+"\"}";
            HttpResponse<String> active = post(base+"/api/event-drops", report);
            assertEquals(active.body(), 409, active.statusCode());
            assertTrue(active.body().contains("geen ondersteunde droplijst"));
            event.endsAt=now.minusSeconds(1).toString();
            // Store holds the event object, so this simulates the event ending without restarting the API.
            assertEquals(409, get(base+"/api/event-drop-list?eventId=event").statusCode());
            assertTrue(post(base+"/api/event-drops", report).body().contains("Event is niet actief"));
        }
        finally { server.stop(0); }
    }

    @Test public void rejectsInvalidAndOversizedPngHeaders()
    {
        assertTrue(DutchNationsApi.validParticipantPng(png()));
        assertFalse(DutchNationsApi.validParticipantPng(new byte[8]));
        byte[] image=png(); image[0]=0;
        assertFalse(DutchNationsApi.validParticipantPng(image));
        image=png(); java.nio.ByteBuffer.wrap(image).putInt(16, 50000);
        assertFalse(DutchNationsApi.validParticipantPng(image));
        image=png(); java.nio.ByteBuffer.wrap(image).putInt(20, -1);
        assertFalse(DutchNationsApi.validParticipantPng(image));
    }

    private static byte[] png()
    {
        // Real one-pixel PNG, without allocating or decoding untrusted image data.
        return java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
    }

    private static void route(HttpServer server,String path,DutchNationsApi api,String name,boolean participant) throws Exception
    {
        Method method=DutchNationsApi.class.getDeclaredMethod(name,HttpExchange.class,boolean.class);
        method.setAccessible(true);
        server.createContext(path, exchange -> {
            try { method.invoke(api,exchange,participant); } catch(Exception ex) { throw new java.io.IOException(ex); }
        });
    }

    private static HttpResponse<String> get(String url) throws Exception
    { return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).GET().build(),HttpResponse.BodyHandlers.ofString()); }

    private static HttpResponse<String> post(String url,String body) throws Exception
    { return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString()); }
}

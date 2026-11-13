package nl.dutchnations.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import org.junit.Test;
import static org.junit.Assert.*;

public class OwnerLogsTest
{
    @Test public void onlyOwnerCanClearEachLogSeparatelyAndDeletionPersists() throws Exception
    {
        Path file = Files.createTempDirectory("owner-logs-test-").resolve("state.json");
        DutchNationsApi.Store store = new DutchNationsApi.Store(file);
        store.audit(null, "beheer behouden");
        store.error("Zeeslag", "foutmelding");
        store.saveDropChannelUrl("https://discord.com/channels/123/456");
        StringBuilder hash = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest("manager-test".getBytes(StandardCharsets.UTF_8)))
            hash.append(String.format("%02x", b));
        store.saveRole("Manager", "MANAGER", hash.toString());
        var constructor = DutchNationsApi.class.getDeclaredConstructor(DutchNationsApi.Store.class, String.class);
        constructor.setAccessible(true);
        var handler = DutchNationsApi.class.getDeclaredMethod("ownerLogs", HttpExchange.class);
        handler.setAccessible(true);
        var api = constructor.newInstance(store, "owner-test");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/owner-logs", exchange ->
        {
            try { handler.invoke(api, exchange); }
            catch (ReflectiveOperationException failure) { throw new IOException(failure); }
        });
        server.start();
        try
        {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/owner-logs";
            HttpClient client = HttpClient.newHttpClient();
            assertEquals(403, request(client, url + "/errors", "DELETE", null).statusCode());
            assertEquals(403, request(client, url + "/management", "DELETE", "manager-test").statusCode());
            assertEquals(405, request(client, url, "DELETE", "owner-test").statusCode());
            assertEquals(404, request(client, url + "/all", "DELETE", "owner-test").statusCode());
            assertEquals(405, request(client, url + "/errors", "GET", "owner-test").statusCode());
            var read = request(client, url, "GET", "owner-test");
            assertEquals(200, read.statusCode());
            assertTrue(read.body().contains("foutmelding"));
            var clear = request(client, url + "/errors", "DELETE", "owner-test");
            assertEquals(200, clear.statusCode());
            assertTrue(clear.body().contains("beheer behouden"));
            assertFalse(clear.body().contains("foutmelding"));
            assertTrue(new DutchNationsApi.Store(file).ownerLogs().errors.isEmpty());
            store.error("Zeeslag", "nieuwe fout");
            assertEquals(200, request(client, url + "/management", "DELETE", "owner-test").statusCode());
            var reloaded = new DutchNationsApi.Store(file);
            assertTrue(reloaded.ownerLogs().management.isEmpty());
            assertEquals("nieuwe fout", reloaded.ownerLogs().errors.get(0).message);
            assertEquals("https://discord.com/channels/123/456", reloaded.dropChannelUrl());
        }
        finally { server.stop(0); }
    }

    private static HttpResponse<String> request(HttpClient client, String url, String method, String token) throws Exception
    {
        var request = HttpRequest.newBuilder(URI.create(url)).method(method, HttpRequest.BodyPublishers.noBody());
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}

package nl.dutchnations.server;

import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.Test;
import static org.junit.Assert.*;

public class TeamDropChannelsTest
{
    @Test public void ownerOnlyTeamChannelsKeepTheGeneralChannelAndPersist() throws Exception
    {
        Path file=Files.createTempDirectory("team-drop-channels-").resolve("state.json");
        DutchNationsApi.Store store=new DutchNationsApi.Store(file);store.saveDropChannelUrl("https://discord.com/channels/123/456");
        StringBuilder hashBytes=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest("manager-test".getBytes(StandardCharsets.UTF_8)))hashBytes.append(String.format("%02x",b));String hash=hashBytes.toString();
        store.saveRole("Manager","MANAGER",hash);
        Constructor<DutchNationsApi> constructor=DutchNationsApi.class.getDeclaredConstructor(DutchNationsApi.Store.class,String.class);constructor.setAccessible(true);
        DutchNationsApi api=constructor.newInstance(store,"owner-test");Method handler=DutchNationsApi.class.getDeclaredMethod("dropChannel",HttpExchange.class);handler.setAccessible(true);
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/drop-channel",e->{try{handler.invoke(api,e);}catch(Exception failure){throw new java.io.IOException(failure);}});server.start();
        try
        {
            String url="http://127.0.0.1:"+server.getAddress().getPort()+"/api/drop-channel";
            String body="{\"team\":3,\"url\":\"https://discord.com/channels/123/789\"}";
            assertEquals(403,post(url,body,null).statusCode());assertEquals(403,post(url,body,"manager-test").statusCode());
            HttpResponse<String> saved=post(url,body,"owner-test");assertEquals(saved.body(),200,saved.statusCode());assertEquals(3,com.google.gson.JsonParser.parseString(saved.body()).getAsJsonObject().get("team").getAsInt());
            assertEquals("https://discord.com/channels/123/456",store.dropChannelUrl());
            assertEquals("https://discord.com/channels/123/789",new DutchNationsApi.Store(file).teamDropChannels()[2]);
            assertEquals(400,post(url,"{\"team\":4,\"url\":\"https://discord.com/channels/123/789\"}","owner-test").statusCode());
            assertEquals(400,post(url,"{\"team\":1,\"url\":\"https://attacker.example\"}","owner-test").statusCode());
            assertEquals(200,post(url,"{\"team\":3,\"url\":\"\"}","owner-test").statusCode());assertEquals("",store.teamDropChannels()[2]);
            assertEquals("https://discord.com/channels/123/456",store.dropChannelUrl());
        }finally{server.stop(0);}
    }
    @Test public void routingUsesOnlyTheKnownTeamAndFallsBackWithoutGuessing()
    {
        DutchNationsApi.Event e=new DutchNationsApi.Event();e.type="CLAN_EVENT";e.teamNames="Rood;Blauw;Groen";
        assertEquals("green",DutchNationsApi.dropChannelFor(e,"general","Groen","red","blue","green"));
        assertEquals("general",DutchNationsApi.dropChannelFor(e,"general",SheetTeamRoster.UNKNOWN,"red","blue","green"));
        assertEquals("general",DutchNationsApi.dropChannelFor(e,"general","Groen","red","blue",""));
        e.teamNames="Rood;Blauw";assertEquals("general",DutchNationsApi.dropChannelFor(e,"general","Groen","red","blue","green"));
        e.type="BOSS";assertEquals("general",DutchNationsApi.dropChannelFor(e,"general","Rood","red","blue","green"));
    }
    @Test public void publicTeamExportKeepsExplicitGidAndNeverFollowsUntrustedRedirects() throws Exception
    {
        assertEquals("https://docs.google.com/spreadsheets/d/12345678901234567890/export?format=csv&gid=1726059411",
            GoogleSheetDrops.teamExportUri("https://docs.google.com/spreadsheets/d/12345678901234567890/edit?gid=1726059411#gid=1726059411").toString());
        assertTrue(GoogleSheetDrops.allowedExportRedirect(URI.create("https://doc-04-c0-sheets.googleusercontent.com/export/example")));
        for(String target:new String[]{"http://doc-04-c0-sheets.googleusercontent.com/x","https://attacker.example/x","https://accounts.google.com/login","https://doc-sheets.googleusercontent.com.attacker.example/x","https://user@doc-sheets.googleusercontent.com/x"})
            assertFalse(target,GoogleSheetDrops.allowedExportRedirect(URI.create(target)));
    }
    private static HttpResponse<String> post(String url,String json,String token) throws Exception
    {
        HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(url)).header("Content-Type","application/json");
        if(token!=null)b.header("Authorization","Bearer "+token);
        return HttpClient.newHttpClient().send(b.POST(HttpRequest.BodyPublishers.ofString(json)).build(),HttpResponse.BodyHandlers.ofString());
    }
}

package nl.dutchnations.server;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import org.junit.Test;
import static org.junit.Assert.*;

public class EventTeamsTest
{
    private static final String HEADER = "Drop (bewerkbaar),Team,Doel,Behaald\n";

    @Test public void goalsAndDropsBelongToEachTeam() throws Exception
    {
        String csv = HEADER + "Blue moon helm,Rood,1,1\nBlue moon helm,Blauw,3,2\nDragon claws,Rood,2,0\n";
        assertTrue(GoogleSheetDrops.parseTeamCsv(csv, "Rood; Blauw").contains("Blue moon helm"));
        assertTrue(GoogleSheetDrops.parseTeamCsv(csv, "Rood; Blauw").contains("Dragon claws"));
        assertFalse(GoogleSheetDrops.parseTeamCsv(csv.replace("Blauw,3,2", "Blauw,3,3"), "Rood; Blauw").contains("Blue moon helm"));
    }

    @Test public void allCompleteIsAValidEmptyListAndResetMakesItReusable() throws Exception
    {
        String csv = HEADER + "Blue moon helm,Rood,1,1\nDragon claws,Blauw,2,9\n";
        assertTrue(GoogleSheetDrops.parseTeamCsv(csv, "Rood;Blauw").isEmpty());
        assertEquals(2, GoogleSheetDrops.parseTeamCsv(csv.replace(",1,1", ",1,0").replace(",2,9", ",2,"), "Rood;Blauw").size());
    }

    @Test public void invalidOrMissingTeamDataFailsClosed() throws Exception
    {
        for (String csv : new String[]{
            HEADER + "Blue moon helm,Rood,1,0\n",
            HEADER + "Blue moon helm,Rood,1,0\nDragon claws,Groen,1,0\n",
            HEADER + "Blue moon helm,Rood,0,0\nDragon claws,Blauw,1,0\n",
            HEADER + "Blue moon helm,Rood,1,-1\nDragon claws,Blauw,1,0\n",
            HEADER + "Blue moon helm,Rood,1,#REF!\nDragon claws,Blauw,1,0\n",
            HEADER + "Blue moon helm,Rood,1,0\nBLUE MOON HELM,rood,2,0\nDragon claws,Blauw,1,0\n",
            "Drop (bewerkbaar),Doel\nBlue moon helm,1\n"})
        {
            try { GoogleSheetDrops.parseTeamCsv(csv, "Rood;Blauw"); fail(csv); }
            catch (java.io.IOException expected) { }
        }
    }

    @Test public void supportsSevenTeamsAndGroupExpansion() throws Exception
    {
        StringBuilder csv = new StringBuilder(HEADER);
        for (int i = 1; i <= 7; i++) csv.append("any barrows piece,T").append(i).append(",8,").append(i == 7 ? 0 : 8).append('\n');
        assertEquals(24, GoogleSheetDrops.parseTeamCsv(csv.toString(), "T1;T2;T3;T4;T5;T6;T7").size());
        for (String names : new String[]{"Rood", "Rood;rood", "Rood;", "Rood;<Blauw>", "Rood;Bla\nuw", "1;2;3;4;5;6;7;8"})
        {
            try { EventTeams.names(names); fail(names); } catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void teamsOnlyForClanEventsAndPersistAcrossRestart() throws Exception
    {
        Method validate = DutchNationsApi.class.getDeclaredMethod("validateEvent", DutchNationsApi.Event.class);
        validate.setAccessible(true);
        DutchNationsApi.Event e = new DutchNationsApi.Event();
        e.id="teams"; e.type="CLAN_EVENT"; e.title="Test"; e.host="Host"; e.world="366";
        e.bossList="Moons"; e.codeword="TEST"; e.teamNames="Rood;Blauw";
        e.startsAt=OffsetDateTime.now().plusHours(1).toString(); e.endsAt=OffsetDateTime.now().plusHours(2).toString();
        e.dropListUrl="https://docs.google.com/spreadsheets/d/12345678901234567890/edit?gid=1";
        assertNull(validate.invoke(null, e));
        java.nio.file.Path file=Files.createTempDirectory("event-teams-test").resolve("state.json");
        DutchNationsApi.Store store = new DutchNationsApi.Store(file); store.addEvent(e);
        assertEquals("Rood;Blauw", new DutchNationsApi.Store(file).feed().events.get(0).teamNames);
        for (String type : new String[]{"BOSS", "MASS", "LEARNER", "CLAN_VS_CLAN"})
        { e.type=type; assertEquals("Teams zijn alleen beschikbaar bij clan-events", validate.invoke(null,e)); }
        e.type="CLAN_EVENT"; e.dropListUrl="";
        assertEquals("Teams vereisen een Google Sheet als screenshot-droplijst", validate.invoke(null,e));
    }

    @Test public void oldClientEditsPreserveTeamConfigurationAndExplicitEmptyDisablesIt() throws Exception
    {
        DutchNationsApi.Store store = new DutchNationsApi.Store(Files.createTempDirectory("teams-http-test").resolve("state.json"));
        java.lang.reflect.Constructor<DutchNationsApi> constructor = DutchNationsApi.class.getDeclaredConstructor(DutchNationsApi.Store.class, String.class);
        constructor.setAccessible(true);
        DutchNationsApi api = constructor.newInstance(store, "teams-test-owner");
        Method route = DutchNationsApi.class.getDeclaredMethod("events", com.sun.net.httpserver.HttpExchange.class);
        route.setAccessible(true);
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/events", exchange -> {
            try { route.invoke(api, exchange); } catch (Exception ex) { throw new java.io.IOException(ex); }
        });
        server.start();
        try
        {
            com.google.gson.JsonObject body = new com.google.gson.JsonObject();
            body.addProperty("type", "CLAN_EVENT"); body.addProperty("title", "Local teams test");
            body.addProperty("host", "Host"); body.addProperty("world", "366"); body.addProperty("bossList", "Moons");
            body.addProperty("startsAt", OffsetDateTime.now().plusHours(1).toString());
            body.addProperty("endsAt", OffsetDateTime.now().plusHours(2).toString()); body.addProperty("codeword", "TEST");
            body.addProperty("teamNames", "Rood;Blauw");
            body.addProperty("dropListUrl", "https://docs.google.com/spreadsheets/d/12345678901234567890/edit?gid=1");
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/events";
            java.net.http.HttpResponse<String> created = request(base, "POST", body.toString());
            assertEquals(created.body(), 201, created.statusCode());
            String id = com.google.gson.JsonParser.parseString(created.body()).getAsJsonObject().get("id").getAsString();
            body.remove("teamNames"); body.addProperty("title", "Old client edit");
            java.net.http.HttpResponse<String> edited = request(base + "/" + id, "PUT", body.toString());
            assertEquals(edited.body(), 200, edited.statusCode());
            assertEquals("Rood;Blauw", store.event(id).teamNames);
            body.addProperty("teamNames", "");
            assertEquals(200, request(base + "/" + id, "PUT", body.toString()).statusCode());
            assertEquals("", store.event(id).teamNames);
            body.addProperty("teamNames", "Rood;Blauw"); body.addProperty("type", "BOSS");
            assertEquals(400, request(base, "POST", body.toString()).statusCode());
        }
        finally { server.stop(0); }
    }

    private static java.net.http.HttpResponse<String> request(String url, String method, String body) throws Exception
    {
        return java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
            .header("Authorization", "Bearer teams-test-owner").header("Content-Type", "application/json")
            .method(method, java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }
}

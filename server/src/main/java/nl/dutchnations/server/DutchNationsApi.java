package nl.dutchnations.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class DutchNationsApi
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String OWNER_RSN = "heavenskill";
    private final Store store;
    private final String bootstrapOwnerHash;

    private DutchNationsApi(Store store, String ownerToken)
    {
        this.store = store;
        this.bootstrapOwnerHash = sha256(ownerToken);
    }

    public static void main(String[] args) throws Exception
    {
        String bind = env("DN_BIND", "127.0.0.1");
        int port = Integer.parseInt(env("DN_PORT", "8787"));
        String ownerToken = System.getenv("DN_OWNER_TOKEN");
        if (blank(ownerToken))
        {
            if (!"127.0.0.1".equals(bind) && !"localhost".equalsIgnoreCase(bind))
                throw new IllegalStateException("DN_OWNER_TOKEN is verplicht wanneer de API extern luistert");
            ownerToken = "dutch-nations-local-owner";
            System.out.println("LET OP: lokale ontwikkeltoken actief: " + ownerToken);
        }
        Path dataFile = Paths.get(env("DN_DATA_FILE", "server-data/state.json")).toAbsolutePath();
        DutchNationsApi app = new DutchNationsApi(new Store(dataFile, System.getenv("DATABASE_URL")), ownerToken);
        app.start(bind, port);
    }

    private void start(String bind, int port) throws IOException
    {
        HttpServer server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        server.createContext("/health", exchange -> send(exchange, 200, map("status", "ok")));
        server.createContext("/feed.json", this::feed);
        server.createContext("/api/events", this::events);
        server.createContext("/api/roles", this::roles);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor();
        cleanup.scheduleAtFixedRate(store::removeExpired, 0, 1, TimeUnit.MINUTES);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { cleanup.shutdown(); server.stop(1); }));
        System.out.println("Dutch Nations API actief op http://" + bind + ":" + port);
        System.out.println("Feed: http://" + bind + ":" + port + "/feed.json");
    }

    private void feed(HttpExchange exchange) throws IOException
    {
        if (!"GET".equals(exchange.getRequestMethod())) { methodNotAllowed(exchange); return; }
        send(exchange, 200, store.feed());
    }

    private void events(HttpExchange exchange) throws IOException
    {
        Actor actor = authenticate(exchange);
        if (actor == null || !(actor.owner() || actor.manager())) { sendError(exchange, 403, "Geen eventrechten"); return; }
        if ("POST".equals(exchange.getRequestMethod()))
        {
            Event event = read(exchange, Event.class);
            String error = validateEvent(event);
            if (error != null) { sendError(exchange, 400, error); return; }
            event.id = UUID.randomUUID().toString();
            event.type = event.type.toUpperCase(Locale.ROOT);
            if ("LEARNER".equals(event.type) || "MASS".equals(event.type)) event.codeword = "";
            if ("BOSS".equals(event.type)) event.world = "";
            store.addEvent(event);
            send(exchange, 201, event);
            return;
        }
        if ("DELETE".equals(exchange.getRequestMethod()))
        {
            String prefix = "/api/events/";
            String path = exchange.getRequestURI().getPath();
            if (!path.startsWith(prefix) || path.length() <= prefix.length()) { sendError(exchange, 400, "Event-id ontbreekt"); return; }
            boolean removed = store.deleteEvent(path.substring(prefix.length()));
            if (!removed) { sendError(exchange, 404, "Event niet gevonden"); return; }
            send(exchange, 200, map("deleted", true));
            return;
        }
        methodNotAllowed(exchange);
    }

    private void roles(HttpExchange exchange) throws IOException
    {
        Actor actor = authenticate(exchange);
        if (actor == null || !actor.owner()) { sendError(exchange, 403, "Alleen owners mogen rollen beheren"); return; }
        if (!"POST".equals(exchange.getRequestMethod())) { methodNotAllowed(exchange); return; }
        RoleChange change = read(exchange, RoleChange.class);
        if (change == null || !valid(change.rsn, 12) || blank(change.role)) { sendError(exchange, 400, "Geldige RSN en rol zijn verplicht"); return; }
        String role = change.role.toUpperCase(Locale.ROOT);
        if (OWNER_RSN.equals(normalize(change.rsn)) && "REMOVE".equals(role))
        { sendError(exchange, 400, "De eerste owner kan niet worden verwijderd"); return; }
        if ("REMOVE".equals(role))
        {
            store.removeRole(change.rsn);
            send(exchange, 200, map("removed", true));
            return;
        }
        if (!("OWNER".equals(role) || "MANAGER".equals(role) || "EVENT_HOST".equals(role)))
        { sendError(exchange, 400, "Ongeldige rol"); return; }
        String newToken = randomToken();
        store.saveRole(change.rsn, role, sha256(newToken));
        Map<String, Object> response = new HashMap<>();
        response.put("rsn", change.rsn.trim()); response.put("role", role); response.put("managementToken", newToken);
        send(exchange, 200, response);
    }

    private Actor authenticate(HttpExchange exchange)
    {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        String hash = sha256(header.substring(7).trim());
        if (MessageDigest.isEqual(hash.getBytes(StandardCharsets.UTF_8), bootstrapOwnerHash.getBytes(StandardCharsets.UTF_8)))
            return new Actor(OWNER_RSN, "OWNER");
        return store.actorForHash(hash);
    }

    private static String validateEvent(Event event)
    {
        if (event == null || blank(event.type) || blank(event.title) || blank(event.startsAt) || blank(event.endsAt))
            return "Type, titel, start en einde zijn verplicht";
        String type = event.type.toUpperCase(Locale.ROOT);
        if (!("LEARNER".equals(type) || "BOSS".equals(type) || "MASS".equals(type))) return "Ongeldig eventtype";
        try
        {
            OffsetDateTime start = OffsetDateTime.parse(event.startsAt);
            OffsetDateTime end = OffsetDateTime.parse(event.endsAt);
            if (!end.isAfter(start)) return "Eindtijd moet na starttijd liggen";
        }
        catch (RuntimeException ex) { return "Ongeldige ISO 8601-datum"; }
        if (!valid(event.title, 80) || !validOptional(event.host, 20) || !validOptional(event.description, 240)) return "Eventtekst is te lang of bevat ongeldige tekens";
        if (("LEARNER".equals(type) || "MASS".equals(type)) && (blank(event.world) || !event.world.matches("\\d{3,4}"))) return "Geldig wereldnummer is verplicht";
        if ("BOSS".equals(type) && (!valid(event.codeword, 40))) return "Boss-event vereist een geldig codewoord van maximaal 40 tekens";
        return null;
    }

    private static <T> T read(HttpExchange exchange, Class<T> type) throws IOException
    {
        byte[] body = exchange.getRequestBody().readNBytes(16_385);
        if (body.length > 16_384) return null;
        try { return GSON.fromJson(new String(body, StandardCharsets.UTF_8), type); }
        catch (RuntimeException ex) { return null; }
    }

    private static void send(HttpExchange exchange, int status, Object value) throws IOException
    {
        byte[] body = GSON.toJson(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(body); }
    }

    private static void sendError(HttpExchange exchange, int status, String message) throws IOException
    { send(exchange, status, map("error", message)); }
    private static void methodNotAllowed(HttpExchange exchange) throws IOException
    { sendError(exchange, 405, "Methode niet toegestaan"); }
    private static Map<String, Object> map(String key, Object value)
    { Map<String, Object> map = new HashMap<>(); map.put(key, value); return map; }
    private static String env(String key, String fallback)
    { String value = System.getenv(key); return blank(value) ? fallback : value; }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static boolean valid(String value, int maximum) { return !blank(value) && validOptional(value, maximum); }
    private static boolean validOptional(String value, int maximum)
    {
        if (value == null) return true;
        if (value.length() > maximum || value.indexOf('<' ) >= 0 || value.indexOf('>' ) >= 0) return false;
        return value.chars().noneMatch(character -> Character.isISOControl(character));
    }
    private static String normalize(String value) { return value == null ? "" : value.replace('\u00a0', ' ').trim().toLowerCase(Locale.ROOT); }

    private static String randomToken()
    {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(); for (byte b : digest) hex.append(String.format("%02x", b)); return hex.toString();
        }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    static final class Store
    {
        private final Path file;
        private final String databaseUrl;
        private State state;

        Store(Path file) { this(file, null); }
        Store(Path file, String databaseUrl)
        {
            this.file = file;
            this.databaseUrl = blank(databaseUrl) ? null : databaseUrl.trim();
            this.state = load();
            ensureOwner();
            if (this.databaseUrl != null) save();
        }

        synchronized Feed feed()
        {
            removeExpired();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            Feed feed = new Feed();
            feed.updatedAt = state.updatedAt;
            feed.events = new ArrayList<>();
            for (Event stored : state.events)
            {
                Event visible = GSON.fromJson(GSON.toJson(stored), Event.class);
                boolean active;
                try { active = !now.isBefore(OffsetDateTime.parse(stored.startsAt)) && now.isBefore(OffsetDateTime.parse(stored.endsAt)); }
                catch (RuntimeException ex) { active = false; }
                if (!active) visible.codeword = "";
                feed.events.add(visible);
            }
            return feed;
        }

        synchronized void addEvent(Event event) { state.events.add(event); changed(); }
        synchronized boolean deleteEvent(String id)
        {
            boolean removed = state.events.removeIf(event -> id.equals(event.id));
            if (removed) changed();
            return removed;
        }
        synchronized void saveRole(String rsn, String role, String tokenHash)
        {
            String key = normalize(rsn);
            Member member = state.members.stream().filter(m -> key.equals(normalize(m.rsn))).findFirst().orElse(null);
            if (member == null) { member = new Member(); member.rsn = rsn.trim(); state.members.add(member); }
            member.role = role;
            state.tokenHashes.put(key, tokenHash);
            changed();
        }
        synchronized void removeRole(String rsn)
        {
            String key = normalize(rsn);
            state.members.removeIf(m -> key.equals(normalize(m.rsn)));
            state.tokenHashes.remove(key);
            ensureOwner();
            changed();
        }
        synchronized Actor actorForHash(String hash)
        {
            for (Member member : state.members)
            {
                String stored = state.tokenHashes.get(normalize(member.rsn));
                if (stored != null && MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8), hash.getBytes(StandardCharsets.UTF_8)))
                    return new Actor(member.rsn, member.role);
            }
            return null;
        }
        synchronized void removeExpired()
        {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            boolean removed = state.events.removeIf(event ->
            {
                try { return !OffsetDateTime.parse(event.endsAt).isAfter(now); }
                catch (RuntimeException ex) { return true; }
            });
            if (removed) changed();
        }

        private State load()
        {
            if (databaseUrl != null) return loadDatabase();
            if (!Files.exists(file)) return new State();
            try
            {
                State loaded = GSON.fromJson(new String(Files.readAllBytes(file), StandardCharsets.UTF_8), State.class);
                return loaded == null ? new State() : loaded;
            }
            catch (Exception ex) { throw new IllegalStateException("Kan API-data niet lezen: " + file, ex); }
        }

        private State loadDatabase()
        {
            try (Connection connection = connection(); Statement statement = connection.createStatement())
            {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS dutch_nations_state (id INTEGER PRIMARY KEY, payload TEXT NOT NULL)");
                try (ResultSet result = statement.executeQuery("SELECT payload FROM dutch_nations_state WHERE id = 1"))
                {
                    if (!result.next()) return new State();
                    State loaded = GSON.fromJson(result.getString(1), State.class);
                    return loaded == null ? new State() : loaded;
                }
            }
            catch (Exception ex) { throw new IllegalStateException("Kan PostgreSQL-data niet lezen", ex); }
        }

        private void ensureOwner()
        {
            if (state.members == null) state.members = new ArrayList<>();
            if (state.events == null) state.events = new ArrayList<>();
            if (state.tokenHashes == null) state.tokenHashes = new HashMap<>();
            if (state.members.stream().noneMatch(m -> OWNER_RSN.equals(normalize(m.rsn))))
            { Member owner = new Member(); owner.rsn = OWNER_RSN; owner.role = "OWNER"; state.members.add(owner); }
            if (blank(state.updatedAt)) state.updatedAt = OffsetDateTime.now(ZoneOffset.UTC).toString();
        }

        private void changed()
        {
            state.updatedAt = OffsetDateTime.now(ZoneOffset.UTC).toString();
            save();
        }

        private void save()
        {
            if (databaseUrl != null) { saveDatabase(); return; }
            try
            {
                Files.createDirectories(file.getParent());
                Path temp = file.resolveSibling(file.getFileName() + ".tmp");
                Files.write(temp, (GSON.toJson(state) + "\n").getBytes(StandardCharsets.UTF_8));
                try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (IOException ex) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
            }
            catch (IOException ex) { throw new IllegalStateException("Kan API-data niet opslaan", ex); }
        }

        private void saveDatabase()
        {
            String sql = "INSERT INTO dutch_nations_state (id, payload) VALUES (1, ?) " +
                "ON CONFLICT (id) DO UPDATE SET payload = EXCLUDED.payload";
            try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql))
            {
                statement.setString(1, GSON.toJson(state));
                statement.executeUpdate();
            }
            catch (SQLException ex) { throw new IllegalStateException("Kan PostgreSQL-data niet opslaan", ex); }
        }

        private Connection connection() throws SQLException
        {
            if (databaseUrl.startsWith("jdbc:postgresql:")) return DriverManager.getConnection(databaseUrl);
            try
            {
                URI uri = URI.create(databaseUrl);
                String[] credentials = uri.getRawUserInfo() == null ? new String[0] : uri.getRawUserInfo().split(":", 2);
                if (credentials.length != 2) throw new IllegalArgumentException("Databasegebruiker of wachtwoord ontbreekt");
                Properties properties = new Properties();
                properties.setProperty("user", URLDecoder.decode(credentials[0], StandardCharsets.UTF_8.name()));
                properties.setProperty("password", URLDecoder.decode(credentials[1], StandardCharsets.UTF_8.name()));
                properties.setProperty("sslmode", "require");
                int port = uri.getPort() < 0 ? 5432 : uri.getPort();
                String jdbc = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath();
                return DriverManager.getConnection(jdbc, properties);
            }
            catch (SQLException ex) { throw ex; }
            catch (Exception ex) { throw new SQLException("Ongeldige DATABASE_URL", ex); }
        }
    }
    static final class State
    {
        String updatedAt;
        List<Member> members = new ArrayList<>();
        List<Event> events = new ArrayList<>();
        Map<String, String> tokenHashes = new HashMap<>();
    }
    static final class Feed { String updatedAt; List<Event> events; }
    static final class Member { String rsn; String role; }
    static final class Event
    {
        String id; String startsAt; String endsAt; String type; String title;
        String world; String host; String description; String codeword;
    }
    static final class RoleChange { String rsn; String role; }
    static final class Actor
    {
        final String rsn; final String role;
        Actor(String rsn, String role) { this.rsn = rsn; this.role = role; }
        boolean owner() { return "OWNER".equalsIgnoreCase(role); }
        boolean manager() { return "MANAGER".equalsIgnoreCase(role); }
    }
}

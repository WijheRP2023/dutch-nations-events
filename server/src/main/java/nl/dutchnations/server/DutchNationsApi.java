package nl.dutchnations.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class DutchNationsApi
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String OWNER_RSN = "heavenskill";
    private final Store store;
    private final String bootstrapOwnerHash;
    private JDA discordBot;
    private final DiscordWebhookPublisher discordWebhook;
    private final ExecutorService discordWebhookExecutor = Executors.newSingleThreadExecutor();

    private DutchNationsApi(Store store, String ownerToken, String discordWebhookUrl)
    {
        this.store = store;
        this.bootstrapOwnerHash = sha256(ownerToken);
        this.discordWebhook = new DiscordWebhookPublisher(discordWebhookUrl, store);
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
        DutchNationsApi app = new DutchNationsApi(new Store(dataFile, System.getenv("DATABASE_URL")), ownerToken, System.getenv("DISCORD_WEBHOOK_URL"));
        app.start(bind, port, System.getenv("DISCORD_BOT_TOKEN"));
    }

    private void start(String bind, int port, String discordBotToken) throws IOException
    {
        HttpServer server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        server.createContext("/", this::root);
        server.createContext("/health", exchange -> send(exchange, 200, map("status", "ok")));
        server.createContext("/feed.json", this::feed);
        server.createContext("/api/events", this::events);
        server.createContext("/api/roles", this::roles);
        server.createContext("/api/announcement-channel", this::announcementChannel);
        server.createContext("/api/owner-logs", this::ownerLogs);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        startDiscordBot(discordBotToken);

        ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor();
        cleanup.scheduleAtFixedRate(store::removeExpired, 0, 1, TimeUnit.MINUTES);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { cleanup.shutdown(); discordWebhookExecutor.shutdownNow(); if (discordBot != null) discordBot.shutdown(); server.stop(1); }));
        System.out.println("Dutch Nations API actief op http://" + bind + ":" + port);
        System.out.println("Feed: http://" + bind + ":" + port + "/feed.json");
    }

    private void startDiscordBot(String token)
    {
        if (blank(token))
        {
            System.out.println("Discord-bot niet gestart: DISCORD_BOT_TOKEN ontbreekt");
            return;
        }

        discordBot = JDABuilder.createDefault(token)
            .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
            .addEventListeners(new ListenerAdapter()
            {
                @Override
                public void onMessageReceived(MessageReceivedEvent event)
                {
                    if (!event.isFromGuild() || event.getAuthor().isBot()) return;
                    store.recordAnnouncement(event.getChannel().getId(), event.getMessageId());
                }
            })
            .build();
        System.out.println("Discord-bot wordt verbonden");
    }
    private void root(HttpExchange exchange) throws IOException
    {
        if (!"/".equals(exchange.getRequestURI().getPath())) { sendError(exchange, 404, "Route niet gevonden"); return; }
        if (!"GET".equals(exchange.getRequestMethod())) { methodNotAllowed(exchange); return; }
        Map<String, Object> status = new HashMap<>();
        status.put("service", "Dutch Nations Events API");
        status.put("status", "ok");
        status.put("health", "/health");
        status.put("feed", "/feed.json");
        send(exchange, 200, status);
    }

    private void feed(HttpExchange exchange) throws IOException
    {
        if (!"GET".equals(exchange.getRequestMethod())) { methodNotAllowed(exchange); return; }
        send(exchange, 200, store.feed());
    }

    private void events(HttpExchange exchange) throws IOException
    {
        Actor actor = authenticate(exchange);
        if (actor == null || !actor.canManageEvents()) { sendError(exchange, 403, "Geen eventrechten"); return; }
        if ("GET".equals(exchange.getRequestMethod()))
        {
            if (!actor.owner()) { sendError(exchange, 403, "Alleen de owner kan een codewoord bekijken"); return; }
            String prefix = "/api/events/";
            String suffix = "/codeword";
            String path = exchange.getRequestURI().getPath();
            if (!path.startsWith(prefix) || !path.endsWith(suffix) || path.length() <= prefix.length() + suffix.length())
            { sendError(exchange, 400, "Event-id ontbreekt"); return; }
            Event existing = store.event(path.substring(prefix.length(), path.length() - suffix.length()));
            if (existing == null) { sendError(exchange, 404, "Event niet gevonden"); return; }
            Map<String, Object> response = new HashMap<>();
            response.put("id", existing.id);
            response.put("codeword", existing.codeword);
            send(exchange, 200, response);
            return;
        }
        if ("POST".equals(exchange.getRequestMethod()))
        {
            Event event = read(exchange, Event.class);
            String error = validateEvent(event);
            if (error != null) { System.out.println("Event create afgewezen: " + error); store.error("Event", actor.rsn + ": " + error); sendError(exchange, 400, error); return; }
            event.id = UUID.randomUUID().toString();
            event.type = event.type.toUpperCase(Locale.ROOT);
            if (!actor.canManageEventType(event.type)) { sendError(exchange, 403, "Deze rol mag alleen learner-events beheren"); return; }
            if ("BOSS".equals(event.type) || "CLAN_EVENT".equals(event.type) || "CLAN_VS_CLAN".equals(event.type)) event.codewordRequired = true;
            if ("LEARNER".equals(event.type)) { event.codeword = ""; event.codewordRequired = false; }
            if ("MASS".equals(event.type) && !event.codewordRequired) event.codeword = "";
            if ("BOSS".equals(event.type) || "CLAN_EVENT".equals(event.type) || "CLAN_VS_CLAN".equals(event.type)) { event.checklist = ""; event.requiredPlugins = ""; event.strategyWikiUrl = ""; } else { event.driveUrl = ""; event.registrationUrl = ""; event.registrationEndsAt = ""; }
            if (!"CLAN_VS_CLAN".equals(event.type)) { event.clansOne = ""; event.clansTwo = ""; event.activity = ""; }
            if (!"CLAN_EVENT".equals(event.type)) event.bossList = "";
            if ("BOSS".equals(event.type) || "CLAN_VS_CLAN".equals(event.type)) event.world = "";
            Event conflict = store.findConflict(event);
            if (conflict != null && !event.allowConflict)
            {
                sendError(exchange, 409, "Event overlapt met " + conflict.title); return;
            }
            event.discordDescription = event.discordText;
            event.discordText = "";
            store.addEvent(event);
            store.audit(actor, "Event gemaakt: " + event.title);
            discordWebhookExecutor.execute(() ->
            {
                String messageId = discordWebhook.publish(event);
                if (!blank(messageId)) store.setDiscordMessageId(event.id, messageId);
            });
            send(exchange, 201, event);
            return;
        }
        if ("PUT".equals(exchange.getRequestMethod()))
        {
            String prefix = "/api/events/";
            String path = exchange.getRequestURI().getPath();
            if (!path.startsWith(prefix) || path.length() <= prefix.length()) { sendError(exchange, 400, "Event-id ontbreekt"); return; }
            Event existing = store.event(path.substring(prefix.length()));
            if (existing == null) { sendError(exchange, 404, "Event niet gevonden"); return; }
            if (!actor.canManageEventType(existing.type) || !actor.canEdit(existing)) { sendError(exchange, 403, "Geen rechten om dit event aan te passen"); return; }
            Event event = read(exchange, Event.class);
            if (event == null) { sendError(exchange, 400, "Ongeldige eventgegevens"); return; }
            event.id = existing.id;
            event.discordDescription = existing.discordDescription;
            event.discordMessageId = existing.discordMessageId;
            event.discordText = "";
            event.type = event.type == null ? "" : event.type.toUpperCase(Locale.ROOT);
            if (!actor.canManageEventType(event.type)) { sendError(exchange, 403, "Deze rol mag alleen learner-events beheren"); return; }
            if (("BOSS".equals(event.type) || "CLAN_EVENT".equals(event.type) || "CLAN_VS_CLAN".equals(event.type) || ("MASS".equals(event.type) && event.codewordRequired)) && blank(event.codeword)) event.codeword = existing.codeword;
            boolean codewordChanged = !String.valueOf(existing.codeword).equals(String.valueOf(event.codeword));
            String error = validateEvent(event);
            if (error != null) { System.out.println("Event create afgewezen: " + error); store.error("Event", actor.rsn + ": " + error); sendError(exchange, 400, error); return; }
            if ("BOSS".equals(event.type) || "CLAN_EVENT".equals(event.type) || "CLAN_VS_CLAN".equals(event.type)) event.codewordRequired = true;
            if ("LEARNER".equals(event.type)) { event.codeword = ""; event.codewordRequired = false; }
            if ("MASS".equals(event.type) && !event.codewordRequired) event.codeword = "";
            if ("BOSS".equals(event.type) || "CLAN_EVENT".equals(event.type) || "CLAN_VS_CLAN".equals(event.type)) { event.checklist = ""; event.requiredPlugins = ""; event.strategyWikiUrl = ""; } else { event.driveUrl = ""; event.registrationUrl = ""; event.registrationEndsAt = ""; }
            if ("BOSS".equals(event.type)) event.world = "";
            if ("CLAN_VS_CLAN".equals(event.type)) event.world = "";
            if (!"CLAN_VS_CLAN".equals(event.type)) { event.clansOne = ""; event.clansTwo = ""; event.activity = ""; }
            if (!"CLAN_EVENT".equals(event.type)) event.bossList = "";
            Event conflict = store.findConflict(event, existing.id);
            if (conflict != null && !event.allowConflict) { sendError(exchange, 409, "Event overlapt met " + conflict.title); return; }
            store.updateEvent(existing.id, event);
            store.audit(actor, "Event aangepast: " + event.title);
            if (codewordChanged) store.audit(actor, "Codewoord gewijzigd voor event: " + event.title);
            if (!blank(event.discordMessageId)) discordWebhookExecutor.execute(() -> discordWebhook.update(event));
            send(exchange, 200, event);
            return;
        }
        if ("DELETE".equals(exchange.getRequestMethod()))
        {
            String prefix = "/api/events/";
            String path = exchange.getRequestURI().getPath();
            if (!path.startsWith(prefix) || path.length() <= prefix.length()) { sendError(exchange, 400, "Event-id ontbreekt"); return; }
            Event existing = store.event(path.substring(prefix.length()));
            if (existing == null) { sendError(exchange, 404, "Event niet gevonden"); return; }
            if (!actor.canManageEventType(existing.type)) { sendError(exchange, 403, "Deze rol mag alleen learner-events beheren"); return; }
            boolean removed = store.deleteEvent(existing.id);
            if (removed) store.audit(actor, "Event verwijderd: " + existing.title);
            if (!removed) { sendError(exchange, 404, "Event niet gevonden"); return; }
            send(exchange, 200, map("deleted", true));
            return;
        }
        methodNotAllowed(exchange);
    }

    private void ownerLogs(HttpExchange exchange) throws IOException
    {
        Actor actor = authenticate(exchange);
        if (actor == null || !actor.owner()) { sendError(exchange, 403, "Alleen de owner kan de logs bekijken"); return; }
        if (!"GET".equals(exchange.getRequestMethod())) { methodNotAllowed(exchange); return; }
        send(exchange, 200, store.ownerLogs());
    }
    private void announcementChannel(HttpExchange exchange) throws IOException
    {
        Actor actor = authenticate(exchange);
        if (actor == null || !actor.owner()) { sendError(exchange, 403, "Alleen de owner kan het mededelingenkanaal wijzigen"); return; }
        if (!"POST".equals(exchange.getRequestMethod())) { methodNotAllowed(exchange); return; }
        AnnouncementChannel change = read(exchange, AnnouncementChannel.class);
        if (change == null || !validDiscordChannelUrl(change.url))
        { sendError(exchange, 400, "Geldige Discord-kanaallink is verplicht"); return; }
        store.saveAnnouncementsUrl(change.url.trim());
        store.audit(actor, "Mededelingenkanaal gewijzigd");
        send(exchange, 200, map("announcementsUrl", change.url.trim()));
    }
    private void roles(HttpExchange exchange) throws IOException
    {
        Actor actor = authenticate(exchange);
        if (actor == null) { sendError(exchange, 401, "Ongeldige management-token"); return; }
        if ("GET".equals(exchange.getRequestMethod()))
        {
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null && query.contains("all=true"))
            {
                if (!(actor.owner() || actor.administrator())) { sendError(exchange, 403, "Geen rechten om het rollenoverzicht te bekijken"); return; }
                send(exchange, 200, map("roles", store.roles())); return;
            }
            Map<String, Object> response = new HashMap<>();
            response.put("rsn", actor.rsn); response.put("role", actor.role);
            send(exchange, 200, response);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) { methodNotAllowed(exchange); return; }
        if (!(actor.owner() || actor.administrator())) { sendError(exchange, 403, "Geen rechten om rollen te beheren"); return; }
        RoleChange change = read(exchange, RoleChange.class);
        if (change == null || !valid(change.rsn, 12) || blank(change.role)) { sendError(exchange, 400, "Geldige RSN en rol zijn verplicht"); return; }
        String role = change.role.toUpperCase(Locale.ROOT);
        if (OWNER_RSN.equals(normalize(change.rsn)))
        { sendError(exchange, 400, "De vaste owner heavenskill kan niet worden gewijzigd"); return; }
        if (actor.administrator())
        {
            Member target = store.member(change.rsn);
            if ("REMOVE".equals(role) || "ROTATE".equals(role))
            {
                if (target == null) { sendError(exchange, 404, "Rol niet gevonden"); return; }
                if (!("MANAGER".equalsIgnoreCase(target.role) || "LEARNER_HOST".equalsIgnoreCase(target.role) || "TEACHER".equalsIgnoreCase(target.role)))
                { sendError(exchange, 403, "Administrators mogen alleen manager- en teacherrollen beheren"); return; }
            }
            else
            {
                if (!("MANAGER".equals(role) || "TEACHER".equals(role)))
                { sendError(exchange, 403, "Administrators mogen alleen managers en teachers toekennen"); return; }
                if (target != null && !("MANAGER".equalsIgnoreCase(target.role) ||
                    "LEARNER_HOST".equalsIgnoreCase(target.role) || "TEACHER".equalsIgnoreCase(target.role)))
                { sendError(exchange, 403, "Administrators mogen owner- en administratorrollen niet wijzigen"); return; }
            }
        }        if ("ROTATE".equals(role))
        {
            if (!(actor.owner() || actor.administrator())) { sendError(exchange, 403, "Geen rechten om tokens te vernieuwen"); return; }
            Member existing = store.member(change.rsn);
            if (existing == null) { sendError(exchange, 404, "Rol niet gevonden"); return; }
            String newToken = randomToken();
            store.saveRole(existing.rsn, existing.role, sha256(newToken));
            store.audit(actor, "Management-token vernieuwd voor " + existing.rsn);
            Map<String, Object> response = new HashMap<>();
            response.put("rsn", existing.rsn); response.put("role", existing.role); response.put("managementToken", newToken);
            send(exchange, 200, response); return;
        }
        if ("REMOVE".equals(role))
        {
            store.removeRole(change.rsn);
            store.audit(actor, "Managementrol ingetrokken van " + change.rsn.trim());
            send(exchange, 200, map("removed", true));
            return;
        }
        if (!("ADMINISTRATOR".equals(role) || "MANAGER".equals(role) || "TEACHER".equals(role) || "LEARNER_HOST".equals(role)))
        { sendError(exchange, 400, "Ongeldige rol"); return; }
        String newToken = randomToken();
        store.saveRole(change.rsn, role, sha256(newToken));
        store.audit(actor, "Rol " + role + " toegekend aan " + change.rsn.trim());
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
        if (!("LEARNER".equals(type) || "BOSS".equals(type) || "MASS".equals(type) || "CLAN_EVENT".equals(type) || "CLAN_VS_CLAN".equals(type))) return "Ongeldig eventtype";
        try
        {
            OffsetDateTime start = OffsetDateTime.parse(event.startsAt);
            OffsetDateTime end = OffsetDateTime.parse(event.endsAt);
            if (!end.isAfter(start)) return "Eindtijd moet na starttijd liggen";
            if (!blank(event.registrationUrl))
            {
                if (blank(event.registrationEndsAt)) return "Een Discord-aanmeldlink vereist een aanmelddeadline";
                OffsetDateTime registrationEnd = OffsetDateTime.parse(event.registrationEndsAt);
                if (registrationEnd.isAfter(start)) return "De aanmelddeadline mag niet na de starttijd liggen";
            }
            else if (!blank(event.registrationEndsAt)) return "Een aanmelddeadline vereist een Discord-aanmeldlink";
        }
        catch (RuntimeException ex) { return "Ongeldige ISO 8601-datum"; }
        if (!valid(event.title, 80)) return "Titel is ongeldig of langer dan 80 tekens";
        if (!valid(event.host, 20)) return "Host-RSN is ongeldig of langer dan 20 tekens";
        if (!validOptional(event.description, 3000)) return "Beschrijving is ongeldig of langer dan 3000 tekens";
        if (!validOptional(event.checklist, 400)) return "Voorbereiding is ongeldig";
        if (!validOptional(event.requiredPlugins, 300)) return "Plug-inlijst is ongeldig";
        if (!validWikiUrl(event.strategyWikiUrl)) return "Strategie-link is ongeldig";
        if (!validDriveUrl(event.driveUrl)) return "Tussenstand-link is ongeldig";
        if (!validDiscordUrl(event.registrationUrl)) return "Discord-aanmeldlink is ongeldig";
        if (!validYoutubeUrl(event.youtubeUrl)) return "YouTube-link is ongeldig";
        if (!validOptional(event.clansOne, 300) || !validOptional(event.clansTwo, 300)) return "Clanlijst is ongeldig";
        if (!validOptional(event.activity, 120)) return "Activiteit is ongeldig";
        if (!validOptional(event.bossList, 300)) return "Boss- of activiteitenlijst is ongeldig";
        if (!validOptional(event.discordText, 3000)) return "Discord-beschrijving is ongeldig of langer dan 3000 tekens";
        int descriptionLength = event.description == null ? 0 : event.description.length();
        int discordDescriptionLength = event.discordText == null ? 0 : event.discordText.length();
        if (descriptionLength + discordDescriptionLength > 4000) return "Beschrijving en Discord-beschrijving samen mogen maximaal 4000 tekens bevatten";
        if (("LEARNER".equals(type) || "MASS".equals(type) || "CLAN_EVENT".equals(type)) && (blank(event.world) || !event.world.matches("\\d{3,4}"))) return "Geldig wereldnummer is verplicht";
        if (("BOSS".equals(type) || "CLAN_EVENT".equals(type) || "CLAN_VS_CLAN".equals(type) || ("MASS".equals(type) && event.codewordRequired)) && !valid(event.codeword, 40)) return "Dit event vereist een geldig codewoord van maximaal 40 tekens";
        if ("CLAN_EVENT".equals(type) && !valid(event.bossList, 300)) return "Vul minimaal één boss of activiteit in";
        if ("CLAN_VS_CLAN".equals(type) && (!valid(event.clansOne, 300) || !valid(event.clansTwo, 300) || !valid(event.activity, 120))) return "Vul beide clanlijsten en de activiteit in";
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
        return value.chars().noneMatch(character -> Character.isISOControl(character) && character != '\n' && character != '\r' && character != '\t');
    }
    private static boolean validWikiUrl(String value)
    {
        if (blank(value)) return true;
        if (!validOptional(value, 300)) return false;
        try
        {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) &&
                "oldschool.runescape.wiki".equalsIgnoreCase(uri.getHost()) && uri.getUserInfo() == null;
        }
        catch (RuntimeException exception) { return false; }
    }
    private static boolean validDiscordWebhookUrl(String value)
    {
        if (blank(value)) return false;
        try
        {
            URI uri = URI.create(value);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String[] parts = uri.getPath() == null ? new String[0] : uri.getPath().split("/");
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null &&
                "discord.com".equals(host) && parts.length == 5 && "api".equals(parts[1]) &&
                "webhooks".equals(parts[2]) && !blank(parts[3]) && !blank(parts[4]);
        }
        catch (RuntimeException exception) { return false; }
    }
    private static boolean validDiscordChannelUrl(String value)
    {
        if (blank(value)) return false;
        try
        {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && "discord.com".equalsIgnoreCase(uri.getHost()) &&
                uri.getUserInfo() == null && uri.getPath() != null && uri.getPath().matches("/channels/[0-9]+/[0-9]+/?");
        }
        catch (RuntimeException exception) { return false; }
    }
    private static String discordChannelId(String value)
    {
        if (!validDiscordChannelUrl(value)) return "";
        try
        {
            String[] parts = URI.create(value).getPath().split("/");
            return parts.length == 4 ? parts[3] : "";
        }
        catch (RuntimeException exception) { return ""; }
    }
    private static boolean validDiscordUrl(String value)
    {
        if (blank(value)) return true;
        if (!validOptional(value, 500)) return false;
        try
        {
            URI uri = URI.create(value);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            boolean invite = "discord.gg".equals(host) && path.length() > 1;
            boolean discordPage = ("discord.com".equals(host) || "www.discord.com".equals(host)) &&
                (path.startsWith("/invite/") || path.startsWith("/channels/"));
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null && (invite || discordPage);
        }
        catch (RuntimeException exception) { return false; }
    }

    private static boolean validYoutubeUrl(String value)
    {
        if (blank(value)) return true;
        if (!validOptional(value, 500)) return false;
        try
        {
            URI uri = URI.create(value);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null &&
                ("youtube.com".equals(host) || "www.youtube.com".equals(host) || "m.youtube.com".equals(host) ||
                    "music.youtube.com".equals(host) || "youtu.be".equals(host));
        }
        catch (RuntimeException exception) { return false; }
    }
    private static boolean validDriveUrl(String value)
    {
        if (blank(value)) return true;
        if (!validOptional(value, 500)) return false;
        try
        {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null &&
                ("drive.google.com".equalsIgnoreCase(host) || "docs.google.com".equalsIgnoreCase(host));
        }
        catch (RuntimeException exception) { return false; }
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
            ensureLogLists();
            ensureOwner();
            if (this.databaseUrl != null) save();
        }

        synchronized Feed feed()
        {
            removeExpired();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            Feed feed = new Feed();
            feed.updatedAt = state.updatedAt;
            feed.announcementsUrl = state.announcementsUrl;
            feed.announcementsSequence = state.announcementsSequence;
            feed.events = new ArrayList<>();
            for (Event stored : state.events)
            {
                Event visible = GSON.fromJson(GSON.toJson(stored), Event.class);
                boolean active;
                try { active = !now.isBefore(OffsetDateTime.parse(stored.startsAt)) && now.isBefore(OffsetDateTime.parse(stored.endsAt)); }
                catch (RuntimeException ex) { active = false; }
                if (!active) visible.codeword = "";
                visible.discordDescription = "";
                visible.discordMessageId = "";
                feed.events.add(visible);
            }
            return feed;
        }

        synchronized Event findConflict(Event candidate) { return findConflict(candidate, null); }
        synchronized Event findConflict(Event candidate, String ignoredId)
        {
            OffsetDateTime start = OffsetDateTime.parse(candidate.startsAt);
            OffsetDateTime end = OffsetDateTime.parse(candidate.endsAt);
            return state.events.stream().filter(event ->
            {
                try { return !event.id.equals(ignoredId) && start.isBefore(OffsetDateTime.parse(event.endsAt)) && end.isAfter(OffsetDateTime.parse(event.startsAt)); }
                catch (RuntimeException ex) { return false; }
            }).findFirst().orElse(null);
        }
        synchronized void addEvent(Event event) { state.events.add(event); changed(); }
        synchronized boolean updateEvent(String id, Event replacement)
        {
            for (int i = 0; i < state.events.size(); i++)
            {
                if (id.equals(state.events.get(i).id)) { state.events.set(i, replacement); changed(); return true; }
            }
            return false;
        }
        synchronized void setDiscordMessageId(String id, String messageId)
        {
            Event event = event(id);
            if (event == null) return;
            event.discordMessageId = messageId;
            changed();
        }

        synchronized boolean deleteEvent(String id)
        {
            boolean removed = state.events.removeIf(event -> id.equals(event.id));
            if (removed) changed();
            return removed;
        }
        synchronized Event event(String id)
        {
            return state.events.stream().filter(value -> id.equals(value.id)).findFirst().orElse(null);
        }
        synchronized void saveAnnouncementsUrl(String value)
        {
            state.announcementsUrl = value;
            changed();
        }
        synchronized void recordAnnouncement(String channelId, String messageId)
        {
            if (blank(channelId) || blank(messageId) || !channelId.equals(discordChannelId(state.announcementsUrl))) return;
            if (messageId.equals(state.lastAnnouncementMessageId)) return;
            state.lastAnnouncementMessageId = messageId;
            state.announcementsSequence++;
            changed();
        }
        synchronized OwnerLogs ownerLogs()
        {
            OwnerLogs logs = new OwnerLogs();
            logs.management = copyLogs(state.managementLogs);
            logs.errors = copyLogs(state.errorLogs);
            return logs;
        }
        synchronized void audit(Actor actor, String message)
        {
            addLog(state.managementLogs, actor == null ? "Systeem" : actor.rsn, message);
            changed();
        }
        synchronized void error(String source, String message)
        {
            addLog(state.errorLogs, source, message);
            changed();
        }
        private static List<LogEntry> copyLogs(List<LogEntry> source)
        {
            List<LogEntry> copy = new ArrayList<>();
            if (source == null) return copy;
            for (LogEntry entry : source) copy.add(GSON.fromJson(GSON.toJson(entry), LogEntry.class));
            return copy;
        }
        private static void addLog(List<LogEntry> logs, String source, String message)
        {
            if (logs == null) return;
            LogEntry entry = new LogEntry();
            entry.at = OffsetDateTime.now(ZoneOffset.UTC).toString();
            entry.source = source;
            entry.message = message;
            logs.add(0, entry);
            while (logs.size() > 100) logs.remove(logs.size() - 1);
        }
        private void ensureLogLists()
        {
            if (state.managementLogs == null) state.managementLogs = new ArrayList<>();
            if (state.errorLogs == null) state.errorLogs = new ArrayList<>();
        }
        synchronized void saveRole(String rsn, String role, String tokenHash)
        {
            String key = normalize(rsn);
            Member member = state.members.stream().filter(m -> key.equals(normalize(m.rsn))).findFirst().orElse(null);
            if (member == null) { member = new Member(); member.rsn = rsn.trim(); state.members.add(member); }
            member.role = role; member.updatedAt = OffsetDateTime.now(ZoneOffset.UTC).toString();
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
        synchronized Member member(String rsn)
        {
            String key = normalize(rsn);
            return state.members.stream().filter(value -> key.equals(normalize(value.rsn))).findFirst().orElse(null);
        }
        synchronized List<Member> roles()
        {
            List<Member> roles = new ArrayList<>();
            for (Member member : state.members) roles.add(GSON.fromJson(GSON.toJson(member), Member.class));
            roles.sort((left, right) -> left.rsn.compareToIgnoreCase(right.rsn));
            return roles;
        }
        synchronized boolean hasAssignedRole(String rsn)
        {
            String key = normalize(rsn);
            return state.members.stream().anyMatch(member -> key.equals(normalize(member.rsn)));
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
            { Member owner = new Member(); owner.rsn = OWNER_RSN; owner.role = "OWNER"; owner.updatedAt = OffsetDateTime.now(ZoneOffset.UTC).toString(); state.members.add(owner); }
            if (blank(state.updatedAt)) state.updatedAt = OffsetDateTime.now(ZoneOffset.UTC).toString();
            for (Member member : state.members)
            {
                if ("EVENT_HOST".equalsIgnoreCase(member.role)) member.role = "MANAGER";
                if (blank(member.updatedAt)) member.updatedAt = state.updatedAt;
            }
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
    private static final class DiscordWebhookPublisher
    {
        private final URI url;
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        private final Store store;

        DiscordWebhookPublisher(String configuredUrl, Store store)
        {
            this.store = store;
            url = validDiscordWebhookUrl(configuredUrl) ? URI.create(configuredUrl.trim()) : null;
            if (!blank(configuredUrl) && url == null) System.err.println("Discord-webhook uitgeschakeld: ongeldige webhook-URL.");
        }

        String publish(Event event)
        {
            if (url == null) return "";
            try
            {
                HttpRequest request = request(URI.create(url.toString() + "?wait=true"), "POST", event);
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() < 200 || response.statusCode() >= 300)
                {
                    System.err.println("Discord-webhook kon event niet plaatsen (HTTP " + response.statusCode() + ").");
                    store.error("Discord-webhook", "Eventbericht afgewezen (HTTP " + response.statusCode() + ")");
                    return "";
                }
                Map<?, ?> message = GSON.fromJson(response.body(), Map.class);
                Object id = message == null ? null : message.get("id");
                return id instanceof String ? (String) id : "";
            }
            catch (Exception exception) { System.err.println("Discord-webhook kon event niet plaatsen."); store.error("Discord-webhook", "Eventbericht kon niet worden geplaatst"); return ""; }
        }

        void update(Event event)
        {
            if (url == null || blank(event.discordMessageId)) return;
            try
            {
                URI messageUrl = URI.create(url.toString() + "/messages/" + event.discordMessageId);
                HttpResponse<Void> response = client.send(request(messageUrl, "PATCH", event), HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() < 200 || response.statusCode() >= 300)
                    System.err.println("Discord-webhook kon event niet bijwerken (HTTP " + response.statusCode() + ").");
                    store.error("Discord-webhook", "Eventbericht kon niet worden bijgewerkt (HTTP " + response.statusCode() + ")");
            }
            catch (Exception exception) { System.err.println("Discord-webhook kon event niet bijwerken."); store.error("Discord-webhook", "Eventbericht kon niet worden bijgewerkt"); }
        }

        private HttpRequest request(URI target, String method, Event event)
        {
            return HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(GSON.toJson(payload(event)), StandardCharsets.UTF_8)).build();
        }

        private static Map<String, Object> payload(Event event)
        {
            Map<String, Object> embed = new HashMap<>();
            embed.put("title", event.type.replace('_', ' ') + ": " + event.title);
            embed.put("color", 15158332);
            String description = blank(event.discordDescription) ? event.description :
                (blank(event.description) ? event.discordDescription : event.discordDescription + "\n\n" + event.description);
            if (!blank(description)) embed.put("description", description);
            List<Map<String, Object>> fields = new ArrayList<>();
            addScheduleFields(fields, event);
            if (!blank(event.world)) addField(fields, "Wereld", event.world);
            if (!blank(event.host)) addField(fields, "Host", event.host);
            if (!blank(event.checklist)) addField(fields, "Voorbereiding", event.checklist.replace(";", ", "));
            if (!blank(event.requiredPlugins)) addField(fields, "Benodigde plug-ins", event.requiredPlugins.replace(";", ", "));
            if (!blank(event.strategyWikiUrl)) addField(fields, "📖 Strategie", "[Strategie openen](" + event.strategyWikiUrl + ")");
            if (!blank(event.youtubeUrl)) addField(fields, "▶️ Video", "[Video openen](" + event.youtubeUrl + ")");
            if (!blank(event.registrationUrl)) addField(fields, "Aanmelden", "[Open aanmeldlink](" + event.registrationUrl + ")");
            embed.put("fields", fields);
            Map<String, Object> payload = new HashMap<>();
            payload.put("username", "Dutch Nation Events");
            payload.put("allowed_mentions", java.util.Collections.singletonMap("parse", java.util.Collections.emptyList()));
            payload.put("embeds", java.util.Collections.singletonList(embed));
            return payload;
        }

        private static void addScheduleFields(List<Map<String, Object>> fields, Event event)
        {
            try
            {
                ZoneId zone = ZoneId.of("Europe/Amsterdam");
                ZonedDateTime start = OffsetDateTime.parse(event.startsAt).atZoneSameInstant(zone);
                ZonedDateTime end = OffsetDateTime.parse(event.endsAt).atZoneSameInstant(zone);
                DateTimeFormatter date = DateTimeFormatter.ofPattern("EEE d MMMM yyyy", new Locale("nl", "NL"));
                DateTimeFormatter time = DateTimeFormatter.ofPattern("HH:mm");
                if (start.toLocalDate().equals(end.toLocalDate()))
                {
                    addField(fields, "Datum", date.format(start));
                    addField(fields, "Tijd", time.format(start) + " - " + time.format(end));
                }
                else
                {
                    addField(fields, "Start", date.format(start) + ", " + time.format(start));
                    addField(fields, "Einde", date.format(end) + ", " + time.format(end));
                }
            }
            catch (RuntimeException exception) { addField(fields, "Datum en tijd", event.startsAt + " tot " + event.endsAt); }
        }

        private static void addField(List<Map<String, Object>> fields, String name, String value)
        {
            Map<String, Object> field = new HashMap<>();
            field.put("name", name); field.put("value", value); field.put("inline", false);
            fields.add(field);
        }
    }    static final class State
    {
        String updatedAt;
        List<Member> members = new ArrayList<>();
        List<Event> events = new ArrayList<>();
        Map<String, String> tokenHashes = new HashMap<>();
        String announcementsUrl = "";
        long announcementsSequence;
        String lastAnnouncementMessageId = "";
        List<LogEntry> managementLogs = new ArrayList<>();
        List<LogEntry> errorLogs = new ArrayList<>();
    }
    static final class Feed { String updatedAt; String announcementsUrl; long announcementsSequence; List<Event> events; }
    static final class OwnerLogs { List<LogEntry> management; List<LogEntry> errors; }
    static final class LogEntry { String at; String source; String message; }
    static final class Member { String rsn; String role; String updatedAt; }
    static final class Event
    {
        String id; String startsAt; String endsAt; String type; String title;
        String world; String host; String description; String codeword; String checklist; String requiredPlugins; String strategyWikiUrl; String driveUrl; String registrationUrl; String registrationEndsAt; String youtubeUrl; String discordText; String discordDescription; String discordMessageId;
        String clansOne; String clansTwo; String activity; String bossList;
        boolean codewordRequired;
        boolean allowConflict;
    }
    static final class RoleChange { String rsn; String role; }
    static final class AnnouncementChannel { String url; }
    static final class Actor
    {
        final String rsn; final String role;
        Actor(String rsn, String role) { this.rsn = rsn; this.role = role; }
        boolean owner() { return "OWNER".equalsIgnoreCase(role); }
        boolean administrator() { return "ADMINISTRATOR".equalsIgnoreCase(role); }
        boolean manager() { return "MANAGER".equalsIgnoreCase(role); }
        boolean learnerHost() { return "TEACHER".equalsIgnoreCase(role) || "LEARNER_HOST".equalsIgnoreCase(role); }
        boolean canManageEvents() { return owner() || administrator() || manager() || learnerHost(); }
        boolean canManageEventType(String type) { return !learnerHost() || "LEARNER".equalsIgnoreCase(type); }
        boolean canEdit(Event event) { return !learnerHost() || ("LEARNER".equalsIgnoreCase(event.type) && normalize(rsn).equals(normalize(event.host))); }
    }
}

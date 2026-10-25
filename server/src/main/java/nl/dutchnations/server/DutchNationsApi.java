package nl.dutchnations.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.FileUpload;
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
    // Alleen gebruikt om bestaande installaties veilig naar overdraagbaar ownership
    // te migreren. Na de eerste overdracht bepaalt de opgeslagen eigenaar de rechten.
    private static final String INITIAL_OWNER_RSN = "heavenskill";
    private final Store store;
    private final String bootstrapOwnerHash;
    private final java.util.concurrent.Semaphore dropTestPermit = new java.util.concurrent.Semaphore(4);
    private final DropRequestLimits dropRequestLimits = new DropRequestLimits();
    private final Map<String, CachedDropList> participantDropLists = new HashMap<>();
    private final java.util.concurrent.Semaphore participantListPermit = new java.util.concurrent.Semaphore(2);
    private JDA discordBot;

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
        app.start(bind, port, System.getenv("DISCORD_BOT_TOKEN"));
    }

    private void start(String bind, int port, String discordBotToken) throws IOException
    {
        HttpServer server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        server.createContext("/", this::root);
        server.createContext("/health", exchange -> send(exchange, 200, map("status", "ok")));
        server.createContext("/feed.json", this::feed);
        server.createContext("/api/events", this::events);
        server.createContext("/api/learner-templates", this::learnerTemplates);
        server.createContext("/api/roles", this::roles);
        server.createContext("/api/announcement-channel", this::announcementChannel);
        server.createContext("/api/announcement-visibility", this::announcementVisibility);
        server.createContext("/api/event-announcement-channel", this::eventAnnouncementChannel);
        server.createContext("/api/drop-channel", this::dropChannel);
        server.createContext("/api/drop-list", this::googleDropList);
        server.createContext("/api/drop-test", this::dropTest);
        server.createContext("/api/event-drop-list", exchange -> googleDropList(exchange, true));
        server.createContext("/api/event-drops", exchange -> dropTest(exchange, true));
        server.createContext("/api/local-drop-test", this::localDropTest);
        server.createContext("/api/owner-logs", this::ownerLogs);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        startDiscordBot(discordBotToken);

        ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor();
        cleanup.scheduleAtFixedRate(store::removeExpired, 0, 1, TimeUnit.MINUTES);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { cleanup.shutdown(); if (discordBot != null) discordBot.shutdown(); server.stop(1); }));
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

        if (Boolean.getBoolean("dutch.nations.local"))
        {
            // The isolated drop test only sends; it does not listen to any Discord messages.
            discordBot = JDABuilder.createDefault(token).build();
            System.out.println("Discord-bot voor lokale droptest wordt verbonden");
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
    private void publishEventWithBot(Event event)
    {
        if (Boolean.getBoolean("dutch.nations.local")) return;
        if (discordBot == null)
        {
            store.error("Discord-bot", "Eventbericht niet geplaatst: bot is niet verbonden");
            return;
        }
        String channelId = store.eventAnnouncementsChannelId();
        if (blank(channelId)) return;
        TextChannel channel = discordBot.getTextChannelById(channelId);
        if (channel == null)
        {
            store.error("Discord-bot", "Eventbericht niet geplaatst: eventmeldingenkanaal niet bereikbaar");
            return;
        }
        sendDiscordParts(channel, event, channelId, splitDiscordMessage(discordEventText(event)), 0, new ArrayList<>());
    }

    private void sendDiscordParts(TextChannel channel, Event event, String channelId, List<String> parts, int index, List<String> messageIds)
    {
        if (index >= parts.size())
        {
            store.setDiscordMessage(event.id, String.join(",", messageIds), channelId);
            return;
        }
        channel.sendMessage(parts.get(index)).queue(
            message ->
            {
                messageIds.add(message.getId());
                sendDiscordParts(channel, event, channelId, parts, index + 1, messageIds);
            },
            error -> store.error("Discord-bot", "Eventbericht kon niet worden geplaatst in het eventmeldingenkanaal"));
    }

    private void updateDiscordEvent(Event event)
    {
        deleteDiscordEvent(event);
        publishEventWithBot(event);
    }

    private void deleteDiscordEvent(Event event)
    {
        if (Boolean.getBoolean("dutch.nations.local")) return;
        TextChannel channel = discordEventChannel(event);
        if (channel == null) return;
        for (String messageId : discordMessageIds(event))
        {
            channel.retrieveMessageById(messageId).queue(
                message -> message.delete().queue(
                    deleted -> { }, error -> store.error("Discord-bot", "Eventbericht kon niet worden verwijderd")),
                error -> store.error("Discord-bot", "Eventbericht kon niet worden gevonden om te verwijderen"));
        }
    }

    private TextChannel discordEventChannel(Event event)
    {
        if (discordBot == null || blank(event.discordMessageId)) return null;
        String channelId = blank(event.discordChannelId) ? store.eventAnnouncementsChannelId() : event.discordChannelId;
        if (blank(channelId)) { store.error("Discord-bot", "Eventbericht kon niet worden verwerkt: eventmeldingenkanaal ontbreekt"); return null; }
        TextChannel channel = discordBot.getTextChannelById(channelId);
        if (channel == null) store.error("Discord-bot", "Eventbericht kon niet worden verwerkt: eventmeldingenkanaal niet bereikbaar");
        return channel;
    }

    private static String discordEventText(Event event)
    {
        StringBuilder text = new StringBuilder("**" + event.title + "**");
        if (!blank(event.bossList)) text.append("\nBosses/activiteiten: ").append(event.bossList.replace(";", ", "));
        else if (!blank(event.activity)) text.append("\nActiviteit: ").append(event.activity);
        appendDiscordDateTime(text, event);
        if (!blank(event.registrationUrl)) text.append("\nAanmelden: ").append(event.registrationUrl);
        if (!blank(event.discordDescription)) text.append("\n\n").append(event.discordDescription);
        if ("LEARNER".equalsIgnoreCase(event.type)) appendLearnerPreparation(text, event);
        return text.toString();
    }

    private static List<String> splitDiscordMessage(String text)
    {
        List<String> parts = new ArrayList<>();
        String remaining = text == null ? "" : text.trim();
        while (remaining.length() > 2000)
        {
            int split = remaining.lastIndexOf('\n', 2000);
            if (split < 1) split = remaining.lastIndexOf(' ', 2000);
            if (split < 1) split = 2000;
            parts.add(remaining.substring(0, split).trim());
            remaining = remaining.substring(split).trim();
        }
        if (!remaining.isEmpty()) parts.add(remaining);
        return parts;
    }

    private static List<String> discordMessageIds(Event event)
    {
        List<String> ids = new ArrayList<>();
        if (event == null || blank(event.discordMessageId)) return ids;
        for (String id : event.discordMessageId.split(",")) if (!blank(id)) ids.add(id.trim());
        return ids;
    }

    private static void appendLearnerPreparation(StringBuilder text, Event event)
    {
        if (blank(event.checklist) && blank(event.requiredPlugins) && blank(event.strategyWikiUrl) && blank(event.youtubeUrl)) return;
        text.append("\n\n**Voorbereiden**");
        if (!blank(event.checklist)) text.append("\n• Vooraf doen: ").append(event.checklist.replace(";", ", "));
        if (!blank(event.requiredPlugins)) text.append("\n• Plug-ins: ").append(event.requiredPlugins.replace(";", ", "));
        if (!blank(event.strategyWikiUrl)) text.append("\n• Wiki: ").append(event.strategyWikiUrl);
        if (!blank(event.youtubeUrl)) text.append("\n• YouTube: ").append(event.youtubeUrl);
    }

    private static void appendDiscordDateTime(StringBuilder text, Event event)
    {
        try
        {
            ZoneId zone = ZoneId.of("Europe/Amsterdam");
            ZonedDateTime start = OffsetDateTime.parse(event.startsAt).atZoneSameInstant(zone);
            ZonedDateTime end = OffsetDateTime.parse(event.endsAt).atZoneSameInstant(zone);
            DateTimeFormatter date = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.forLanguageTag("nl-NL"));
            DateTimeFormatter time = DateTimeFormatter.ofPattern("HH:mm");
            if (start.toLocalDate().equals(end.toLocalDate()))
            {
                text.append("\nDatum: ").append(date.format(start));
                text.append("\nTijd: ").append(time.format(start)).append(" - ").append(time.format(end));
            }
            else
            {
                text.append("\nStart: ").append(date.format(start)).append(" om ").append(time.format(start));
                text.append("\nEinde: ").append(date.format(end)).append(" om ").append(time.format(end));
            }
        }
        catch (RuntimeException ignored) { }
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
            if ("BOSS".equals(event.type) || "CLAN_EVENT".equals(event.type) || "CLAN_VS_CLAN".equals(event.type)) { event.checklist = ""; event.requiredPlugins = ""; event.strategyWikiUrl = ""; } else { event.driveUrl = ""; event.dropListUrl = ""; event.registrationUrl = ""; event.registrationEndsAt = ""; }
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
            publishEventWithBot(event);
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
            event.discordDescription = blank(event.discordText) ? existing.discordDescription : event.discordText;
            event.discordMessageId = existing.discordMessageId;
            event.discordChannelId = existing.discordChannelId;
            if (event.dropSource == null) event.dropSource = existing.dropSource;
            if (event.dropListUrl == null) event.dropListUrl = existing.dropListUrl;
            // Older Plugin Hub clients do not send this newly optional field.
            if (event.teamNames == null) event.teamNames = existing.teamNames;
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
            if ("BOSS".equals(event.type) || "CLAN_EVENT".equals(event.type) || "CLAN_VS_CLAN".equals(event.type)) { event.checklist = ""; event.requiredPlugins = ""; event.strategyWikiUrl = ""; } else { event.driveUrl = ""; event.dropListUrl = ""; event.registrationUrl = ""; event.registrationEndsAt = ""; }
            if ("BOSS".equals(event.type)) event.world = "";
            if ("CLAN_VS_CLAN".equals(event.type)) event.world = "";
            if (!"CLAN_VS_CLAN".equals(event.type)) { event.clansOne = ""; event.clansTwo = ""; event.activity = ""; }
            if (!"CLAN_EVENT".equals(event.type)) event.bossList = "";
            Event conflict = store.findConflict(event, existing.id);
            if (conflict != null && !event.allowConflict) { sendError(exchange, 409, "Event overlapt met " + conflict.title); return; }
            store.updateEvent(existing.id, event);
            updateDiscordEvent(event);
            store.audit(actor, "Event aangepast: " + event.title);
            if (codewordChanged) store.audit(actor, "Codewoord gewijzigd voor event: " + event.title);
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
            if (removed) { deleteDiscordEvent(existing); store.audit(actor, "Event verwijderd: " + existing.title); }
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

    private void learnerTemplates(HttpExchange exchange) throws IOException
    {
        Actor actor = authenticate(exchange);
        if (actor == null || !actor.owner()) { sendError(exchange, 403, "Alleen de owner mag learner-sjablonen beheren"); return; }
        String path = exchange.getRequestURI().getPath();
        String prefix = "/api/learner-templates/";
        if ("POST".equals(exchange.getRequestMethod()) && "/api/learner-templates".equals(path))
        {
            LearnerTemplate template = read(exchange, LearnerTemplate.class);
            String error = validateLearnerTemplate(template);
            if (error != null) { sendError(exchange, 400, error); return; }
            template.id = UUID.randomUUID().toString();
            store.addLearnerTemplate(template);
            store.audit(actor, "Learner-sjabloon toegevoegd: " + template.name);
            send(exchange, 201, template); return;
        }
        if (("PUT".equals(exchange.getRequestMethod()) || "DELETE".equals(exchange.getRequestMethod())) && path.startsWith(prefix) && path.length() > prefix.length())
        {
            String id = path.substring(prefix.length());
            if ("DELETE".equals(exchange.getRequestMethod()))
            {
                LearnerTemplate existing = store.learnerTemplate(id);
                if (existing == null) { sendError(exchange, 404, "Learner-sjabloon niet gevonden"); return; }
                store.deleteLearnerTemplate(id);
                store.audit(actor, "Learner-sjabloon verwijderd: " + existing.name);
                send(exchange, 200, map("deleted", true)); return;
            }
            LearnerTemplate template = read(exchange, LearnerTemplate.class);
            String error = validateLearnerTemplate(template);
            if (error != null) { sendError(exchange, 400, error); return; }
            template.id = id;
            if (!store.updateLearnerTemplate(id, template)) { sendError(exchange, 404, "Learner-sjabloon niet gevonden"); return; }
            store.audit(actor, "Learner-sjabloon aangepast: " + template.name);
            send(exchange, 200, template); return;
        }
        methodNotAllowed(exchange);
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
    private void eventAnnouncementChannel(HttpExchange exchange) throws IOException
    {
        Actor actor = authenticate(exchange);
        if (actor == null || !actor.owner()) { sendError(exchange, 403, "Alleen de owner kan het eventmeldingenkanaal wijzigen"); return; }
        if (!"POST".equals(exchange.getRequestMethod())) { methodNotAllowed(exchange); return; }
        AnnouncementChannel change = read(exchange, AnnouncementChannel.class);
        if (change == null || !validDiscordChannelUrl(change.url))
        { sendError(exchange, 400, "Geldige Discord-kanaallink is verplicht"); return; }
        store.saveEventAnnouncementsUrl(change.url.trim());
        store.audit(actor, "Eventmeldingenkanaal gewijzigd");
        send(exchange, 200, map("eventAnnouncementsUrl", change.url.trim()));
    }

    private void announcementVisibility(HttpExchange exchange) throws IOException
    {
        Actor actor = authenticate(exchange);
        if (actor == null || !actor.owner()) { sendError(exchange, 403, "Alleen de owner kan de mededelingenknop wijzigen"); return; }
        if (!"POST".equals(exchange.getRequestMethod())) { methodNotAllowed(exchange); return; }
        AnnouncementVisibility change = read(exchange, AnnouncementVisibility.class);
        if (change == null) { sendError(exchange, 400, "Instelling ontbreekt"); return; }
        store.saveAnnouncementsVisible(change.visible);
        store.audit(actor, change.visible ? "Mededelingenknop zichtbaar gemaakt" : "Mededelingenknop verborgen");
        send(exchange, 200, map("announcementsVisible", change.visible));
    }

    private void dropChannel(HttpExchange exchange) throws IOException
    {
        Actor actor = authenticate(exchange);
        if (actor == null || !actor.owner()) { sendError(exchange, 403, "Alleen de owner kan het dropkanaal wijzigen"); return; }
        if (!"POST".equals(exchange.getRequestMethod())) { methodNotAllowed(exchange); return; }
        AnnouncementChannel change = read(exchange, AnnouncementChannel.class);
        if (change == null || !validDiscordChannelUrl(change.url))
        { sendError(exchange, 400, "Geldige Discord-kanaallink is verplicht"); return; }
        store.saveDropChannelUrl(change.url.trim());
        store.audit(actor, "Dropkanaal gewijzigd");
        send(exchange, 200, map("dropChannelUrl", change.url.trim()));
    }

    private void googleDropList(HttpExchange exchange) throws IOException
    { googleDropList(exchange, false); }

    private void googleDropList(HttpExchange exchange, boolean participant) throws IOException
    {
        if (!"GET".equals(exchange.getRequestMethod())) { methodNotAllowed(exchange); return; }
        if (participant)
        {
            if (!"/api/event-drop-list".equals(exchange.getRequestURI().getPath())) { sendError(exchange, 404, "Route niet gevonden"); return; }
            if (!dropRequestLimits.allow("list:" + dropPeer(exchange), 240, System.currentTimeMillis()))
            { sendError(exchange, 429, "Te veel droplijstverzoeken; probeer later opnieuw"); return; }
        }
        else
        {
            Actor actor = authenticate(exchange);
            if (actor == null || !actor.owner()) { sendError(exchange, 403, "Alleen de owner kan de droplijst ophalen"); return; }
        }
        String query = exchange.getRequestURI().getRawQuery();
        String id;
        try { id = query != null && query.startsWith("eventId=") && !query.contains("&")
            ? URLDecoder.decode(query.substring("eventId=".length()), StandardCharsets.UTF_8) : ""; }
        catch (IllegalArgumentException ex) { sendError(exchange, 400, "Ongeldig event-id"); return; }
        Event event = blank(id) || id.length() > 120 ? null : store.event(id);
        if (participant && !activeDropEvent(event, OffsetDateTime.now(ZoneOffset.UTC)))
        { sendError(exchange, 409, "Event is niet actief"); return; }
        String dropListUrl = effectiveDropListUrl(event);
        boolean googleSheet = GoogleSheetDrops.sheetId(dropListUrl) != null;
        if (event == null || (!googleSheet && !PvmWarProgress.SOURCE.equals(event.dropSource)))
        { sendError(exchange, 404, "Geen ondersteunde droplijst voor dit event"); return; }
        try
        {
            Map<String, Object> result = map("source", googleSheet ? dropListUrl : PvmWarProgress.SOURCE);
            result.put("items", participant ? participantDropItems(dropListUrl, googleSheet, eventTeams(event))
                : googleSheet ? GoogleSheetDrops.fetch(dropListUrl, eventTeams(event)) : PvmWarProgress.fetch().availableItems());
            result.put("teams", EventTeams.names(eventTeams(event)));
            send(exchange, 200, result);
        }
        catch (IOException | RuntimeException ex)
        { sendError(exchange, 503, googleSheet ? "Google Sheet kan niet worden gelezen; controleer de leesrechten en instellingen" : "PvM War-pagina kan niet worden gelezen"); }
    }

    private void dropTest(HttpExchange exchange) throws IOException
    { dropTest(exchange, false); }

    private void dropTest(HttpExchange exchange, boolean participant) throws IOException
    {
        if (!"POST".equals(exchange.getRequestMethod())) { methodNotAllowed(exchange); return; }
        if (participant)
        {
            if (!"/api/event-drops".equals(exchange.getRequestURI().getPath())) { sendError(exchange, 404, "Route niet gevonden"); return; }
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json"))
            { sendError(exchange, 415, "JSON-dropgegevens zijn verplicht"); return; }
            if (!dropRequestLimits.allow("upload:" + dropPeer(exchange), 60, System.currentTimeMillis())
                || !dropRequestLimits.allow("upload:all", 180, System.currentTimeMillis()))
            { sendError(exchange, 429, "Te veel screenshots; probeer later opnieuw"); return; }
        }
        else
        {
            Actor actor = authenticate(exchange);
            if (actor == null || !actor.owner()) { sendError(exchange, 403, "Alleen de owner kan de droptest gebruiken"); return; }
        }
        if (!dropTestPermit.tryAcquire()) { sendError(exchange, 429, "Een droptest is nog bezig"); return; }
        try
        {
            byte[] body = exchange.getRequestBody().readNBytes(5_600_001);
            if (body.length > 5_600_000) { sendError(exchange, 413, "Screenshot is te groot"); return; }
            DropTestReport report;
            try { report = GSON.fromJson(new String(body, StandardCharsets.UTF_8), DropTestReport.class); }
            catch (RuntimeException ex) { report = null; }
            if (report == null || blank(report.eventId) || report.eventId.length() > 120
                || report.screenshot == null || report.screenshot.length() > 5_592_408)
            { sendError(exchange, 400, "Ongeldige dropgegevens of screenshot"); return; }
            Event event = report == null || blank(report.eventId) ? null : store.event(report.eventId);
            String error = validateLocalDrop(report, event, OffsetDateTime.now(ZoneOffset.UTC), report == null ? null : report.item);
            if (error != null) { sendError(exchange, 400, error); return; }
            if (participant && !dropRequestLimits.allow("player:" + normalize(report.rsn), 20, System.currentTimeMillis()))
            { sendError(exchange, 429, "Te veel screenshots van deze speler; probeer later opnieuw"); return; }
            if (participant && !validParticipantPng(Base64.getDecoder().decode(report.screenshot)))
            { sendError(exchange, 400, "Ongeldig PNG-spelbeeld"); return; }
            String dropListUrl = effectiveDropListUrl(event);
            boolean googleSheet = GoogleSheetDrops.sheetId(dropListUrl) != null;
            if (!googleSheet && !PvmWarProgress.SOURCE.equals(event.dropSource))
            { sendError(exchange, 409, "Event heeft geen ondersteunde droplijst"); return; }
            String items = event.dropItems;
            PvmWarProgress.Slots slots = null;
            if (googleSheet)
            {
                try { items = String.join(";", GoogleSheetDrops.fetch(dropListUrl, eventTeams(event))); }
                catch (IOException | RuntimeException ex) { sendError(exchange, 503, "Google Sheet kan niet worden gelezen; geen bericht geplaatst"); return; }
            }
            if (!googleSheet)
            {
                try
                {
                    PvmWarProgress progress = PvmWarProgress.fetch();
                    slots = progress.lookup(report.item);
                    items = String.join(";", progress.availableItems());
                }
                catch (IOException | RuntimeException ex) { sendError(exchange, 503, "PvM War-voortgang niet bereikbaar; geen bericht geplaatst"); return; }
                if (slots == null) { sendError(exchange, 422, "Item niet herkenbaar op de PvM War-pagina"); return; }
                if (slots.approved >= 3) { sendError(exchange, 409, "Dit item is al drie keer goedgekeurd en is klaar"); return; }
            }
            error = validateLocalDrop(report, event, OffsetDateTime.now(ZoneOffset.UTC), items);
            if (error != null) { sendError(exchange, 409, error); return; }
            String channelUrl = store.dropChannelUrl();
            if (!validDiscordChannelUrl(channelUrl)) { sendError(exchange, 400, "Stel eerst het screenshotkanaal in bij de ownerinstellingen"); return; }
            String[] channelParts = URI.create(channelUrl).getPath().split("/");
            TextChannel channel = discordBot == null ? null : discordBot.getTextChannelById(channelParts[3]);
            if (channel == null || !channelParts[2].equals(channel.getGuild().getId()))
            { sendError(exchange, 503, "Bot of ingesteld screenshotkanaal niet bereikbaar"); return; }
            // Recheck after the external Sheet lookup and before handing an image to Discord.
            if (!activeDropEvent(store.event(report.eventId), OffsetDateTime.now(ZoneOffset.UTC)))
            { sendError(exchange, 409, "Event is niet actief"); return; }
            if (participant && !dropRequestLimits.remember("sent:" + sha256(report.eventId + "\n" + normalize(report.rsn)
                + "\n" + report.item + "\n" + report.quantity + "\n" + report.screenshot), System.currentTimeMillis()))
            { sendError(exchange, 409, "Deze screenshot is al aangeboden; controleer Discord"); return; }
            try
            {
                channel.sendMessage("**Eventdrop (test)**\nSpeler: " + report.rsn + "\nItem: " + report.item + "\nAantal: " + report.quantity)
                    .setAllowedMentions(java.util.Collections.emptyList())
                    .addFiles(FileUpload.fromData(Base64.getDecoder().decode(report.screenshot), "drop.png"))
                    .submit().get(15, TimeUnit.SECONDS);
            }
            catch (java.util.concurrent.TimeoutException ex)
            { sendError(exchange, 504, "Discord reageerde niet op tijd; controleer het kanaal voordat je opnieuw probeert"); return; }
            catch (InterruptedException ex)
            { Thread.currentThread().interrupt(); sendError(exchange, 503, "Droptest onderbroken; controleer het kanaal"); return; }
            catch (Exception ex) { sendError(exchange, 503, "Discord-bericht kon niet worden geplaatst"); return; }
            Map<String, Object> result = map("delivered", true);
            if (slots != null) result.put("approved", slots.approved);
            send(exchange, 200, result);
        }
        finally { dropTestPermit.release(); }
    }

    private static String dropPeer(HttpExchange exchange)
    {
        // Never trust caller-supplied forwarding headers to bypass request limits.
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    static boolean activeDropEvent(Event event, OffsetDateTime now)
    {
        try { return event != null && !now.isBefore(OffsetDateTime.parse(event.startsAt)) && now.isBefore(OffsetDateTime.parse(event.endsAt)); }
        catch (RuntimeException ex) { return false; }
    }

    static boolean validParticipantPng(byte[] image)
    {
        if (image == null || image.length < 33 || image.length > 4 * 1024 * 1024) return false;
        byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        for (int i = 0; i < signature.length; i++) if (image[i] != signature[i]) return false;
        java.nio.ByteBuffer header = java.nio.ByteBuffer.wrap(image);
        if (header.getInt(8) != 13 || header.getInt(12) != 0x49484452) return false;
        int width = header.getInt(16), height = header.getInt(20);
        return width > 0 && height > 0 && width <= 16384 && height <= 16384 && (long) width * height <= 20_000_000;
    }

    private List<String> participantDropItems(String source, boolean googleSheet, String teams) throws IOException
    {
        long now = System.currentTimeMillis();
        String key = googleSheet ? source + "\n" + teams : PvmWarProgress.SOURCE;
        synchronized (participantDropLists)
        {
            participantDropLists.values().removeIf(value -> value.expiresAt <= now);
            CachedDropList cached = participantDropLists.get(key);
            if (cached != null)
            {
                if (cached.items == null) throw new IOException("Droplijst wordt geladen of is tijdelijk onbereikbaar");
                return cached.items;
            }
            if (participantDropLists.size() >= 64 || !participantListPermit.tryAcquire()) throw new IOException("Droplijstserver is bezet");
            participantDropLists.put(key, new CachedDropList(null, now + 60000));
        }
        try
        {
            List<String> items = new ArrayList<>(googleSheet ? GoogleSheetDrops.fetch(source, teams) : PvmWarProgress.fetch().availableItems());
            synchronized (participantDropLists) { participantDropLists.put(key, new CachedDropList(items, System.currentTimeMillis() + 30000)); }
            return items;
        }
        catch (IOException | RuntimeException ex)
        {
            synchronized (participantDropLists) { participantDropLists.put(key, new CachedDropList(null, System.currentTimeMillis() + 10000)); }
            throw ex;
        }
        finally { participantListPermit.release(); }
    }

    private static final class CachedDropList
    {
        final List<String> items;
        final long expiresAt;
        CachedDropList(List<String> items, long expiresAt) { this.items = items; this.expiresAt = expiresAt; }
    }

    private void localDropTest(HttpExchange exchange) throws IOException
    {
        if (!Boolean.getBoolean("dutch.nations.local") || !exchange.getLocalAddress().getAddress().isLoopbackAddress()
            || !exchange.getRemoteAddress().getAddress().isLoopbackAddress())
        { sendError(exchange, 404, "Route niet gevonden"); return; }
        if (!"POST".equals(exchange.getRequestMethod())) { methodNotAllowed(exchange); return; }
        byte[] body = exchange.getRequestBody().readNBytes(5_600_001);
        if (body.length > 5_600_000) { sendError(exchange, 413, "Screenshot is te groot"); return; }
        DropTestReport report;
        try { report = GSON.fromJson(new String(body, StandardCharsets.UTF_8), DropTestReport.class); }
        catch (RuntimeException ex) { report = null; }
        Event event = report == null || blank(report.eventId) ? null : store.event(report.eventId);
        String dropListUrl = effectiveDropListUrl(event);
        boolean googleSheet = GoogleSheetDrops.sheetId(dropListUrl) != null;
        String selectedItems = event == null ? "" : event.dropItems;
        if (googleSheet)
        {
            try { selectedItems = String.join(";", GoogleSheetDrops.fetch(dropListUrl, eventTeams(event))); }
            catch (IOException | RuntimeException ex) { sendError(exchange, 503, "Google Sheet kan niet worden gelezen; geen drop verstuurd"); return; }
        }
        PvmWarProgress.Slots slots = null;
        if (event != null && !googleSheet && PvmWarProgress.SOURCE.equals(event.dropSource))
        {
            try
            {
                PvmWarProgress progress = PvmWarProgress.fetch();
                slots = report == null ? null : progress.lookup(report.item);
                selectedItems = String.join(";", progress.availableItems());
            }
            catch (IOException | RuntimeException ex) { sendError(exchange, 503, "PvM War-voortgang niet bereikbaar; drop niet verstuurd"); return; }
            if (slots == null) { sendError(exchange, 422, "Item niet herkenbaar op de PvM War-pagina; drop niet verstuurd"); return; }
            if (slots.approved >= 3) { sendError(exchange, 409, "Dit item is al drie keer goedgekeurd en is klaar"); return; }
        }
        String error = validateLocalDrop(report, event, OffsetDateTime.now(), selectedItems);
        if (error != null) { sendError(exchange, 400, error); return; }
        String channelUrl = dropChannelFor(event, store.dropChannelUrl());
        if (!validDiscordChannelUrl(channelUrl)) { sendError(exchange, 400, "Dropkanaal ontbreekt"); return; }
        boolean queued = Boolean.getBoolean("dutch.nations.localDropSend");
        if (queued)
        {
            if (!localDropChannelAllowed(channelUrl, System.getenv("DN_DROP_TEST_CHANNEL")))
            { sendError(exchange, 403, "Dit kanaal is niet het toegestane Discord-testkanaal"); return; }
            TextChannel channel = discordBot == null ? null : discordBot.getTextChannelById(discordChannelId(channelUrl));
            if (channel == null) { sendError(exchange, 503, "Bot of dropkanaal niet bereikbaar"); return; }
            byte[] image = Base64.getDecoder().decode(report.screenshot);
            channel.sendMessage("**Eventdrop**\nSpeler: " + report.rsn + "\nItem: " + report.item + "\nAantal: " + report.quantity)
                .setAllowedMentions(java.util.Collections.emptyList()).addFiles(FileUpload.fromData(image, "drop.png"))
                .queue(message -> { }, failure -> store.error("Discord-bot", "Dropscreenshot kon niet worden geplaatst"));
        }
        Map<String, Object> result = map("localTest", true);
        result.put("discordQueued", queued);
        if (slots != null) { result.put("approved", slots.approved); result.put("waiting", slots.waiting); }
        send(exchange, 200, result);
    }

    static String validateLocalDrop(DropTestReport report, Event event, OffsetDateTime now)
    { return validateLocalDrop(report, event, now, event == null ? null : event.dropItems); }

    static String validateLocalDrop(DropTestReport report, Event event, OffsetDateTime now, String selectedItems)
    {
        if (report == null || event == null || blank(report.rsn) || !report.rsn.matches("[A-Za-z0-9 _-]{1,12}")
            || !valid(report.item, 100) || report.quantity <= 0) return "Ongeldige dropgegevens";
        try
        {
            if (now.isBefore(OffsetDateTime.parse(event.startsAt)) || !now.isBefore(OffsetDateTime.parse(event.endsAt))) return "Event is niet actief";
            boolean selected = false;
            if (selectedItems != null) for (String item : selectedItems.split(";"))
                if (!item.trim().isEmpty() && GoogleSheetDrops.matchesItem(item, report.item)) selected = true;
            if (!selected) return "Item staat niet op de droplijst";
            byte[] image = Base64.getDecoder().decode(report.screenshot);
            byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
            if (image.length < signature.length || image.length > 4 * 1024 * 1024) return "Ongeldige screenshot";
            for (int i = 0; i < signature.length; i++) if (image[i] != signature[i]) return "Ongeldige screenshot";
        }
        catch (RuntimeException ex) { return "Ongeldige dropgegevens"; }
        return null;
    }

    // Kept for the online drop-test endpoint and its security tests. The normal
    // screenshot flow uses validateLocalDrop above, which returns the user-facing error.
    static byte[] validateDropTest(DropTestReport report)
    {
        if (report == null || blank(report.eventId) || report.eventId.length() > 120 || blank(report.rsn)
            || !report.rsn.matches("[A-Za-z0-9 _-]{1,12}") || !valid(report.item, 100) || report.quantity <= 0
            || report.screenshot == null || report.screenshot.length() > 5_592_408) return null;
        try
        {
            byte[] image = Base64.getDecoder().decode(report.screenshot);
            byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
            if (image.length < signature.length || image.length > 4 * 1024 * 1024) return null;
            for (int i = 0; i < signature.length; i++) if (image[i] != signature[i]) return null;
            return image;
        }
        catch (IllegalArgumentException ex) { return null; }
    }

    static boolean dropTestEligible(Event event, String item, OffsetDateTime now)
    {
        if (event == null || !PvmWarProgress.SOURCE.equals(event.dropSource) || blank(event.dropItems) || blank(item)) return false;
        try
        {
            if (now.isBefore(OffsetDateTime.parse(event.startsAt)) || !now.isBefore(OffsetDateTime.parse(event.endsAt))) return false;
            for (String candidate : event.dropItems.split(";"))
                if (candidate.trim().replace('\u2019', '\'').equalsIgnoreCase(item.trim().replace('\u2019', '\''))) return true;
        }
        catch (RuntimeException ex) { return false; }
        return false;
    }

    static String dropChannelFor(Event event, String defaultChannel)
    { return event == null || blank(event.dropChannelUrl) ? defaultChannel : event.dropChannelUrl; }

    static boolean localDropChannelAllowed(String channelUrl, String allowedUrl)
    {
        return validDiscordChannelUrl(channelUrl) && validDiscordChannelUrl(allowedUrl)
            && channelUrl.trim().equals(allowedUrl.trim());
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
        if ("OWNER".equals(role))
        {
            if (!actor.owner()) { sendError(exchange, 403, "Alleen de huidige owner kan ownership overdragen"); return; }
            if (normalize(actor.rsn).equals(normalize(change.rsn)))
            { sendError(exchange, 400, "Deze speler is al de owner"); return; }
            String newToken = randomToken();
            store.transferOwner(change.rsn, sha256(newToken));
            store.audit(actor, "Ownership overgedragen aan " + change.rsn.trim());
            Map<String, Object> response = new HashMap<>();
            response.put("rsn", change.rsn.trim()); response.put("role", "OWNER"); response.put("managementToken", newToken);
            send(exchange, 200, response); return;
        }
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
            if (store.isOwner(change.rsn)) { sendError(exchange, 400, "De huidige owner kan niet worden ingetrokken; draag ownership eerst over"); return; }
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
        if (MessageDigest.isEqual(hash.getBytes(StandardCharsets.UTF_8), bootstrapOwnerHash.getBytes(StandardCharsets.UTF_8)) && store.bootstrapOwnerActive())
            return new Actor(store.ownerRsn(), "OWNER");
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
        if (!validStandingsUrl(event.driveUrl)) return "Tussenstand-link is ongeldig; gebruik een volledig webadres met http:// of https://";
        if (!validStandingsUrl(event.dropListUrl)) return "Screenshot-droplijst-link is ongeldig; gebruik een volledig webadres met http:// of https://";
        if (!validDiscordUrl(event.registrationUrl)) return "Discord-aanmeldlink is ongeldig";
        if (!validYoutubeUrl(event.youtubeUrl)) return "YouTube-link is ongeldig";
        if (!validOptional(event.clansOne, 300) || !validOptional(event.clansTwo, 300)) return "Clanlijst is ongeldig";
        if (!validOptional(event.activity, 120)) return "Activiteit is ongeldig";
        if (!validOptional(event.bossList, 300)) return "Boss- of activiteitenlijst is ongeldig";
        if (!blank(event.teamNames))
        {
            if (!"CLAN_EVENT".equals(type)) return "Teams zijn alleen beschikbaar bij clan-events";
            try { EventTeams.names(event.teamNames); }
            catch (IllegalArgumentException ex) { return ex.getMessage(); }
            if (GoogleSheetDrops.sheetId(event.dropListUrl) == null) return "Teams vereisen een Google Sheet als screenshot-droplijst";
        }
        if (!validOptional(event.dropItems, 12000)) return "Droplijst is ongeldig of langer dan 12.000 tekens";
        if (!blank(event.dropSource) && !PvmWarProgress.SOURCE.equals(event.dropSource)) return "Ongeldige dropbron";
        if (!blank(event.dropChannelUrl) && !validDiscordChannelUrl(event.dropChannelUrl)) return "Dropkanaal is ongeldig";
        if (!validOptional(event.discordText, 12000)) return "Discord-beschrijving is ongeldig of langer dan 12.000 tekens";
        int descriptionLength = event.description == null ? 0 : event.description.length();
        int discordDescriptionLength = event.discordText == null ? 0 : event.discordText.length();
        if (descriptionLength + discordDescriptionLength > 15000) return "Beschrijving en Discord-beschrijving samen mogen maximaal 15.000 tekens bevatten";
        if (("LEARNER".equals(type) || "MASS".equals(type) || "CLAN_EVENT".equals(type)) && (blank(event.world) || !event.world.matches("\\d{3,4}"))) return "Geldig wereldnummer is verplicht";
        if (("BOSS".equals(type) || "CLAN_EVENT".equals(type) || "CLAN_VS_CLAN".equals(type) || ("MASS".equals(type) && event.codewordRequired)) && !valid(event.codeword, 40)) return "Dit event vereist een geldig codewoord van maximaal 40 tekens";
        if ("CLAN_EVENT".equals(type) && !valid(event.bossList, 300)) return "Vul minimaal één boss of activiteit in";
        if ("CLAN_VS_CLAN".equals(type) && (!valid(event.clansOne, 300) || !valid(event.clansTwo, 300) || !valid(event.activity, 120))) return "Vul beide clanlijsten en de activiteit in";
        return null;
    }

    private static String validateLearnerTemplate(LearnerTemplate template)
    {
        if (template == null || !valid(template.name, 60) || !valid(template.title, 80) ||
            !validOptional(template.description, 240) || !validOptional(template.discordDescription, 12000) || !validOptional(template.checklist, 400) ||
            !validOptional(template.requiredPlugins, 300) || !validWikiUrl(template.strategyWikiUrl) ||
            !validYoutubeUrl(template.youtubeUrl)) return "Sjabloontekst of link is ongeldig";
        return null;
    }

    private static List<LearnerTemplate> defaultLearnerTemplates()
    {
        List<LearnerTemplate> templates = new ArrayList<>();
        addDefaultTemplate(templates, "Chambers of Xeric (CoX)", "https://oldschool.runescape.wiki/w/Chambers_of_Xeric/Strategies");
        addDefaultTemplate(templates, "Theatre of Blood (ToB)", "https://oldschool.runescape.wiki/w/Theatre_of_Blood/Strategies");
        addDefaultTemplate(templates, "Tombs of Amascut (ToA)", "https://oldschool.runescape.wiki/w/Tombs_of_Amascut/Strategies");
        addDefaultTemplate(templates, "General Graardor (Bandos)", "https://oldschool.runescape.wiki/w/General_Graardor/Strategies");
        addDefaultTemplate(templates, "Commander Zilyana (Saradomin)", "https://oldschool.runescape.wiki/w/Commander_Zilyana/Strategies");
        addDefaultTemplate(templates, "Kree'arra (Armadyl)", "https://oldschool.runescape.wiki/w/Kree%27arra/Strategies");
        addDefaultTemplate(templates, "K'ril Tsutsaroth (Zamorak)", "https://oldschool.runescape.wiki/w/K%27ril_Tsutsaroth/Strategies");
        addDefaultTemplate(templates, "Callisto", "https://oldschool.runescape.wiki/w/Callisto/Strategies");
        addDefaultTemplate(templates, "Vet'ion", "https://oldschool.runescape.wiki/w/Vet%27ion/Strategies");
        addDefaultTemplate(templates, "Venenatis", "https://oldschool.runescape.wiki/w/Venenatis/Strategies");
        addDefaultTemplate(templates, "Artio", "https://oldschool.runescape.wiki/w/Artio/Strategies");
        addDefaultTemplate(templates, "Calvar'ion", "https://oldschool.runescape.wiki/w/Calvar%27ion/Strategies");
        addDefaultTemplate(templates, "Spindel", "https://oldschool.runescape.wiki/w/Spindel/Strategies");
        addDefaultTemplate(templates, "King Black Dragon (KBD)", "https://oldschool.runescape.wiki/w/King_Black_Dragon/Strategies");
        addDefaultTemplate(templates, "Nex", "https://oldschool.runescape.wiki/w/Nex/Strategies");
        addDefaultTemplate(templates, "Dagannoth Kings", "https://oldschool.runescape.wiki/w/Dagannoth_Kings/Strategies");
        addDefaultTemplate(templates, "Corporeal Beast", "https://oldschool.runescape.wiki/w/Corporeal_Beast/Strategies");
        addDefaultTemplate(templates, "Sarachnis", "https://oldschool.runescape.wiki/w/Sarachnis/Strategies");
        addDefaultTemplate(templates, "The Nightmare", "https://oldschool.runescape.wiki/w/The_Nightmare/Strategies");
        addDefaultTemplate(templates, "Hueycoatl", "https://oldschool.runescape.wiki/w/Hueycoatl/Strategies");
        addDefaultTemplate(templates, "Royal Titans", "https://oldschool.runescape.wiki/w/Royal_Titans/Strategies");
        addDefaultTemplate(templates, "Scurrius", "https://oldschool.runescape.wiki/w/Scurrius/Strategies");
        addDefaultTemplate(templates, "God Wars Dungeon", "https://oldschool.runescape.wiki/w/God_Wars_Dungeon");
        addDefaultTemplate(templates, "Wilderness bosses", "https://oldschool.runescape.wiki/w/Wilderness_bosses");
        return templates;
    }

    private static void addDefaultTemplate(List<LearnerTemplate> templates, String name, String wikiUrl)
    {
        LearnerTemplate template = new LearnerTemplate();
        template.id = UUID.randomUUID().toString(); template.name = name; template.title = name;
        template.strategyWikiUrl = wikiUrl; template.description = ""; template.discordDescription = ""; template.checklist = "";
        template.requiredPlugins = ""; template.youtubeUrl = "";
        templates.add(template);
    }

    private static <T> T read(HttpExchange exchange, Class<T> type) throws IOException
    {
        byte[] body = exchange.getRequestBody().readNBytes(32_769);
        if (body.length > 32_768) return null;
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
    private static String effectiveDropListUrl(Event event)
    { return event == null ? "" : event.dropListUrl == null ? event.driveUrl : event.dropListUrl; }

    private static String eventTeams(Event event)
    { return event != null && "CLAN_EVENT".equalsIgnoreCase(event.type) && event.teamNames != null ? event.teamNames : ""; }
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
    private static boolean validStandingsUrl(String value)
    {
        if (blank(value)) return true;
        if (!validOptional(value, 500)) return false;
        try
        {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) &&
                uri.getHost() != null && uri.getUserInfo() == null && uri.getPort() <= 65535;
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
            feed.announcementsVisible = !Boolean.FALSE.equals(state.announcementsVisible);
            feed.eventAnnouncementsUrl = state.eventAnnouncementsUrl;
            feed.dropChannelUrl = state.dropChannelUrl;
            feed.announcementsSequence = state.announcementsSequence;
            feed.learnerTemplates = new ArrayList<>();
            for (LearnerTemplate template : state.learnerTemplates)
                feed.learnerTemplates.add(GSON.fromJson(GSON.toJson(template), LearnerTemplate.class));
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
                visible.discordChannelId = "";
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
        synchronized void setDiscordMessage(String id, String messageId, String channelId)
        {
            Event event = event(id);
            if (event == null) return;
            event.discordMessageId = messageId;
            event.discordChannelId = channelId;
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
        synchronized LearnerTemplate learnerTemplate(String id)
        {
            return state.learnerTemplates.stream().filter(value -> id.equals(value.id)).findFirst().orElse(null);
        }
        synchronized void addLearnerTemplate(LearnerTemplate template)
        {
            state.learnerTemplates.add(template);
            changed();
        }
        synchronized boolean updateLearnerTemplate(String id, LearnerTemplate replacement)
        {
            for (int i = 0; i < state.learnerTemplates.size(); i++)
            {
                if (id.equals(state.learnerTemplates.get(i).id)) { state.learnerTemplates.set(i, replacement); changed(); return true; }
            }
            return false;
        }
        synchronized boolean deleteLearnerTemplate(String id)
        {
            boolean removed = state.learnerTemplates.removeIf(template -> id.equals(template.id));
            if (removed) changed();
            return removed;
        }
        synchronized void saveAnnouncementsUrl(String value)
        {
            state.announcementsUrl = value;
            changed();
        }
        synchronized void saveAnnouncementsVisible(boolean value)
        {
            state.announcementsVisible = value;
            changed();
        }
        synchronized void saveEventAnnouncementsUrl(String value)
        {
            state.eventAnnouncementsUrl = value;
            changed();
        }
        synchronized String eventAnnouncementsChannelId()
        {
            return discordChannelId(state.eventAnnouncementsUrl);
        }
        synchronized void saveDropChannelUrl(String value) { state.dropChannelUrl = value; changed(); }
        synchronized String dropChannelUrl() { return state.dropChannelUrl; }
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
        synchronized void transferOwner(String rsn, String tokenHash)
        {
            String previousOwner = normalize(state.ownerRsn);
            state.members.removeIf(member -> previousOwner.equals(normalize(member.rsn)));
            state.tokenHashes.remove(previousOwner);
            String key = normalize(rsn);
            Member member = state.members.stream().filter(value -> key.equals(normalize(value.rsn))).findFirst().orElse(null);
            if (member == null) { member = new Member(); member.rsn = rsn.trim(); state.members.add(member); }
            member.role = "OWNER"; member.updatedAt = OffsetDateTime.now(ZoneOffset.UTC).toString();
            state.ownerRsn = member.rsn;
            state.tokenHashes.put(key, tokenHash);
            changed();
        }
        synchronized boolean isOwner(String rsn) { return normalize(state.ownerRsn).equals(normalize(rsn)); }
        synchronized boolean bootstrapOwnerActive() { return INITIAL_OWNER_RSN.equals(normalize(state.ownerRsn)); }
        synchronized String ownerRsn() { return state.ownerRsn; }
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
            for (Member member : state.members)
            {
                Member visible = GSON.fromJson(GSON.toJson(member), Member.class);
                // Alleen de rollen-API voor beheer ziet deze status. De code zelf
                // blijft gehasht en wordt nooit teruggestuurd.
                visible.tokenActive = state.tokenHashes.containsKey(normalize(member.rsn));
                roles.add(visible);
            }
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
            OffsetDateTime cutoff = now.minusDays(7);
            boolean logsRemoved = removeLogsOlderThan(state.managementLogs, cutoff) | removeLogsOlderThan(state.errorLogs, cutoff);
            if (removed || logsRemoved) changed();
        }

        private static boolean removeLogsOlderThan(List<LogEntry> logs, OffsetDateTime cutoff)
        {
            if (logs == null) return false;
            return logs.removeIf(entry ->
            {
                try { return entry == null || entry.at == null || !OffsetDateTime.parse(entry.at).isAfter(cutoff); }
                catch (RuntimeException ex) { return true; }
            });
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
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS dutch_nations_role_tokens (rsn TEXT PRIMARY KEY, token_hash TEXT NOT NULL)");
                State loaded;
                try (ResultSet result = statement.executeQuery("SELECT payload FROM dutch_nations_state WHERE id = 1"))
                {
                    loaded = result.next() ? GSON.fromJson(result.getString(1), State.class) : new State();
                }
                if (loaded == null) loaded = new State();
                if (loaded.tokenHashes == null) loaded.tokenHashes = new HashMap<>();
                try (ResultSet result = statement.executeQuery("SELECT rsn, token_hash FROM dutch_nations_role_tokens"))
                {
                    while (result.next()) loaded.tokenHashes.put(result.getString(1), result.getString(2));
                }
                return loaded;
            }
            catch (Exception ex) { throw new IllegalStateException("Kan PostgreSQL-data niet lezen", ex); }
        }

        private void ensureOwner()
        {
            if (state.members == null) state.members = new ArrayList<>();
            if (state.events == null) state.events = new ArrayList<>();
            if (state.learnerTemplates == null) state.learnerTemplates = new ArrayList<>();
            if (state.learnerTemplates.isEmpty()) state.learnerTemplates.addAll(defaultLearnerTemplates());
            if (state.tokenHashes == null) state.tokenHashes = new HashMap<>();
            if (state.announcementsVisible == null) state.announcementsVisible = true;
            if (blank(state.ownerRsn)) state.ownerRsn = INITIAL_OWNER_RSN;
            String ownerKey = normalize(state.ownerRsn);
            Member owner = state.members.stream().filter(member -> ownerKey.equals(normalize(member.rsn))).findFirst().orElse(null);
            if (owner == null) { owner = new Member(); owner.rsn = state.ownerRsn; state.members.add(owner); }
            owner.role = "OWNER";
            if (blank(owner.updatedAt)) owner.updatedAt = OffsetDateTime.now(ZoneOffset.UTC).toString();
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
            try (Connection connection = connection())
            {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement())
                {
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS dutch_nations_state (id INTEGER PRIMARY KEY, payload TEXT NOT NULL)");
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS dutch_nations_role_tokens (rsn TEXT PRIMARY KEY, token_hash TEXT NOT NULL)");
                }
                try (PreparedStatement statement = connection.prepareStatement(sql))
                {
                    statement.setString(1, GSON.toJson(state));
                    statement.executeUpdate();
                }
                try (Statement statement = connection.createStatement())
                {
                    statement.executeUpdate("DELETE FROM dutch_nations_role_tokens");
                }
                try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO dutch_nations_role_tokens (rsn, token_hash) VALUES (?, ?)"))
                {
                    for (Map.Entry<String, String> token : state.tokenHashes.entrySet())
                    {
                        statement.setString(1, token.getKey());
                        statement.setString(2, token.getValue());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
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
        String ownerRsn;
        List<Member> members = new ArrayList<>();
        List<Event> events = new ArrayList<>();
        List<LearnerTemplate> learnerTemplates = new ArrayList<>();
        Map<String, String> tokenHashes = new HashMap<>();
        String announcementsUrl = "";
        Boolean announcementsVisible = true;
        String eventAnnouncementsUrl = "";
        String dropChannelUrl = "";
        long announcementsSequence;
        String lastAnnouncementMessageId = "";
        List<LogEntry> managementLogs = new ArrayList<>();
        List<LogEntry> errorLogs = new ArrayList<>();
    }
    static final class Feed { String updatedAt; String announcementsUrl; boolean announcementsVisible; String eventAnnouncementsUrl; String dropChannelUrl; long announcementsSequence; List<LearnerTemplate> learnerTemplates; List<Event> events; }
    static final class AnnouncementVisibility { boolean visible; }
    static final class OwnerLogs { List<LogEntry> management; List<LogEntry> errors; }
    static final class LogEntry { String at; String source; String message; }
    static final class Member { String rsn; String role; String updatedAt; Boolean tokenActive; }
    static final class Event
    {
        String id; String startsAt; String endsAt; String type; String title;
        String world; String host; String description; String codeword; String checklist; String requiredPlugins; String strategyWikiUrl; String driveUrl; String dropListUrl; String registrationUrl; String registrationEndsAt; String youtubeUrl; String discordText; String discordDescription; String discordMessageId; String discordChannelId;
        String clansOne; String clansTwo; String activity; String bossList; String dropItems; String dropChannelUrl; String dropSource; String teamNames;
        boolean codewordRequired;
        boolean allowConflict;
    }
    static final class LearnerTemplate
    {
        String id; String name; String title; String description; String discordDescription; String checklist;
        String requiredPlugins; String strategyWikiUrl; String youtubeUrl;
    }
    static final class RoleChange { String rsn; String role; }
    static final class AnnouncementChannel { String url; }
    static final class DropTestReport { String eventId; String rsn; String item; int quantity; String screenshot; }
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

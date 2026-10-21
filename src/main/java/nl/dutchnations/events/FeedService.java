package nl.dutchnations.events;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

final class FeedService
{
    private static final String PRODUCTION_URL = "https://dutch-nations-events.onrender.com";
    private static final String LOCAL_URL = "http://127.0.0.1:8787";
    private static final String API_BASE_URL = Boolean.getBoolean("dutch.nations.local") ? LOCAL_URL : PRODUCTION_URL;
    static final String FEED_URL = API_BASE_URL + "/feed.json";
    static final String EVENTS_URL = API_BASE_URL + "/api/events";
    static final String ROLES_URL = API_BASE_URL + "/api/roles";
    static final String ANNOUNCEMENT_CHANNEL_URL = API_BASE_URL + "/api/announcement-channel";
    static final String ANNOUNCEMENT_VISIBILITY_URL = API_BASE_URL + "/api/announcement-visibility";
    static final String EVENT_ANNOUNCEMENT_CHANNEL_URL = API_BASE_URL + "/api/event-announcement-channel";
    static final String DROP_CHANNEL_URL = API_BASE_URL + "/api/drop-channel";
    static final String DROP_TEST_URL = API_BASE_URL + (Boolean.getBoolean("dutch.nations.local") ? "/api/local-drop-test" : "/api/event-drops");
    static final String DROP_LIST_URL = API_BASE_URL + "/api/event-drop-list";
    static final String OWNER_LOGS_URL = API_BASE_URL + "/api/owner-logs";
    static final String LEARNER_TEMPLATES_URL = API_BASE_URL + "/api/learner-templates";
    interface Listener { void success(ClanFeed feed, String json); void failure(String message); }
    interface SaveListener { void success(); void failure(String message); }
    interface DropTestListener { void success(boolean queued); void failure(String message); }
    interface DropListListener { void success(String source, String items); void failure(String message); }
    interface RoleSaveListener { void success(String managementToken); void failure(String message); }
    interface RoleStatusListener { void success(String rsn, String role); void failure(); }
    interface RolesListener { void success(List<ManagementRole> roles); void failure(String message); }
    interface OwnerLogsListener { void success(OwnerLogsResponse logs); void failure(String message); }
    interface AnnouncementChannelListener { void success(String url); void failure(String message); }
    interface AnnouncementVisibilityListener { void success(boolean visible); void failure(String message); }
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient client;
    private final Gson gson;

    static final class DropTestReport
    {
        String eventId; String rsn; String item; int quantity; String screenshot;
    }

    FeedService(OkHttpClient client, Gson gson) { this.client = client; this.gson = gson; }

    void fetch(String url, Listener listener)
    {
        HttpUrl parsed = https(url);
        if (parsed == null) { listener.failure("Ongeldig HTTPS-adres voor de management-feed."); return; }
        if (isExample(url)) { loadBundledFeed(listener); return; }
        client.newCall(new Request.Builder().url(parsed).header("Cache-Control", "no-cache").build()).enqueue(new Callback()
        {
            @Override public void onFailure(@javax.annotation.Nonnull Call call, @javax.annotation.Nonnull IOException e) { listener.failure("Feed niet bereikbaar."); }
            @Override public void onResponse(@javax.annotation.Nonnull Call call, @javax.annotation.Nonnull Response response) throws IOException
            {
                try (Response ignored = response)
                {
                    if (!response.isSuccessful() || response.body() == null) { listener.failure("Feed gaf HTTP " + response.code() + "."); return; }
                    String json = response.body().string(); ClanFeed feed = parse(json);
                    if (feed == null) listener.failure("Feed bevat ongeldige gegevens."); else listener.success(feed, json);
                }
            }
        });
    }

    private void loadBundledFeed(Listener listener)
    {
        try (InputStream stream = FeedService.class.getResourceAsStream("/example-feed.json"))
        {
            if (stream == null) { listener.failure("Ingebouwde testfeed ontbreekt."); return; }
            StringBuilder json = new StringBuilder();
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
            {
                char[] buffer = new char[2048]; int count;
                while ((count = reader.read(buffer)) != -1) json.append(buffer, 0, count);
            }
            ClanFeed feed = parse(json.toString());
            if (feed == null) listener.failure("Ingebouwde testfeed is ongeldig."); else listener.success(feed, json.toString());
        }
        catch (IOException e) { listener.failure("Ingebouwde testfeed kon niet worden gelezen."); }
    }

    void createEvent(String url, String token, EventDraft event, SaveListener listener)
    {
        if (isExample(url)) { listener.failure("Testfeed actief: pas feed.json aan om een event toe te voegen."); return; }
        post(url, token, gson.toJson(event), "Event", listener);
    }

    void updateEvent(String url, String token, String eventId, EventDraft event, SaveListener listener)
    {
        if (isExample(url)) { listener.failure("Testfeed actief: pas feed.json aan om een event te wijzigen."); return; }
        HttpUrl base = https(url);
        if (base == null) { listener.failure("Event-API-adres ontbreekt of is ongeldig."); return; }
        if (blank(token)) { listener.failure("Management-token ontbreekt. Vul deze opnieuw in bij de plugininstellingen."); return; }
        if (blank(eventId)) { listener.failure("Event-id ontbreekt; vernieuw eerst de kalender."); return; }
        HttpUrl target = base.newBuilder().addPathSegment(eventId).build();
        Request request = new Request.Builder().url(target).header("Authorization", "Bearer " + token.trim())
            .put(RequestBody.create(JSON, gson.toJson(event))).build();
        execute(request, "Event", listener);
    }

    void deleteEvent(String url, String token, String eventId, SaveListener listener)
    {
        if (isExample(url)) { listener.failure("GitHub/testfeed actief: verwijder het event uit feed.json."); return; }
        HttpUrl base = https(url);
        if (base == null) { listener.failure("Event-API-adres ontbreekt of is ongeldig."); return; }
        if (blank(token)) { listener.failure("Management-token ontbreekt. Vul deze opnieuw in bij de plugininstellingen."); return; }
        if (blank(eventId)) { listener.failure("Event-id ontbreekt; vernieuw eerst de kalender."); return; }
        HttpUrl target = base.newBuilder().addPathSegment(eventId).build();
        Request request = new Request.Builder().url(target).header("Authorization", "Bearer " + token.trim()).delete().build();
        execute(request, "Event", listener);
    }

    void createLearnerTemplate(String token, ClanFeed.LearnerTemplate template, SaveListener listener)
    {
        post(LEARNER_TEMPLATES_URL, token, gson.toJson(template), "Learner-sjabloon", listener);
    }

    void updateLearnerTemplate(String token, String templateId, ClanFeed.LearnerTemplate template, SaveListener listener)
    {
        put(LEARNER_TEMPLATES_URL, token, templateId, gson.toJson(template), "Learner-sjabloon", listener);
    }

    void deleteLearnerTemplate(String token, String templateId, SaveListener listener)
    {
        HttpUrl base = https(LEARNER_TEMPLATES_URL);
        if (base == null || blank(token) || blank(templateId)) { listener.failure("Owner-token of sjabloon ontbreekt."); return; }
        Request request = new Request.Builder().url(base.newBuilder().addPathSegment(templateId).build())
            .header("Authorization", "Bearer " + token.trim()).delete().build();
        execute(request, "Learner-sjabloon", listener);
    }

    void saveAnnouncementChannel(String url, String token, AnnouncementChannelListener listener)
    {
        saveDiscordChannel(ANNOUNCEMENT_CHANNEL_URL, url, token, 0, listener);
    }
    void saveEventAnnouncementChannel(String url, String token, AnnouncementChannelListener listener)
    {
        saveDiscordChannel(EVENT_ANNOUNCEMENT_CHANNEL_URL, url, token, 1, listener);
    }
    void saveDropChannel(String url, String token, AnnouncementChannelListener listener)
    {
        saveDiscordChannel(DROP_CHANNEL_URL, url, token, 2, listener);
    }
    void testDrop(DropTestReport report, DropTestListener listener)
    {
        HttpUrl target = https(DROP_TEST_URL);
        if (target == null) { listener.failure("Screenshotserver is niet beschikbaar."); return; }
        Request request = new Request.Builder().url(target).post(RequestBody.create(JSON, gson.toJson(report))).build();
        client.newBuilder().readTimeout(45, java.util.concurrent.TimeUnit.SECONDS).build().newCall(request).enqueue(new Callback()
        {
            @Override public void onFailure(@javax.annotation.Nonnull Call call, @javax.annotation.Nonnull IOException e) { listener.failure("Screenshotserver is niet bereikbaar."); }
            @Override public void onResponse(@javax.annotation.Nonnull Call call, @javax.annotation.Nonnull Response response) throws IOException
            {
                try (Response ignored = response)
                {
                    String body = response.body() == null ? "" : response.body().string();
                    if (!response.isSuccessful())
                    {
                        ApiError error = gson.fromJson(body, ApiError.class);
                        listener.failure(error != null && !blank(error.error) ? error.error : "Dropscreenshot gaf HTTP " + response.code());
                        return;
                    }
                    DropTestResponse result = gson.fromJson(body, DropTestResponse.class);
                    listener.success(result != null && (Boolean.getBoolean("dutch.nations.local") ? result.discordQueued : result.delivered));
                }
            }
        });
    }
    void fetchSheetDrops(String eventId, DropListListener listener)
    {
        HttpUrl base = https(DROP_LIST_URL);
        if (base == null || blank(eventId))
        { listener.failure("Droplijst vereist een actief event."); return; }
        HttpUrl url = base.newBuilder().addQueryParameter("eventId", eventId).build();
        Request request = new Request.Builder().url(url).get().build();
        client.newCall(request).enqueue(new Callback()
        {
            @Override public void onFailure(@javax.annotation.Nonnull Call call, @javax.annotation.Nonnull IOException ex) { listener.failure("Google-droplijst niet bereikbaar."); }
            @Override public void onResponse(@javax.annotation.Nonnull Call call, @javax.annotation.Nonnull Response response) throws IOException
            {
                try (Response ignored = response)
                {
                    if (!response.isSuccessful() || response.body() == null)
                    { listener.failure("Google-droplijst kon niet worden geladen; controleer de server en leesrechten."); return; }
                    try
                    {
                        DropListResponse result = gson.fromJson(response.body().string(), DropListResponse.class);
                        if (result == null || !(GoogleSheetLink.supported(result.source) || "PVM_WAR_2026".equals(result.source)) || result.items == null
                            || result.items.size() > 500 || result.items.stream().anyMatch(item -> blank(item) || item.contains(";")))
                        { listener.failure("Google-droplijst bevat ongeldige gegevens."); return; }
                        listener.success(result.source, String.join(";", result.items));
                    }
                    catch (RuntimeException ex) { listener.failure("Droplijst bevat ongeldige gegevens."); }
                }
            }
        });
    }
    private static final class DropListResponse { String source; List<String> items; }
    private static final class DropTestResponse { boolean delivered; boolean discordQueued; }
    void saveAnnouncementsVisibility(boolean visible, String token, AnnouncementVisibilityListener listener)
    {
        HttpUrl parsed = https(ANNOUNCEMENT_VISIBILITY_URL);
        if (parsed == null || blank(token)) { listener.failure("Owner-token ontbreekt."); return; }
        Request request = new Request.Builder().url(parsed).header("Authorization", "Bearer " + token.trim())
            .post(RequestBody.create(JSON, gson.toJson(new AnnouncementVisibility(visible)))).build();
        client.newCall(request).enqueue(new Callback()
        {
            @Override public void onFailure(@javax.annotation.Nonnull Call call, @javax.annotation.Nonnull IOException e) { listener.failure("Mededelingenknop is niet opgeslagen: server niet bereikbaar."); }
            @Override public void onResponse(@javax.annotation.Nonnull Call call, @javax.annotation.Nonnull Response response) throws IOException
            {
                try (Response ignored = response)
                {
                    String body = response.body() == null ? "" : response.body().string();
                    if (!response.isSuccessful())
                    {
                        ApiError error = gson.fromJson(body, ApiError.class);
                        listener.failure(error != null && !blank(error.error) ? error.error : "Opslaan gaf HTTP " + response.code() + ".");
                        return;
                    }
                    AnnouncementVisibilityResponse saved = gson.fromJson(body, AnnouncementVisibilityResponse.class);
                    if (saved == null || saved.announcementsVisible == null) { listener.failure("Server bevestigde geen zichtbaarheidsinstelling."); return; }
                    listener.success(saved.announcementsVisible);
                }
            }
        });
    }
    private void saveDiscordChannel(String endpoint, String url, String token, int channelType, AnnouncementChannelListener listener)
    {
        HttpUrl parsed = https(endpoint);
        if (parsed == null || blank(token)) { listener.failure("Owner-token ontbreekt."); return; }
        Request request = new Request.Builder().url(parsed).header("Authorization", "Bearer " + token.trim())
            .post(RequestBody.create(JSON, gson.toJson(new AnnouncementChannel(url)))).build();
        client.newCall(request).enqueue(new Callback()
        {
            @Override public void onFailure(@javax.annotation.Nonnull Call call, @javax.annotation.Nonnull IOException e) { listener.failure("Mededelingenkanaal is niet opgeslagen: server niet bereikbaar."); }
            @Override public void onResponse(@javax.annotation.Nonnull Call call, @javax.annotation.Nonnull Response response) throws IOException
            {
                try (Response ignored = response)
                {
                    String body = response.body() == null ? "" : response.body().string();
                    if (!response.isSuccessful())
                    {
                        ApiError error = gson.fromJson(body, ApiError.class);
                        listener.failure(error != null && !blank(error.error) ? error.error : "Opslaan gaf HTTP " + response.code() + ".");
                        return;
                    }
                    AnnouncementChannelResponse saved = gson.fromJson(body, AnnouncementChannelResponse.class);
                    String savedUrl = saved == null ? "" : (channelType == 2 ? saved.dropChannelUrl : channelType == 1 ? saved.eventAnnouncementsUrl : saved.announcementsUrl);
                    if (blank(savedUrl)) { listener.failure("Server bevestigde geen kanaalinstelling."); return; }
                    listener.success(savedUrl);
                }
            }
        });
    }
    void fetchRole(String url, String token, RoleStatusListener listener)
    {
        HttpUrl parsed = https(url);
        if (parsed == null || blank(token)) { listener.failure(); return; }
        Request request = new Request.Builder().url(parsed).header("Authorization", "Bearer " + token.trim()).get().build();
        client.newCall(request).enqueue(new Callback()
        {
            @Override public void onFailure(@javax.annotation.Nonnull Call call, @javax.annotation.Nonnull IOException e) { listener.failure(); }
            @Override public void onResponse(@javax.annotation.Nonnull Call call, @javax.annotation.Nonnull Response response) throws IOException
            {
                try (Response ignored = response)
                {
                    if (!response.isSuccessful() || response.body() == null) { listener.failure(); return; }
                    RoleResponse saved = gson.fromJson(response.body().string(), RoleResponse.class);
                    if (saved == null || blank(saved.role)) listener.failure(); else listener.success(saved.rsn, saved.role);
                }
            }
        });
    }
    void fetchRoles(String url, String token, RolesListener listener)
    {
        HttpUrl parsed = https(url);
        if (parsed == null || blank(token)) { listener.failure("Rollen-API of owner-token ontbreekt."); return; }
        HttpUrl target = parsed.newBuilder().addQueryParameter("all", "true").build();
        Request request = new Request.Builder().url(target).header("Authorization", "Bearer " + token.trim()).get().build();
        client.newCall(request).enqueue(new Callback()
        {
            @Override public void onFailure(@javax.annotation.Nonnull Call call, @javax.annotation.Nonnull IOException e) { listener.failure("Rollenoverzicht niet bereikbaar."); }
            @Override public void onResponse(@javax.annotation.Nonnull Call call, @javax.annotation.Nonnull Response response) throws IOException
            {
                try (Response ignored = response)
                {
                    if (!response.isSuccessful() || response.body() == null)
                    { listener.failure("Rollenoverzicht gaf HTTP " + response.code() + "."); return; }
                    RolesResponse saved = gson.fromJson(response.body().string(), RolesResponse.class);
                    if (saved == null || saved.roles == null) listener.failure("Ongeldig rollenoverzicht.");
                    else listener.success(saved.roles);
                }
            }
        });
    }
    void fetchOwnerLogs(String token, OwnerLogsListener listener)
    {
        HttpUrl parsed = https(OWNER_LOGS_URL);
        if (parsed == null || blank(token)) { listener.failure("Owner-token ontbreekt."); return; }
        Request request = new Request.Builder().url(parsed).header("Authorization", "Bearer " + token.trim()).get().build();
        client.newCall(request).enqueue(new Callback()
        {
            @Override public void onFailure(@javax.annotation.Nonnull Call call, @javax.annotation.Nonnull IOException e) { listener.failure("Logs niet bereikbaar."); }
            @Override public void onResponse(@javax.annotation.Nonnull Call call, @javax.annotation.Nonnull Response response) throws IOException
            {
                try (Response ignored = response)
                {
                    if (!response.isSuccessful() || response.body() == null) { listener.failure("Logs gaven HTTP " + response.code() + "."); return; }
                    OwnerLogsResponse logs = gson.fromJson(response.body().string(), OwnerLogsResponse.class);
                    if (logs == null) listener.failure("Ongeldige loggegevens."); else listener.success(logs);
                }
            }
        });
    }
    void saveRole(String url, String token, RoleDraft role, RoleSaveListener listener)
    {
        HttpUrl parsed = https(url);
        if (parsed == null || blank(token)) { listener.failure("Rollen-API of persoonlijke management-token ontbreekt."); return; }
        Request request = new Request.Builder().url(parsed).header("Authorization", "Bearer " + token.trim())
            .post(RequestBody.create(JSON, gson.toJson(role))).build();
        client.newCall(request).enqueue(new Callback()
        {
            @Override public void onFailure(@javax.annotation.Nonnull Call call, @javax.annotation.Nonnull IOException e) { listener.failure("Rol kon niet worden opgeslagen."); }
            @Override public void onResponse(@javax.annotation.Nonnull Call call, @javax.annotation.Nonnull Response response) throws IOException
            {
                try (Response ignored = response)
                {
                    if (response.isSuccessful())
                    {
                        String body = response.body() == null ? "{}" : response.body().string();
                        RoleResponse saved = gson.fromJson(body, RoleResponse.class);
                        listener.success(saved == null ? null : saved.managementToken);
                    }
                    else if (response.code() == 401 || response.code() == 403) listener.failure("Geen toestemming om deze rolwijziging uit te voeren.");
                    else listener.failure("Rol opslaan gaf HTTP " + response.code() + ".");
                }
            }
        });
    }

    private void post(String url, String token, String json, String subject, SaveListener listener)
    {
        HttpUrl parsed = https(url);
        if (parsed == null) { listener.failure("Event-API-adres ontbreekt of is ongeldig."); return; }
        if (blank(token)) { listener.failure("Management-token ontbreekt. Vul deze opnieuw in bij de plugininstellingen."); return; }
        Request request = new Request.Builder().url(parsed).header("Authorization", "Bearer " + token.trim())
            .post(RequestBody.create(JSON, json)).build();
        execute(request, subject, listener);
    }

    private void put(String url, String token, String id, String json, String subject, SaveListener listener)
    {
        HttpUrl base = https(url);
        if (base == null || blank(token) || blank(id)) { listener.failure(subject + " mist gegevens."); return; }
        Request request = new Request.Builder().url(base.newBuilder().addPathSegment(id).build()).header("Authorization", "Bearer " + token.trim())
            .put(RequestBody.create(JSON, json)).build();
        execute(request, subject, listener);
    }

    private void execute(Request request, String subject, SaveListener listener)
    {
        client.newCall(request).enqueue(new Callback()
        {
            @Override public void onFailure(@javax.annotation.Nonnull Call call, @javax.annotation.Nonnull IOException e) { listener.failure(subject + " kon niet worden opgeslagen."); }
            @Override public void onResponse(@javax.annotation.Nonnull Call call, @javax.annotation.Nonnull Response response)
            {
                try (Response ignored = response)
                {
                    if (response.isSuccessful()) listener.success();
                    else if (response.code() == 401 || response.code() == 403) listener.failure("Geen toestemming: controleer je rol en token.");
                    else if (response.code() == 409) listener.failure("Dit event overlapt met een bestaand event.");
                    else listener.failure("Actie gaf HTTP " + response.code() + ".");
                }
            }
        });
    }

    private static final class AnnouncementChannel
    {
        final String url;
        AnnouncementChannel(String url) { this.url = url; }
    }
    private static final class AnnouncementVisibility { final boolean visible; AnnouncementVisibility(boolean visible) { this.visible = visible; } }
    private static final class AnnouncementVisibilityResponse { Boolean announcementsVisible; }
    private static final class AnnouncementChannelResponse { String announcementsUrl; String eventAnnouncementsUrl; String dropChannelUrl; }
    private static final class ApiError { String error; }
    private static final class RolesResponse
    {
        List<ManagementRole> roles;
    }
    static final class OwnerLogsResponse { List<OwnerLogEntry> management; List<OwnerLogEntry> errors; }
    static final class OwnerLogEntry { String at; String source; String message; }
    private static final class RoleResponse
    {
        String managementToken;
        String rsn;
        String role;
    }

    ClanFeed parse(String json)
    {
        try
        {
            ClanFeed feed = gson.fromJson(json, ClanFeed.class); OffsetDateTime.parse(feed.updatedAt);
            if (feed.events == null) return null;
            if (feed.learnerTemplates == null) feed.learnerTemplates = new java.util.ArrayList<>();
            for (ClanFeed.LearnerTemplate template : feed.learnerTemplates)
            {
                if (template == null || blank(template.id) || blank(template.name) || blank(template.title) ||
                    unsafe(template.name) || unsafe(template.title) || unsafe(template.description) ||
                    unsafe(template.discordDescription) || unsafe(template.checklist) || unsafe(template.requiredPlugins) || unsafe(template.strategyWikiUrl) ||
                    unsafe(template.youtubeUrl)) return null;
            }
            for (ClanFeed.ClanEvent event : feed.events)
            {
                boolean learner = event.learner();
                boolean boss = "BOSS".equalsIgnoreCase(event.type);
                boolean mass = "MASS".equalsIgnoreCase(event.type);
                boolean clanEvent = "CLAN_EVENT".equalsIgnoreCase(event.type);
                boolean clanVsClan = "CLAN_VS_CLAN".equalsIgnoreCase(event.type);
                if (!(learner || boss || mass || clanEvent || clanVsClan)) return null;
                boolean needsWorld = learner || mass || clanEvent;
                if (blank(event.id) || blank(event.title) || (needsWorld && blank(event.world)) ||
                    !event.end().isAfter(event.start()) || unsafe(event.title) || unsafe(event.world) ||
                    unsafe(event.host) || unsafe(event.description) || unsafe(event.codeword) || unsafe(event.checklist) ||
                    unsafe(event.requiredPlugins) || unsafe(event.driveUrl) || unsafe(event.dropListUrl) || unsafe(event.registrationUrl) || unsafe(event.registrationEndsAt) || unsafe(event.youtubeUrl) ||
                    unsafe(event.clansOne) || unsafe(event.clansTwo) || unsafe(event.activity) || unsafe(event.bossList) ||
                    unsafe(event.dropItems) || unsafe(event.dropChannelUrl) || unsafe(event.dropSource)) return null;
                if (!blank(event.registrationEndsAt)) { try { OffsetDateTime.parse(event.registrationEndsAt); } catch (RuntimeException ignored) { return null; } }
                if ((boss || clanEvent || clanVsClan || (mass && event.codewordRequired)) && !event.active(OffsetDateTime.now())) event.codeword = "";
            }
            return feed;
        }
        catch (RuntimeException e) { return null; }
    }

    private static boolean isExample(String value)
    {
        HttpUrl parsed = https(value); return parsed != null && "example.com".equalsIgnoreCase(parsed.host());
    }
    private static HttpUrl https(String value)
    {
        HttpUrl parsed = HttpUrl.parse(value == null ? "" : value.trim());
        if (parsed == null) return null;
        if ("https".equals(parsed.scheme())) return parsed;
        boolean localHttp = "http".equals(parsed.scheme()) &&
            ("127.0.0.1".equals(parsed.host()) || "localhost".equalsIgnoreCase(parsed.host()));
        return localHttp ? parsed : null;
    }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static boolean unsafe(String value)
    {
        return value != null && (value.contains("<") || value.contains(">") ||
            value.chars().anyMatch(character -> Character.isISOControl(character) && character != '\n' && character != '\r' && character != '\t'));
    }
}

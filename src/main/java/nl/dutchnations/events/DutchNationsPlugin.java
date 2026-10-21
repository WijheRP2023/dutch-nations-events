package nl.dutchnations.events;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ExternalPluginsChanged;
import net.runelite.client.externalplugins.ExternalPluginClient;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.externalplugins.PluginHubManifest;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import okhttp3.OkHttpClient;

@PluginDescriptor(name = "Dutch Nation Events", description = "Clan eventkalender Dutch Nation")
public class DutchNationsPlugin extends Plugin
{
    private static final String CACHE = "cachedFeed";
    private static final String ANNOUNCEMENT_READ_SEQUENCE = "announcementReadSequence";
    private static final String DUTCH_NATION_CLAN = "Dutch Nation";
    private static final DateTimeFormatter CHAT_DATE_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm", new java.util.Locale("nl", "NL"));
    @Inject private Client client;
    @Inject private ChatMessageManager chatMessages;
    @Inject private ClientToolbar toolbar;
    @Inject private OverlayManager overlays;
    @Inject private CodewordOverlay overlay;
    @Inject @SuppressWarnings("unused") private DutchNationsConfig config;
    @Inject private ConfigManager configs;
    @Inject private OkHttpClient http;
    @Inject private Gson gson;
    @Inject private ExternalPluginClient pluginHubClient;
    @Inject private ExternalPluginManager externalPluginManager;
    @Inject private ChatIconManager chatIconManager;
    @Inject private ItemManager itemManager;
    @Inject private DrawManager drawManager;
    @Inject private ScheduledExecutorService executor;
    private FeedService service;
    private WomCompetitionService womService;
    private DutchNationsPanel panel;
    private NavigationButton button;
    private volatile ClanFeed feed;
    private volatile String authenticatedRole = "";
    private final Set<String> remindedEvents = new HashSet<>();
    private final Set<String> announcedEvents = new HashSet<>();
    private final Queue<ScheduleChange> pendingScheduleChanges = new ConcurrentLinkedQueue<>();
    private volatile boolean receivedLiveFeed;
    private int ticksUntilRefresh;
    private int ticksUntilClanRefresh;
    private int ticksUntilRoleRefresh;
    private int remainingRoleRefreshAttempts;
    private String clanMembersSignature = "";
    private long lastWomRefresh;
    private final Map<String, SheetDropSnapshot> sheetDrops = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Long> nextSheetRefresh = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<String> sheetRefreshInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final class SheetDropSnapshot
    {
        final String items; final long fetchedAt;
        SheetDropSnapshot(String items, long fetchedAt) { this.items = items; this.fetchedAt = fetchedAt; }
    }

    @Provides @SuppressWarnings("unused") DutchNationsConfig config(ConfigManager manager) { return manager.getConfig(DutchNationsConfig.class); }

    @Override protected void startUp()
    {
        service = new FeedService(http, gson);
        womService = new WomCompetitionService(http, gson);
        panel = new DutchNationsPanel(config, this::refresh, this::canManage, this::isOwner,
            this::isAdministrator, this::isLearnerHost, this::canManageRoles, this::fetchRoles, this::fetchOwnerLogs, this::hasClanAccess,
            this::createEvent, this::updateEvent, this::deleteEvent, this::createLearnerTemplate, this::updateLearnerTemplate, this::deleteLearnerTemplate, this::saveRole, this::saveAnnouncementChannel, this::saveEventAnnouncementChannel, this::saveDropChannel, this::saveAnnouncementsVisibility, this::announcementReadSequence, this::markAnnouncementsRead, this::localPlayerName);
        ClanFeed cached = service.parse(configs.getConfiguration(DutchNationsConfig.GROUP, CACHE));
        if (cached != null) { feed = cached; panel.update(cached, "Opgeslagen versie; update wordt gecontroleerd."); }
        button = NavigationButton.builder().tooltip("Dutch Nation").icon(icon()).priority(6).panel(panel).build();
        toolbar.addNavigation(button); overlays.add(overlay); loadPluginCatalog(); scheduleRoleRefresh(); refresh();
    }

    private void loadPluginCatalog()
    {
        new SwingWorker<java.util.List<String>, Void>()
        {
            private java.util.Set<String> installedNames = java.util.Collections.emptySet();

            @Override protected java.util.List<String> doInBackground() throws Exception
            {
                PluginHubManifest.ManifestFull manifest = pluginHubClient.downloadManifestFull();
                java.util.Set<String> installedInternal = new java.util.HashSet<>(externalPluginManager.getInstalledExternalPlugins());
                installedNames = manifest.getDisplay().stream()
                    .filter(data -> installedInternal.contains(data.getInternalName()))
                    .map(PluginHubManifest.DisplayData::getDisplayName)
                    .collect(java.util.stream.Collectors.toSet());
                return manifest.getDisplay().stream()
                    .map(PluginHubManifest.DisplayData::getDisplayName)
                    .filter(name -> name != null && !name.trim().isEmpty())
                    .distinct().sorted(String.CASE_INSENSITIVE_ORDER).collect(java.util.stream.Collectors.toList());
            }

            @Override protected void done()
            {
                try { if (panel != null) panel.updatePluginCatalog(get(), installedNames); }
                catch (Exception ignored)
                {
                    if (panel != null) panel.updatePluginCatalog(java.util.Collections.emptyList(), java.util.Collections.emptySet());
                }
            }
        }.execute();
    }

    @Override protected void shutDown()
    {
        overlays.remove(overlay); toolbar.removeNavigation(button); feed = null; authenticatedRole = ""; panel = null; service = null; womService = null;
        sheetDrops.clear(); nextSheetRefresh.clear(); sheetRefreshInFlight.clear();
    }

    @Subscribe @SuppressWarnings("unused") public void onExternalPluginsChanged(ExternalPluginsChanged ignored)
    {
        loadPluginCatalog();
    }

    @Subscribe @SuppressWarnings("unused") public void onConfigChanged(ConfigChanged e)
    {
        if (DutchNationsConfig.GROUP.equals(e.getGroup()) && !CACHE.equals(e.getKey()))
        {
            if ("managementToken".equals(e.getKey())) refreshRole();
            refresh();
        }
    }

    @Subscribe @SuppressWarnings("unused") public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN) scheduleRoleRefresh();
        else if (event.getGameState() == GameState.LOGIN_SCREEN)
        {
            ticksUntilRoleRefresh = 0;
            remainingRoleRefreshAttempts = 0;
            authenticatedRole = "";
            if (panel != null) SwingUtilities.invokeLater(panel::permissionsChanged);
        }
    }

    @Subscribe
    @SuppressWarnings("unused") public void onGameTick(GameTick ignored)
    {
        refreshRoleWhenPlayerIsReady();
        if (--ticksUntilClanRefresh <= 0)
        {
            ticksUntilClanRefresh = 10;
            updateOnlineClanMembers();
        }
        if (!hasClanAccess()) return;
        ClanFeed current = feed;
        if (current == null) return;
        if (isOwner() && config.shareDropScreenshots()) refreshSheetDrops(current);
        if (--ticksUntilRefresh <= 0)
        {
            ticksUntilRefresh = 25;
            refreshWom(false);
            refresh(false);
        }
        ScheduleChange change;
        while ((change = pendingScheduleChanges.poll()) != null)
            if (shouldNotify(change.updated)) queueScheduleChangeMessage(change.previous, change.updated);
        OffsetDateTime now = OffsetDateTime.now();
        for (ClanFeed.ClanEvent event : current.events)
        {
            long secondsUntilStart = Duration.between(now, event.start()).getSeconds();
            if (shouldNotify(event) && secondsUntilStart > 0 && secondsUntilStart <= config.reminderMinutes() * 60L && remindedEvents.add(notificationKey(event)))
            {
                long minutes = Math.max(1, (secondsUntilStart + 59) / 60);
                queueEventMessage(event, "start over " + minutes + (minutes == 1 ? " minuut" : " minuten"));
            }
            if (config.notifyAtStart() && shouldNotify(event) && event.active(now) && announcedEvents.add(notificationKey(event)))
            {
                queueEventMessage(event, "is nu gestart");
            }
        }
    }

    @Subscribe @SuppressWarnings("unused") public void onLootReceived(LootReceived loot)
    {
        if (!isOwner() || !config.shareDropScreenshots() || !hasClanAccess()) return;
        ClanFeed current = feed;
        Player player = client.getLocalPlayer();
        if (current == null || player == null || player.getName() == null || loot.getItems() == null) return;
        String rsn = player.getName();
        OffsetDateTime now = OffsetDateTime.now();
        for (ItemStack item : loot.getItems())
        {
            if (item.getId() < 0 || item.getQuantity() <= 0) continue;
            String name = itemManager.getItemComposition(itemManager.canonicalize(item.getId())).getMembersName();
            for (ClanFeed.ClanEvent event : current.events)
                if (event.id != null && DropMatcher.matches(event, selectedDropItems(event), name, now))
                    captureLocalDrop(event.id, rsn, name, item.getQuantity());
        }
    }

    private static String dynamicDropSource(ClanFeed.ClanEvent event)
    {
        String dropListUrl = event.dropListUrl == null ? event.driveUrl : event.dropListUrl;
        if (GoogleSheetLink.supported(dropListUrl)) return dropListUrl;
        return "PVM_WAR_2026".equals(event.dropSource) ? event.dropSource : null;
    }
    private static String sheetKey(ClanFeed.ClanEvent event) { return event.id + "\n" + dynamicDropSource(event); }

    private String selectedDropItems(ClanFeed.ClanEvent event)
    {
        String source = dynamicDropSource(event);
        if (source == null) return event.dropItems;
        SheetDropSnapshot snapshot = sheetDrops.get(sheetKey(event));
        if (snapshot == null || System.currentTimeMillis() - snapshot.fetchedAt > 120000)
            return "PVM_WAR_2026".equals(source) ? event.dropItems : null;
        return snapshot.items;
    }

    private void refreshSheetDrops(ClanFeed current)
    {
        FeedService activeService = service;
        if (activeService == null) return;
        long nowMillis = System.currentTimeMillis();
        for (ClanFeed.ClanEvent event : current.events)
        {
            String source = dynamicDropSource(event);
            if (event.id == null || source == null || !event.active(OffsetDateTime.now())) continue;
            String key = sheetKey(event);
            if (nowMillis < nextSheetRefresh.getOrDefault(key, 0L) || !sheetRefreshInFlight.add(key)) continue;
            nextSheetRefresh.put(key, nowMillis + 30000);
            activeService.fetchSheetDrops(event.id, config.managementToken(), new FeedService.DropListListener()
            {
                @Override public void success(String source, String items)
                {
                    sheetRefreshInFlight.remove(key);
                    if (service == activeService && source.equals(dynamicDropSource(event)))
                        sheetDrops.put(key, new SheetDropSnapshot(items, System.currentTimeMillis()));
                }
                @Override public void failure(String message)
                {
                    sheetRefreshInFlight.remove(key); sheetDrops.remove(key);
                    if (service == activeService && GoogleSheetLink.supported(source)) showDropTestStatus(message);
                }
            });
        }
    }

    private void captureLocalDrop(String eventId, String rsn, String name, int quantity)
    {
        drawManager.requestNextFrameListener(image ->
        {
            if (!config.shareDropScreenshots() || !activeDropEvent(eventId) || image == null || image.getWidth(null) <= 0 || image.getHeight(null) <= 0) return;
            BufferedImage copy = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = copy.createGraphics();
            try { graphics.drawImage(image, 0, 0, null); } finally { graphics.dispose(); }
            executor.execute(() ->
            {
                if (!config.shareDropScreenshots() || !activeDropEvent(eventId)) return;
                try
                {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    if (!ImageIO.write(copy, "png", bytes) || bytes.size() > 4 * 1024 * 1024)
                    { showDropTestStatus("Dropscreenshot is te groot."); return; }
                    FeedService.DropTestReport report = new FeedService.DropTestReport();
                    report.eventId = eventId; report.rsn = rsn; report.item = name; report.quantity = quantity;
                    report.screenshot = java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
                    FeedService currentService = service;
                    if (currentService != null) currentService.testDrop(report, config.managementToken(), new FeedService.DropTestListener()
                    {
                        @Override public void success(boolean delivered) { showDropTestStatus(Boolean.getBoolean("dutch.nations.local") ? (delivered ? "Drop staat in de wachtrij van de Discord-bot." : "Drop lokaal getest; niets naar Discord verstuurd.") : (delivered ? "Dropscreenshot verstuurd naar Discord." : "Discord heeft het versturen niet bevestigd.")); }
                        @Override public void failure(String message) { showDropTestStatus(message); }
                    });
                }
                catch (IOException ex) { showDropTestStatus("Dropscreenshot kon niet worden gemaakt."); }
            });
        });
    }

    private boolean activeDropEvent(String eventId)
    {
        ClanFeed current = feed;
        if (!isOwner() || current == null || eventId == null) return false;
        OffsetDateTime now = OffsetDateTime.now();
        return current.events.stream().anyMatch(event -> eventId.equals(event.id) && event.active(now));
    }

    private void showDropTestStatus(String message)
    { SwingUtilities.invokeLater(() -> { if (panel != null) panel.status(message); }); }

    private void scheduleRoleRefresh()
    {
        remainingRoleRefreshAttempts = 10;
        ticksUntilRoleRefresh = 1;
    }

    private void refreshRoleWhenPlayerIsReady()
    {
        if (ticksUntilRoleRefresh <= 0 || --ticksUntilRoleRefresh > 0) return;
        Player player = client.getLocalPlayer();
        String name = player == null ? "" : player.getName();
        if (name == null || name.trim().isEmpty())
        {
            if (--remainingRoleRefreshAttempts > 0) ticksUntilRoleRefresh = 1;
            return;
        }
        remainingRoleRefreshAttempts = 0;
        refreshRole();
    }

    private volatile boolean clanAccessGranted;
    private boolean hasClanAccess() { return clanAccessGranted; }
    private void updateOnlineClanMembers()
    {
        ClanChannel channel = client.getClanChannel();
        ClanSettings settings = client.getClanSettings();
        boolean inDutchNation = channel != null && settings != null
            && DUTCH_NATION_CLAN.equalsIgnoreCase(channel.getName() == null ? "" : channel.getName().trim())
            && DUTCH_NATION_CLAN.equalsIgnoreCase(settings.getName() == null ? "" : settings.getName().trim());
        if (channel != null || settings != null) clanAccessGranted = inDutchNation;
        else if (client.getGameState() == GameState.LOGGED_IN) clanAccessGranted = false;
        java.util.List<OnlineClanMember> members = new java.util.ArrayList<>();
        if (inDutchNation)
        {
            for (ClanChannelMember member : channel.getMembers())
            {
                String name = member.getName();
                if (name == null || name.trim().isEmpty()) continue;
                int rank = member.getRank() == null ? -1 : member.getRank().getRank();
                ClanTitle title = member.getRank() == null ? null : settings.titleForRank(member.getRank());
                java.awt.image.BufferedImage rankIcon = null;
                try { if (title != null) rankIcon = chatIconManager.getRankImage(title); }
                catch (RuntimeException ignored) { }
                members.add(new OnlineClanMember(name, rank, member.getWorld(), rankIcon));
            }
            members.sort(java.util.Comparator.comparingInt((OnlineClanMember value) -> value.rank).reversed()
                .thenComparing(value -> value.name.toLowerCase(java.util.Locale.ROOT)));
        }
        String signature = inDutchNation + ":" + clanAccessGranted + ":" + members;
        if (signature.equals(clanMembersSignature)) return;
        clanMembersSignature = signature;
        SwingUtilities.invokeLater(() -> { if (panel != null) { panel.updateClanAccess(clanAccessGranted); panel.updateOnlineMembers(members, inDutchNation); } });
    }

    private void queueScheduleChanges(ClanFeed previous, ClanFeed updated)
    {
        if (!receivedLiveFeed || previous == null || updated == null) { receivedLiveFeed = true; return; }
        Map<String, ClanFeed.ClanEvent> earlier = new HashMap<>();
        for (ClanFeed.ClanEvent event : previous.events) earlier.put(event.id, event);
        for (ClanFeed.ClanEvent event : updated.events)
        {
            ClanFeed.ClanEvent oldEvent = earlier.get(event.id);
            if (oldEvent != null && (!oldEvent.startsAt.equals(event.startsAt) || !oldEvent.endsAt.equals(event.endsAt)))
                pendingScheduleChanges.add(new ScheduleChange(oldEvent, event));
        }
    }

    private void queueScheduleChangeMessage(ClanFeed.ClanEvent previous, ClanFeed.ClanEvent updated)
    {
        chatMessages.queue(QueuedMessage.builder()
            .type(ChatMessageType.GAMEMESSAGE)
            .runeLiteFormattedMessage("<col=ffbd45>Dutch Nation:</col> Event <col=ffffff>" + safeText(updated.title) +
                "</col> gewijzigd van <col=ffbd45>" + schedule(previous) + "</col> naar <col=40e0e5>" + schedule(updated) + "</col>.")
            .build());
    }

    private static String schedule(ClanFeed.ClanEvent event)
    {
        return CHAT_DATE_TIME.format(event.start().atZoneSameInstant(ZoneId.systemDefault())) + " - " +
            CHAT_DATE_TIME.format(event.end().atZoneSameInstant(ZoneId.systemDefault()));
    }

    private static final class ScheduleChange
    {
        private final ClanFeed.ClanEvent previous;
        private final ClanFeed.ClanEvent updated;
        private ScheduleChange(ClanFeed.ClanEvent previous, ClanFeed.ClanEvent updated) { this.previous = previous; this.updated = updated; }
    }
    private static String notificationKey(ClanFeed.ClanEvent event)
    {
        return event.id + "@" + event.startsAt;
    }

    private void queueEventMessage(ClanFeed.ClanEvent event, String timing)
    {
        String safeTitle = safeText(event.title);
        String eventType = event.learner() ? "Learner-event" :
            ("MASS".equalsIgnoreCase(event.type) ? "Mass-event" :
            ("CLAN_EVENT".equalsIgnoreCase(event.type) ? "Clan-event" :
            ("CLAN_VS_CLAN".equalsIgnoreCase(event.type) ? "Clan vs Clan-event" : "Boss-event")));
        String world = (event.learner() || "MASS".equalsIgnoreCase(event.type) || "CLAN_EVENT".equalsIgnoreCase(event.type)) && event.world != null && !event.world.trim().isEmpty()
            ? " op wereld <col=40e0e5>" + event.world + "</col>" : "";
        chatMessages.queue(QueuedMessage.builder()
            .type(ChatMessageType.GAMEMESSAGE)
            .runeLiteFormattedMessage("<col=ffbd45>Dutch Nation:</col> " + eventType +
                " <col=ffffff>" + safeTitle + "</col> " + timing + world + "!")
            .build());
    }

    private boolean shouldNotify(ClanFeed.ClanEvent event)
    {
        if (event.learner()) return config.notifyLearner();
        if ("BOSS".equalsIgnoreCase(event.type) || "CLAN_EVENT".equalsIgnoreCase(event.type) || "CLAN_VS_CLAN".equalsIgnoreCase(event.type)) return config.notifyBoss();
        return "MASS".equalsIgnoreCase(event.type) && config.notifyMass();
    }

    ClanFeed.ClanEvent activeEvent(OffsetDateTime now)
    {
        if (!hasClanAccess()) return null;
        ClanFeed current = feed;
        if (current == null) return null;
        return current.events.stream().filter(e -> ("BOSS".equalsIgnoreCase(e.type) || "CLAN_EVENT".equalsIgnoreCase(e.type) || "CLAN_VS_CLAN".equalsIgnoreCase(e.type) || ("MASS".equalsIgnoreCase(e.type) && e.codewordRequired)) && e.active(now)).findFirst().orElse(null);
    }

    private boolean canManage()
    {
        return isOwner() || "OWNER".equals(authenticatedRole) || "ADMINISTRATOR".equals(authenticatedRole) ||
            "MANAGER".equals(authenticatedRole) || "TEACHER".equals(authenticatedRole) || "LEARNER_HOST".equals(authenticatedRole);
    }
    private boolean isAdministrator() { return "ADMINISTRATOR".equals(authenticatedRole); }
    private boolean isLearnerHost() { return "TEACHER".equals(authenticatedRole) || "LEARNER_HOST".equals(authenticatedRole); }
    private boolean canManageRoles() { return isOwner() || isAdministrator(); }
    private boolean isOwner() { return "OWNER".equals(authenticatedRole); }

    private void createEvent(EventDraft draft)
    {
        if (!canManage()) { panel.status("Je RuneScape-naam heeft geen managementrechten."); return; }
        if (isLearnerHost() && !"LEARNER".equalsIgnoreCase(draft.type)) { panel.status("Teachers mogen alleen learner-events maken."); return; }
        ClanFeed current = feed;
        if (current != null)
        {
            ClanFeed.ClanEvent conflict = current.events.stream().filter(event ->
                OffsetDateTime.parse(draft.startsAt).isBefore(event.end()) && OffsetDateTime.parse(draft.endsAt).isAfter(event.start()))
                .findFirst().orElse(null);
            if (conflict != null)
            {
                int answer = JOptionPane.showConfirmDialog(null,
                    "Dit event overlapt met '" + conflict.title + "'. Toch opslaan?", "Eventconflict",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (answer != JOptionPane.YES_OPTION) { panel.status("Event niet opgeslagen vanwege tijdconflict."); return; }
                draft.allowConflict = true;
            }
        }
        panel.status("Event opslaan...");
        service.createEvent(FeedService.EVENTS_URL, config.managementToken(), draft, new SaveResult("Event opgeslagen."));
    }
    private void updateEvent(String eventId, EventDraft draft)
    {
        if (!canManage()) { panel.status("Je RuneScape-naam heeft geen managementrechten."); return; }
        ClanFeed current = feed;
        ClanFeed.ClanEvent existing = current == null ? null : current.events.stream().filter(event -> eventId.equals(event.id)).findFirst().orElse(null);
        if (isLearnerHost())
        {
            if (existing == null || !existing.learner() || !normalize(localPlayerName()).equals(normalize(existing.host)) || !"LEARNER".equalsIgnoreCase(draft.type))
            { panel.status("Teachers mogen alleen hun eigen learner-event aanpassen."); return; }
        }
        if (current != null)
        {
            ClanFeed.ClanEvent conflict = current.events.stream().filter(event -> !eventId.equals(event.id) &&
                OffsetDateTime.parse(draft.startsAt).isBefore(event.end()) && OffsetDateTime.parse(draft.endsAt).isAfter(event.start()))
                .findFirst().orElse(null);
            if (conflict != null)
            {
                int answer = JOptionPane.showConfirmDialog(null,
                    "Dit event overlapt met '" + conflict.title + "'. Toch opslaan?", "Eventconflict",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (answer != JOptionPane.YES_OPTION) { panel.status("Event niet aangepast vanwege tijdconflict."); return; }
                draft.allowConflict = true;
            }
        }
        panel.status("Event aanpassen...");
        service.updateEvent(FeedService.EVENTS_URL, config.managementToken(), eventId, draft, new SaveResult("Event aangepast."));
    }

    private void createLearnerTemplate(ClanFeed.LearnerTemplate template)
    {
        if (!isOwner()) { panel.templateStatus("Alleen de owner mag learner-sjablonen beheren."); return; }
        panel.templateStatus("Learner-sjabloon opslaan...");
        service.createLearnerTemplate(config.managementToken(), template, new TemplateSaveResult("Learner-sjabloon toegevoegd."));
    }

    private void updateLearnerTemplate(String templateId, ClanFeed.LearnerTemplate template)
    {
        if (!isOwner()) { panel.templateStatus("Alleen de owner mag learner-sjablonen beheren."); return; }
        panel.templateStatus("Learner-sjabloon opslaan...");
        service.updateLearnerTemplate(config.managementToken(), templateId, template, new TemplateSaveResult("Learner-sjabloon aangepast."));
    }

    private void deleteLearnerTemplate(String templateId)
    {
        if (!isOwner()) { panel.templateStatus("Alleen de owner mag learner-sjablonen beheren."); return; }
        panel.templateStatus("Learner-sjabloon verwijderen...");
        service.deleteLearnerTemplate(config.managementToken(), templateId, new TemplateSaveResult("Learner-sjabloon verwijderd."));
    }

    private final class TemplateSaveResult implements FeedService.SaveListener
    {
        private final String success;
        private TemplateSaveResult(String success) { this.success = success; }
        @Override public void success()
        {
            SwingUtilities.invokeLater(() ->
            {
                if (panel != null) panel.templateStatus(success);
                fetchOwnerLogs();
                refresh();
            });
        }
        @Override public void failure(String message)
        { SwingUtilities.invokeLater(() -> { if (panel != null) panel.templateStatus(message); }); }
    }

    private String localPlayerName()
    {
        Player local = client.getLocalPlayer();
        return local == null ? "" : local.getName();
    }
    private long announcementReadSequence()
    {
        try { return Long.parseLong(configs.getConfiguration(DutchNationsConfig.GROUP, ANNOUNCEMENT_READ_SEQUENCE)); }
        catch (RuntimeException exception) { return 0L; }
    }

    private void markAnnouncementsRead(long sequence)
    {
        if (sequence > announcementReadSequence())
            configs.setConfiguration(DutchNationsConfig.GROUP, ANNOUNCEMENT_READ_SEQUENCE, Long.toString(sequence));
    }
    private void saveAnnouncementChannel(String url)
    {
        if (!isOwner()) { panel.announcementChannelFailed("Alleen de owner kan het mededelingenkanaal instellen."); return; }
        panel.announcementChannelSaving();
        service.saveAnnouncementChannel(url, config.managementToken(), new FeedService.AnnouncementChannelListener()
        {
            @Override public void success(String savedUrl)
            {
                SwingUtilities.invokeLater(() ->
                {
                    if (panel != null) panel.announcementChannelSaved(savedUrl);
                    fetchOwnerLogs();
                    refresh();
                });
            }
            @Override public void failure(String message)
            { SwingUtilities.invokeLater(() -> { if (panel != null) panel.announcementChannelFailed(message); }); }
        });
    }
    private void saveAnnouncementsVisibility(boolean visible)
    {
        if (!isOwner()) { panel.announcementVisibilityFailed("Alleen de owner kan de mededelingenknop wijzigen."); return; }
        panel.announcementVisibilitySaving();
        service.saveAnnouncementsVisibility(visible, config.managementToken(), new FeedService.AnnouncementVisibilityListener()
        {
            @Override public void success(boolean savedVisible)
            {
                SwingUtilities.invokeLater(() ->
                {
                    if (panel != null) panel.announcementVisibilitySaved(savedVisible);
                    fetchOwnerLogs();
                    refresh();
                });
            }
            @Override public void failure(String message)
            { SwingUtilities.invokeLater(() -> { if (panel != null) panel.announcementVisibilityFailed(message); }); }
        });
    }
    private void saveEventAnnouncementChannel(String url)
    {
        if (!isOwner()) { panel.eventAnnouncementChannelFailed("Alleen de owner kan het eventmeldingenkanaal instellen."); return; }
        panel.eventAnnouncementChannelSaving();
        service.saveEventAnnouncementChannel(url, config.managementToken(), new FeedService.AnnouncementChannelListener()
        {
            @Override public void success(String savedUrl)
            {
                SwingUtilities.invokeLater(() ->
                {
                    if (panel != null) panel.eventAnnouncementChannelSaved(savedUrl);
                    fetchOwnerLogs();
                    refresh();
                });
            }
            @Override public void failure(String message)
            { SwingUtilities.invokeLater(() -> { if (panel != null) panel.eventAnnouncementChannelFailed(message); }); }
        });
    }
    private void deleteEvent(String eventId)
    {
        if (!canManage()) { panel.status("Je RuneScape-naam heeft geen managementrechten."); return; }
        panel.status("Event verwijderen...");
        service.deleteEvent(FeedService.EVENTS_URL, config.managementToken(), eventId, new SaveResult("Event verwijderd."));
    }

    private void saveDropChannel(String url)
    {
        if (!isOwner()) { panel.dropChannelStatus("Alleen de owner kan het dropkanaal instellen."); return; }
        panel.dropChannelStatus("Dropkanaal opslaan...");
        service.saveDropChannel(url, config.managementToken(), new FeedService.AnnouncementChannelListener()
        {
            @Override public void success(String savedUrl)
            { SwingUtilities.invokeLater(() -> { if (panel != null) panel.dropChannelSaved(savedUrl); refresh(); }); }
            @Override public void failure(String message)
            { SwingUtilities.invokeLater(() -> { if (panel != null) panel.dropChannelStatus(message); }); }
        });
    }

    private void saveRole(RoleDraft draft)
    {
        if (!canManageRoles()) { panel.status("Geen rechten om rollen aan te passen."); return; }
        if (isAdministrator() && !("MANAGER".equals(draft.role) || "TEACHER".equals(draft.role)))
        { panel.status("Administrators mogen alleen managers en teachers toekennen."); return; }
        if ("OWNER".equals(draft.role))
        {
            int answer = JOptionPane.showConfirmDialog(null,
                "Ownership overdragen aan '" + draft.rsn + "'?\n\nJij verliest daarna direct alle ownerrechten. De nieuwe owner krijgt een nieuwe persoonlijke token.",
                "Ownership overdragen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) return;
        }
        panel.status("Managementrol opslaan...");
        service.saveRole(FeedService.ROLES_URL, config.managementToken(), draft, new FeedService.RoleSaveListener()
        {
            @Override public void success(String token)
            {
                SwingUtilities.invokeLater(() ->
                {
                    if (token != null && !token.trim().isEmpty())
                    {
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(token), null);
                        JOptionPane.showMessageDialog(null,
                            "Persoonlijke token voor " + draft.rsn + ":\n\n" + token +
                                "\n\nDe token is naar het klembord gekopieerd. Deel hem privé en slechts één keer.",
                            "Managementrol toegekend", JOptionPane.INFORMATION_MESSAGE);
                    }
                    if (panel != null) { panel.status("Rol centraal opgeslagen."); refresh(); fetchRoles(); }
                });
            }
            @Override public void failure(String message)
            {
                SwingUtilities.invokeLater(() -> { if (panel != null) panel.status(message); });
            }
        });
    }
    private final class SaveResult implements FeedService.SaveListener
    {
        private final String successMessage;
        private SaveResult(String successMessage) { this.successMessage = successMessage; }
        @Override public void success() { SwingUtilities.invokeLater(() -> { if (panel != null) { panel.status(successMessage); refresh(); } }); }
        @Override public void failure(String message) { SwingUtilities.invokeLater(() -> { if (panel != null) panel.status(message); }); }
    }

    private void refresh() { refreshWom(true); refresh(true); }

    private void refreshWom(boolean force)
    {
        if (womService == null || panel == null || !config.showWomCompetition()) return;
        long now = System.currentTimeMillis();
        if (!force && now - lastWomRefresh < Duration.ofHours(1).toMillis()) return;
        lastWomRefresh = now;
        womService.fetch(new WomCompetitionService.Listener()
        {
            @Override public void success(java.util.List<WomCompetition> competitions)
            { SwingUtilities.invokeLater(() -> { if (panel != null) panel.updateCompetition(competitions); }); }
            @Override public void failure()
            { SwingUtilities.invokeLater(() -> { if (panel != null) panel.competitionUnavailable(); }); }
        });
    }

    private void refreshRole()
    {
        if (service == null || config.managementToken().trim().isEmpty())
        {
            authenticatedRole = "";
            if (panel != null) SwingUtilities.invokeLater(panel::permissionsChanged);
            return;
        }
        service.fetchRole(FeedService.ROLES_URL, config.managementToken(), new FeedService.RoleStatusListener()
        {
            @Override public void success(String rsn, String role)
            {
                Player local = client.getLocalPlayer();
                String localRsn = local == null ? "" : normalize(local.getName());
                authenticatedRole = localRsn.equals(normalize(rsn)) ? role.trim().toUpperCase() : "";
                if (panel != null) SwingUtilities.invokeLater(panel::permissionsChanged);
            }
            @Override public void failure()
            {
                authenticatedRole = "";
                if (panel != null) SwingUtilities.invokeLater(panel::permissionsChanged);
            }
        });
    }
    private void fetchRoles()
    {
        if (!canManageRoles() || service == null || config.managementToken().trim().isEmpty()) return;
        service.fetchRoles(FeedService.ROLES_URL, config.managementToken(), new FeedService.RolesListener()
        {
            @Override public void success(java.util.List<ManagementRole> roles)
            { if (panel != null) SwingUtilities.invokeLater(() -> panel.updateRoles(roles)); }
            @Override public void failure(String message)
            { if (panel != null) SwingUtilities.invokeLater(() -> panel.rolesStatus(message)); }
        });
    }
    private void fetchOwnerLogs()
    {
        if (!isOwner() || service == null || config.managementToken().trim().isEmpty()) return;
        service.fetchOwnerLogs(config.managementToken(), new FeedService.OwnerLogsListener()
        {
            @Override public void success(FeedService.OwnerLogsResponse logs)
            { if (panel != null) SwingUtilities.invokeLater(() -> panel.updateOwnerLogs(logs)); }
            @Override public void failure(String message)
            { if (panel != null) SwingUtilities.invokeLater(() -> panel.ownerLogsStatus(message)); }
        });
    }

    private void refresh(boolean showStatus)
    {
        if (service == null) return;
        if (showStatus) panel.status("Controleren op management-updates...");
        service.fetch(FeedService.FEED_URL, new FeedService.Listener()
        {
            @Override public void success(ClanFeed value, String json)
            {
                ClanFeed previous = feed;
                queueScheduleChanges(previous, value);
                feed = value; configs.setConfiguration(DutchNationsConfig.GROUP, CACHE, cacheWithoutCodewords(value));
                SwingUtilities.invokeLater(() -> { if (panel != null) { panel.connectionChanged(true); panel.update(value, ""); } });
            }
            @Override public void failure(String message)
            {
                SwingUtilities.invokeLater(() -> { if (panel != null) { panel.connectionChanged(false); panel.status(message + (feed == null ? "" : " Laatste geldige versie blijft actief.")); } });
            }
        });
    }



    private String cacheWithoutCodewords(ClanFeed source)
    {
        ClanFeed cached = gson.fromJson(gson.toJson(source), ClanFeed.class);
        for (ClanFeed.ClanEvent event : cached.events) event.codeword = "";
        return gson.toJson(cached);
    }
    private static String normalize(String rsn) { return rsn == null ? "" : rsn.replace(' ', ' ').trim().toLowerCase(); }
    private static String safeText(String value) { return value == null ? "" : value.replace("<", "").replace(">", ""); }
    private static BufferedImage icon()
    {
        try (InputStream stream = DutchNationsPlugin.class.getResourceAsStream("/nl/dutchnations/events/dutch-nations-icon.png"))
        {
            BufferedImage source = stream == null ? null : ImageIO.read(stream);
            if (source != null)
            {
                BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = image.createGraphics();
                graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.drawImage(source, 0, 0, 32, 32, null);
                graphics.dispose();
                return image;
            }
        }
        catch (IOException ignored) { }
        BufferedImage fallback = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = fallback.createGraphics();
        graphics.setColor(new Color(174, 28, 40)); graphics.fillRoundRect(1, 1, 30, 30, 8, 8);
        graphics.setColor(Color.WHITE); graphics.setFont(graphics.getFont().deriveFont(java.awt.Font.BOLD, 18f));
        graphics.drawString("DN", 3, 23); graphics.dispose();
        return fallback;
    }
}

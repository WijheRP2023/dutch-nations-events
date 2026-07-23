package nl.dutchnations.events;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
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
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import okhttp3.OkHttpClient;

@PluginDescriptor(name = "Dutch Nations Events", description = "Centrale eventkalender met tijdelijke codewoord-popup")
public class DutchNationsPlugin extends Plugin
{
    private static final String CACHE = "cachedFeed";
    @Inject private Client client;
    @Inject private ChatMessageManager chatMessages;
    @Inject private ClientToolbar toolbar;
    @Inject private OverlayManager overlays;
    @Inject private CodewordOverlay overlay;
    @Inject private DutchNationsConfig config;
    @Inject private ConfigManager configs;
    @Inject private OkHttpClient http;
    @Inject private Gson gson;
    @Inject private ExternalPluginClient pluginHubClient;
    @Inject private ExternalPluginManager externalPluginManager;
    private FeedService service;
    private WomCompetitionService womService;
    private DutchNationsPanel panel;
    private NavigationButton button;
    private volatile ClanFeed feed;
    private volatile String authenticatedRole = "";
    private final Set<String> remindedEvents = new HashSet<>();
    private final Set<String> announcedEvents = new HashSet<>();
    private int ticksUntilRefresh;
    private long lastWomRefresh;

    @Provides DutchNationsConfig config(ConfigManager manager) { return manager.getConfig(DutchNationsConfig.class); }

    @Override protected void startUp()
    {
        service = new FeedService(http, gson);
        womService = new WomCompetitionService(http, gson);
        panel = new DutchNationsPanel(config, this::refresh, this::canManage, this::isOwner,
            this::isAdministrator, this::canManageRoles,
            this::createEvent, this::deleteEvent, this::saveRole);
        ClanFeed cached = service.parse(configs.getConfiguration(DutchNationsConfig.GROUP, CACHE));
        if (cached != null) { feed = cached; panel.update(cached, "Opgeslagen versie; update wordt gecontroleerd."); }
        button = NavigationButton.builder().tooltip("Dutch Nations").icon(icon()).priority(6).panel(panel).build();
        toolbar.addNavigation(button); overlays.add(overlay); loadPluginCatalog(); refresh();
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
    }

    @Subscribe public void onExternalPluginsChanged(ExternalPluginsChanged ignored)
    {
        loadPluginCatalog();
    }

    @Subscribe public void onConfigChanged(ConfigChanged e)
    {
        if (DutchNationsConfig.GROUP.equals(e.getGroup()) && !CACHE.equals(e.getKey())) refresh();
    }

    @Subscribe public void onGameStateChanged(GameStateChanged ignored)
    {
        authenticatedRole = "";
        refreshRole();
        if (panel != null) SwingUtilities.invokeLater(panel::permissionsChanged);
    }

    @Subscribe
    public void onGameTick(GameTick ignored)
    {
        ClanFeed current = feed;
        if (current == null) return;
        if (--ticksUntilRefresh <= 0)
        {
            ticksUntilRefresh = 50;
            refreshWom(false);
            refresh(false);
        }
        OffsetDateTime now = OffsetDateTime.now();
        for (ClanFeed.ClanEvent event : current.events)
        {
            long secondsUntilStart = Duration.between(now, event.start()).getSeconds();
            if (shouldNotify(event) && secondsUntilStart > 0 && secondsUntilStart <= config.reminderMinutes() * 60L && remindedEvents.add(event.id))
            {
                long minutes = Math.max(1, (secondsUntilStart + 59) / 60);
                queueEventMessage(event, "start over " + minutes + (minutes == 1 ? " minuut" : " minuten"));
            }
            if (config.notifyAtStart() && shouldNotify(event) && event.active(now) && announcedEvents.add(event.id))
            {
                queueEventMessage(event, "is nu gestart");
            }
        }
    }

    private void queueEventMessage(ClanFeed.ClanEvent event, String timing)
    {
        String safeTitle = safeText(event.title);
        String eventType = event.learner() ? "Learner-event" :
            ("MASS".equalsIgnoreCase(event.type) ? "Mass-event" : "Boss-event");
        String world = (event.learner() || "MASS".equalsIgnoreCase(event.type)) && event.world != null && !event.world.trim().isEmpty()
            ? " op wereld <col=40e0e5>" + event.world + "</col>" : "";
        chatMessages.queue(QueuedMessage.builder()
            .type(ChatMessageType.GAMEMESSAGE)
            .runeLiteFormattedMessage("<col=ffbd45>Dutch Nations:</col> " + eventType +
                " <col=ffffff>" + safeTitle + "</col> " + timing + world + "!")
            .build());
    }

    private boolean shouldNotify(ClanFeed.ClanEvent event)
    {
        if (event.learner()) return config.notifyLearner();
        if ("BOSS".equalsIgnoreCase(event.type)) return config.notifyBoss();
        return "MASS".equalsIgnoreCase(event.type) && config.notifyMass();
    }

    ClanFeed.ClanEvent activeEvent(OffsetDateTime now)
    {
        ClanFeed current = feed;
        if (current == null) return null;
        return current.events.stream().filter(e -> "BOSS".equalsIgnoreCase(e.type) && e.active(now)).findFirst().orElse(null);
    }

    private boolean canManage()
    {
        return isOwner() || "OWNER".equals(authenticatedRole) || "ADMINISTRATOR".equals(authenticatedRole) ||
            "MANAGER".equals(authenticatedRole) || "EVENT_HOST".equals(authenticatedRole);
    }
    private boolean isAdministrator() { return "ADMINISTRATOR".equals(authenticatedRole); }
    private boolean canManageRoles() { return isOwner() || isAdministrator(); }
    private boolean isOwner()
    {
        Player local = client.getLocalPlayer();
        return local != null && "heavenskill".equals(normalize(local.getName()));
    }

    private void createEvent(EventDraft draft)
    {
        if (!canManage()) { panel.status("Je RuneScape-naam heeft geen managementrechten."); return; }
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
        service.createEvent(config.managementApiUrl(), config.managementToken(), draft, new SaveResult("Event opgeslagen."));
    }
    private void deleteEvent(String eventId)
    {
        if (!canManage()) { panel.status("Je RuneScape-naam heeft geen managementrechten."); return; }
        panel.status("Event verwijderen...");
        service.deleteEvent(config.managementApiUrl(), config.managementToken(), eventId, new SaveResult("Event verwijderd."));
    }

    private void saveRole(RoleDraft draft)
    {
        if (!canManageRoles()) { panel.status("Geen rechten om rollen aan te passen."); return; }
        if (isAdministrator() && !"MANAGER".equals(draft.role))
        { panel.status("Administrators mogen alleen managers toevoegen."); return; }
        if ("heavenskill".equals(normalize(draft.rsn)) && "REMOVE".equals(draft.role))
        { panel.status("De eerste owner heavenskill kan zichzelf niet verwijderen."); return; }
        panel.status("Managementrol opslaan...");
        service.saveRole(config.rolesApiUrl(), config.managementToken(), draft, new FeedService.RoleSaveListener()
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
                    if (panel != null) { panel.status("Rol centraal opgeslagen."); refresh(); }
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
            @Override public void success(WomCompetition competition)
            { SwingUtilities.invokeLater(() -> { if (panel != null) panel.updateCompetition(competition, true); }); }
            @Override public void failure()
            { SwingUtilities.invokeLater(() -> { if (panel != null) panel.competitionUnavailable(); }); }
        });
    }

    private void refreshRole()
    {
        if (isOwner())
        {
            authenticatedRole = "OWNER";
            if (panel != null) SwingUtilities.invokeLater(panel::permissionsChanged);
            return;
        }
        if (service == null || config.managementToken().trim().isEmpty())
        {
            authenticatedRole = "";
            if (panel != null) SwingUtilities.invokeLater(panel::permissionsChanged);
            return;
        }
        service.fetchRole(config.rolesApiUrl(), config.managementToken(), new FeedService.RoleStatusListener()
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
        if (!isOwner() || service == null || config.managementToken().trim().isEmpty()) return;
        service.fetchRoles(config.rolesApiUrl(), config.managementToken(), new FeedService.RolesListener()
        {
            @Override public void success(java.util.List<ManagementRole> roles)
            { if (panel != null) SwingUtilities.invokeLater(() -> panel.updateRoles(roles)); }
            @Override public void failure(String message)
            { if (panel != null) SwingUtilities.invokeLater(() -> panel.rolesStatus(message)); }
        });
    }
    private void refresh(boolean showStatus)
    {
        if (service == null) return;
        if (showStatus) panel.status("Controleren op management-updates...");
        service.fetch(config.feedUrl(), new FeedService.Listener()
        {
            @Override public void success(ClanFeed value, String json)
            {
                feed = value; configs.setConfiguration(DutchNationsConfig.GROUP, CACHE, cacheWithoutCodewords(value));
                SwingUtilities.invokeLater(() -> { if (panel != null) { panel.connectionChanged(true); panel.update(value, "Actueel vanuit management."); } });
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
    private static String normalize(String rsn) { return rsn == null ? "" : rsn.replace('\u00a0', ' ').trim().toLowerCase(); }
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

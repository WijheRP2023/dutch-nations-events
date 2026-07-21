package nl.dutchnations.events;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.JOptionPane;
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
    private FeedService service;
    private DutchNationsPanel panel;
    private NavigationButton button;
    private volatile ClanFeed feed;
    private final Set<String> remindedEvents = new HashSet<>();
    private final Set<String> announcedEvents = new HashSet<>();
    private int ticksUntilRefresh;

    @Provides DutchNationsConfig config(ConfigManager manager) { return manager.getConfig(DutchNationsConfig.class); }

    @Override protected void startUp()
    {
        service = new FeedService(http, gson);
        panel = new DutchNationsPanel(this::refresh, this::canManage, this::isOwner,
            this::createEvent, this::deleteEvent, this::saveRole);
        ClanFeed cached = service.parse(configs.getConfiguration(DutchNationsConfig.GROUP, CACHE));
        if (cached != null) { feed = cached; panel.update(cached, "Opgeslagen versie; update wordt gecontroleerd."); }
        button = NavigationButton.builder().tooltip("Dutch Nations").icon(icon()).priority(6).panel(panel).build();
        toolbar.addNavigation(button); overlays.add(overlay); refresh();
    }

    @Override protected void shutDown()
    {
        overlays.remove(overlay); toolbar.removeNavigation(button); feed = null; panel = null; service = null;
    }

    @Subscribe public void onConfigChanged(ConfigChanged e)
    {
        if (DutchNationsConfig.GROUP.equals(e.getGroup()) && !CACHE.equals(e.getKey())) refresh();
    }

    @Subscribe public void onGameStateChanged(GameStateChanged ignored)
    {
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
            refresh(false);
        }
        OffsetDateTime now = OffsetDateTime.now();
        for (ClanFeed.ClanEvent event : current.events)
        {
            long secondsUntilStart = Duration.between(now, event.start()).getSeconds();
            if (secondsUntilStart > 0 && secondsUntilStart <= 30 * 60 && remindedEvents.add(event.id))
            {
                queueEventMessage(event, "start over 30 minuten");
            }
            if (event.active(now) && announcedEvents.add(event.id))
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

    ClanFeed.ClanEvent activeEvent(OffsetDateTime now)
    {
        ClanFeed current = feed;
        if (current == null) return null;
        return current.events.stream().filter(e -> "BOSS".equalsIgnoreCase(e.type) && e.active(now)).findFirst().orElse(null);
    }

    private boolean canManage() { return isOwner() || !config.managementToken().trim().isEmpty(); }
    private boolean isOwner()
    {
        Player local = client.getLocalPlayer();
        return local != null && "heavenskill".equals(normalize(local.getName()));
    }

    private void createEvent(EventDraft draft)
    {
        if (!canManage()) { panel.status("Je RuneScape-naam heeft geen managementrechten."); return; }
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
        if (!isOwner()) { panel.status("Alleen een owner kan rollen aanpassen."); return; }
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

    private void refresh() { refresh(true); }

    private void refresh(boolean showStatus)
    {
        if (service == null) return;
        if (showStatus) panel.status("Controleren op management-updates...");
        service.fetch(config.feedUrl(), new FeedService.Listener()
        {
            @Override public void success(ClanFeed value, String json)
            {
                feed = value; configs.setConfiguration(DutchNationsConfig.GROUP, CACHE, cacheWithoutCodewords(value));
                SwingUtilities.invokeLater(() -> { if (panel != null) panel.update(value, "Actueel vanuit management."); });
            }
            @Override public void failure(String message)
            {
                SwingUtilities.invokeLater(() -> { if (panel != null) panel.status(message + (feed == null ? "" : " Laatste geldige versie blijft actief.")); });
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
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics(); g.setColor(new Color(174, 28, 40)); g.fillRoundRect(1, 1, 30, 30, 8, 8);
        g.setColor(Color.WHITE); g.setFont(g.getFont().deriveFont(java.awt.Font.BOLD, 18f)); g.drawString("DN", 3, 23); g.dispose(); return image;
    }
}

package nl.dutchnations.events;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.net.URL;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

final class DutchNationsPanel extends PluginPanel
{
    private static final Color RED = new Color(142, 25, 32);
    private static final Color BACKGROUND = new Color(20, 17, 15);
    private static final Color STONE = new Color(48, 47, 50);
    private static final Color DARK_STONE = new Color(35, 34, 36);
    private static final Color CARD_BROWN = new Color(31, 25, 21);
    private static final Color LEARNER_COLOR = new Color(35, 220, 225);
    private static final Color BOSS_COLOR = new Color(255, 105, 105);
    private static final Color MASS_COLOR = new Color(195, 125, 255);
    private static final Color GOLD = new Color(232, 198, 94);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy", new Locale("nl", "NL"));
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final DutchNationsConfig config;
    private final Runnable refresh;
    private final BooleanSupplier canManage;
    private final BooleanSupplier isOwner;
    private final BooleanSupplier isAdministrator;
    private final BooleanSupplier canManageRoles;
    private final Consumer<EventDraft> saveEvent;
    private final Consumer<String> deleteEvent;
    private final Consumer<RoleDraft> saveRole;
    private ClanFeed feed;
    private List<ManagementRole> roles = new ArrayList<>();
    private List<String> pluginCatalog = new ArrayList<>();
    private String rolesMessage = "Rollenoverzicht laden...";
    private String viewMode = "LIJST";
    private boolean serverOnline;
    private String status = "Management-feed laden...";

    DutchNationsPanel(DutchNationsConfig config, Runnable refresh, BooleanSupplier canManage, BooleanSupplier isOwner,
        BooleanSupplier isAdministrator, BooleanSupplier canManageRoles,
        Consumer<EventDraft> saveEvent, Consumer<String> deleteEvent, Consumer<RoleDraft> saveRole)
    {
        this.config = config; this.refresh = refresh; this.canManage = canManage; this.isOwner = isOwner;
        this.isAdministrator = isAdministrator; this.canManageRoles = canManageRoles;
        this.saveEvent = saveEvent; this.deleteEvent = deleteEvent; this.saveRole = saveRole;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); setBackground(BACKGROUND); render();
    }

    void update(ClanFeed value, String message) { feed = value; status = message; render(); }
    void status(String message) { status = message; render(); }
    void permissionsChanged() { render(); }
    void connectionChanged(boolean online) { serverOnline = online; render(); }
    void updateRoles(List<ManagementRole> value) { roles = value == null ? new ArrayList<>() : new ArrayList<>(value); rolesMessage = ""; render(); }
    void rolesStatus(String message) { rolesMessage = message; render(); }

    private void render()
    {
        removeAll();
        add(header());
        add(Box.createRigidArea(new Dimension(0, 8)));
        add(statusCard());
        add(Box.createRigidArea(new Dimension(0, 7)));
        add(actionBar());
        add(Box.createRigidArea(new Dimension(0, 7)));
        add(viewBar());
        add(Box.createRigidArea(new Dimension(0, 14)));

        if (feed != null)
        {
            List<ClanFeed.ClanEvent> upcoming = new ArrayList<>(feed.events);
            upcoming.removeIf(event -> event.end().isBefore(OffsetDateTime.now()));
            upcoming.sort(Comparator.comparing(ClanFeed.ClanEvent::start));
            upcoming.removeIf(event -> (event.learner() && !config.showLearner()) ||
                ("BOSS".equalsIgnoreCase(event.type) && !config.showBoss()) ||
                ("MASS".equalsIgnoreCase(event.type) && !config.showMass()));
            if ("LIJST".equals(viewMode))
            {
                List<ClanFeed.ClanEvent> learners = upcoming.stream().filter(ClanFeed.ClanEvent::learner).collect(Collectors.toList());
                List<ClanFeed.ClanEvent> bosses = upcoming.stream().filter(event -> "BOSS".equalsIgnoreCase(event.type)).collect(Collectors.toList());
                List<ClanFeed.ClanEvent> masses = upcoming.stream().filter(event -> "MASS".equalsIgnoreCase(event.type)).collect(Collectors.toList());
                addSection("LEARNER-EVENTS", "Leren met begeleiding", learners, LEARNER_COLOR);
                add(Box.createRigidArea(new Dimension(0, 14)));
                addSection("BOSS-EVENTS", "Bossen met tijdelijk codewoord", bosses, BOSS_COLOR);
                add(Box.createRigidArea(new Dimension(0, 14)));
                addSection("MASS-EVENTS", "Grootschalige clanactiviteiten", masses, MASS_COLOR);
            }
            else addCalendar(upcoming, "WEEK".equals(viewMode) ? 7 : 31);

            if (canManageRoles.getAsBoolean())
            {
                add(Box.createRigidArea(new Dimension(0, 14)));
                addManagementSection();
            }
        }
        add(Box.createVerticalGlue()); revalidate(); repaint();
    }

    private JPanel header()
    {
        JPanel panel = card(BACKGROUND);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 4, 0, RED), BorderFactory.createEmptyBorder(5, 5, 7, 5)));
        URL logoResource = DutchNationsPanel.class.getResource("/nl/dutchnations/events/dutch-nations-header.png");
        if (logoResource != null)
        {
            Image logoImage = new ImageIcon(logoResource).getImage().getScaledInstance(210, 79, Image.SCALE_SMOOTH);
            JLabel logo = new JLabel(new ImageIcon(logoImage));
            logo.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(logo);
        }
        else panel.add(label("DUTCH NATIONS", Color.WHITE, Font.BOLD, 18f));
        panel.add(Box.createRigidArea(new Dimension(0, 4)));
        panel.add(bodyLabel("Clan-eventkalender", new Color(255, 235, 235), Font.PLAIN, 13f));
        if (isOwner.getAsBoolean()) panel.add(bodyLabel("Beheerstatus: OWNER", new Color(255, 225, 120), Font.BOLD, 12f));
        else if (isAdministrator.getAsBoolean()) panel.add(bodyLabel("Beheerstatus: ADMINISTRATOR", GOLD, Font.BOLD, 12f));
        else if (canManage.getAsBoolean()) panel.add(bodyLabel("Beheerstatus: MANAGER", new Color(220, 235, 255), Font.BOLD, 12f));
        return panel;
    }

    private JPanel statusCard()
    {
        JPanel panel = card(CARD_BROWN);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, new Color(90, 185, 110)),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        panel.add(bodyLabel((serverOnline ? "● SERVER ONLINE" : "● SERVER OFFLINE"),
            serverOnline ? new Color(120, 220, 140) : new Color(255, 120, 100), Font.BOLD, 11f));
        JTextArea text = new JTextArea(status);
        text.setLineWrap(true); text.setWrapStyleWord(true); text.setEditable(false); text.setFocusable(false);
        text.setOpaque(false); text.setForeground(Color.WHITE); text.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        text.setAlignmentX(Component.LEFT_ALIGNMENT); panel.add(text);
        return panel;
    }

    void updatePluginCatalog(List<String> names)
    {
        pluginCatalog = names == null ? new ArrayList<>() : new ArrayList<>(names);
    }

    private JPanel actionBar()
    {
        JPanel actions = new JPanel(); actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        actions.setOpaque(false); actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton reload = button("Vernieuwen"); reload.addActionListener(event -> refresh.run()); actions.add(reload);
        if (canManage.getAsBoolean())
        {
            actions.add(Box.createRigidArea(new Dimension(0, 5))); JButton create = button("+ Event maken");
            create.addActionListener(event -> { EventDraft draft = EventEditorDialog.show(pluginCatalog); if (draft != null) saveEvent.accept(draft); }); actions.add(create);
        }
        if (canManageRoles.getAsBoolean())
        {
            actions.add(Box.createRigidArea(new Dimension(0, 5))); JButton roles = button("Managementrollen");
            roles.addActionListener(event -> { RoleDraft draft = RoleEditorDialog.show(isOwner.getAsBoolean()); if (draft != null) saveRole.accept(draft); }); actions.add(roles);
        }
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, actions.getPreferredSize().height));
        return actions;
    }

    private JPanel viewBar()
    {
        JPanel views = new JPanel(new java.awt.GridLayout(1, 3, 5, 0));
        views.setOpaque(false); views.setAlignmentX(Component.LEFT_ALIGNMENT);
        addViewButton(views, "Lijst", "LIJST"); addViewButton(views, "7 dagen", "WEEK"); addViewButton(views, "31 dagen", "MAAND");
        views.setMaximumSize(new Dimension(Integer.MAX_VALUE, views.getPreferredSize().height));
        return views;
    }
    private void addViewButton(JPanel panel, String title, String mode)
    {
        JButton button = button(title);
        if (mode.equals(viewMode)) button.setBackground(new Color(105, 75, 27));
        button.addActionListener(event -> { viewMode = mode; render(); });
        panel.add(button);
    }
    private void addCalendar(List<ClanFeed.ClanEvent> events, int days)
    {
        LocalDate today = LocalDate.now(); boolean found = false;
        for (int offset = 0; offset < days; offset++)
        {
            LocalDate date = today.plusDays(offset);
            List<ClanFeed.ClanEvent> dayEvents = events.stream().filter(event ->
                event.start().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate().equals(date)).collect(Collectors.toList());
            if (dayEvents.isEmpty()) continue;
            found = true;
            JPanel heading = card(STONE);
            heading.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 5, 0, 0, GOLD), BorderFactory.createEmptyBorder(7, 8, 7, 8)));
            heading.add(bodyLabel(DATE.format(date), GOLD, Font.BOLD, 13f)); add(heading);
            add(Box.createRigidArea(new Dimension(0, 7)));
            for (ClanFeed.ClanEvent event : dayEvents)
            { add(eventCard(event, accentFor(event))); add(Box.createRigidArea(new Dimension(0, 8))); }
        }
        if (!found)
        {
            JPanel empty = card(DARK_STONE);
            empty.add(bodyLabel("Geen events in de komende " + days + " dagen.", Color.LIGHT_GRAY, Font.ITALIC, 12f));
            add(empty);
        }
    }
    private static Color accentFor(ClanFeed.ClanEvent event)
    {
        if (event.learner()) return LEARNER_COLOR;
        return "MASS".equalsIgnoreCase(event.type) ? MASS_COLOR : BOSS_COLOR;
    }
    private void addSection(String title, String subtitle, List<ClanFeed.ClanEvent> events, Color accent)
    {
        JPanel heading = card(STONE);
        heading.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 5, 0, 0, accent),
            BorderFactory.createEmptyBorder(7, 8, 7, 8)));
        heading.add(label(title + "  (" + events.size() + ")", accent, Font.BOLD, 13f));
        heading.add(bodyLabel(subtitle, Color.WHITE, Font.PLAIN, 12f));
        add(heading); add(Box.createRigidArea(new Dimension(0, 7)));

        if (events.isEmpty())
        {
            JPanel empty = card(DARK_STONE);
            empty.add(bodyLabel("Geen geplande events in deze categorie.", Color.LIGHT_GRAY, Font.ITALIC, 12f));
            add(empty); return;
        }
        for (ClanFeed.ClanEvent event : events) { add(eventCard(event, accent)); add(Box.createRigidArea(new Dimension(0, 8))); }
    }

    private JPanel eventCard(ClanFeed.ClanEvent event, Color accent)
    {
        JPanel panel = card(CARD_BROWN);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 1, 1, 4, GOLD),
            BorderFactory.createEmptyBorder(9, 9, 9, 9)));
        String badgeText = event.learner() ? " LEARNER " : ("MASS".equalsIgnoreCase(event.type) ? " MASS " : " BOSS ");
        JLabel badge = label(badgeText, Color.BLACK, Font.BOLD, 11f); badge.setOpaque(true); badge.setBackground(accent); panel.add(badge);
        panel.add(Box.createRigidArea(new Dimension(0, 6)));
        panel.add(label(event.title, Color.WHITE, Font.BOLD, 16f));
        java.time.ZonedDateTime start = event.start().atZoneSameInstant(ZoneId.systemDefault());
        java.time.ZonedDateTime end = event.end().atZoneSameInstant(ZoneId.systemDefault());
        panel.add(bodyLabel("Datum: " + DATE.format(start), GOLD, Font.BOLD, 13f));
        if (start.toLocalDate().equals(end.toLocalDate()))
            panel.add(bodyLabel("Tijd: " + TIME.format(start) + " - " + TIME.format(end), GOLD, Font.BOLD, 13f));
        else
        {
            panel.add(bodyLabel("Start: " + TIME.format(start), GOLD, Font.BOLD, 13f));
            panel.add(bodyLabel("Einde: " + DATE.format(end) + " om " + TIME.format(end), new Color(255, 175, 120), Font.BOLD, 13f));
        }
        if (!"BOSS".equalsIgnoreCase(event.type)) panel.add(bodyLabel("Wereld: " + event.world, Color.WHITE, Font.PLAIN, 13f));
        if (!blank(event.host)) panel.add(bodyLabel("Host: " + event.host, Color.WHITE, Font.PLAIN, 13f));
        if (!blank(event.description)) panel.add(bodyLabel("Info: " + event.description, Color.WHITE, Font.PLAIN, 13f));
        if (event.learner() && !blank(event.checklist))
        {
            panel.add(Box.createRigidArea(new Dimension(0, 5)));
            panel.add(bodyLabel("VOORBEREIDING", GOLD, Font.BOLD, 12f));
            panel.add(bodyText("• " + event.checklist.replace(";", "\n• "), Color.WHITE, Font.PLAIN, 12f));
        }
        if (event.learner() && !blank(event.requiredPlugins))
        {
            panel.add(Box.createRigidArea(new Dimension(0, 5)));
            panel.add(bodyLabel("BENODIGDE PLUGINS", new Color(120, 210, 255), Font.BOLD, 12f));
            for (String value : event.requiredPlugins.split(";"))
            {
                String pluginName = value.trim();
                if (pluginName.isEmpty()) continue;
                JButton pluginLink = button("Plugin Hub: " + pluginName);
                pluginLink.setForeground(new Color(180, 225, 255));
                pluginLink.setBackground(new Color(32, 52, 66));
                pluginLink.addActionListener(click -> LinkBrowser.browse(
                    "https://runelite.net/plugin-hub/show/" + pluginSlug(pluginName)));
                panel.add(pluginLink);
                panel.add(Box.createRigidArea(new Dimension(0, 3)));
            }
        }
        String codeInfo = "BOSS".equalsIgnoreCase(event.type) ? "Codewoord verschijnt tijdens het event" : "Geen codewoord nodig";
        panel.add(Box.createRigidArea(new Dimension(0, 5))); panel.add(bodyLabel(codeInfo, accent, Font.BOLD, 12f));
        if (canManage.getAsBoolean())
        {
            JButton remove = button("Event verwijderen"); remove.setForeground(new Color(255, 205, 190)); remove.setBackground(new Color(105, 28, 31));
            remove.addActionListener(click ->
            {
                int answer = JOptionPane.showConfirmDialog(this, "Event '" + event.title + "' definitief verwijderen?",
                    "Event verwijderen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (answer == JOptionPane.YES_OPTION) deleteEvent.accept(event.id);
            });
            panel.add(Box.createRigidArea(new Dimension(0, 7))); panel.add(remove);
        }
        return panel;
    }

    private void addManagementSection()
    {
        JPanel heading = card(STONE);
        heading.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 5, 0, 0, GOLD), BorderFactory.createEmptyBorder(7, 8, 7, 8)));
        heading.add(label("MANAGEMENTROLLEN", GOLD, Font.BOLD, 13f));
        heading.add(bodyText("Alleen beveiligd zichtbaar voor de owner.", Color.WHITE, Font.PLAIN, 12f));
        add(heading);
        if (!isOwner.getAsBoolean()) return;
        if (!blank(rolesMessage))
        {
            add(Box.createRigidArea(new Dimension(0, 7)));
            JPanel message = card(DARK_STONE); message.add(bodyText(rolesMessage, Color.LIGHT_GRAY, Font.PLAIN, 12f)); add(message);
        }
        for (ManagementRole role : roles)
        {
            add(Box.createRigidArea(new Dimension(0, 7)));
            JPanel roleCard = card(CARD_BROWN);
            roleCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 1, 4, GOLD), BorderFactory.createEmptyBorder(8, 8, 8, 8)));
            roleCard.add(bodyLabel(role.rsn, Color.WHITE, Font.BOLD, 14f));
            roleCard.add(bodyLabel("Rol: " + role.role, GOLD, Font.BOLD, 12f));
            if (!blank(role.updatedAt))
            {
                try
                {
                    java.time.ZonedDateTime changed = OffsetDateTime.parse(role.updatedAt).atZoneSameInstant(ZoneId.systemDefault());
                    roleCard.add(bodyLabel("Gewijzigd: " + DATE.format(changed) + " " + TIME.format(changed), Color.LIGHT_GRAY, Font.PLAIN, 11f));
                }
                catch (RuntimeException ignored) { }
            }
            if (!"heavenskill".equalsIgnoreCase(role.rsn))
            {
                JButton rotate = button("Token vernieuwen");
                rotate.addActionListener(event ->
                {
                    int answer = JOptionPane.showConfirmDialog(this, "Token van '" + role.rsn + "' vernieuwen? De oude token stopt direct.",
                        "Token vernieuwen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (answer == JOptionPane.YES_OPTION) saveRole.accept(new RoleDraft(role.rsn, "ROTATE"));
                });
                JButton remove = button("Rol intrekken"); remove.setBackground(new Color(105, 28, 31));
                remove.addActionListener(event ->
                {
                    int answer = JOptionPane.showConfirmDialog(this, "Rol van '" + role.rsn + "' definitief intrekken?",
                        "Rol intrekken", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (answer == JOptionPane.YES_OPTION) saveRole.accept(new RoleDraft(role.rsn, "REMOVE"));
                });
                roleCard.add(Box.createRigidArea(new Dimension(0, 6))); roleCard.add(rotate);
                roleCard.add(Box.createRigidArea(new Dimension(0, 5))); roleCard.add(remove);
            }
            add(roleCard);
        }
    }

    private static JButton button(String text)
    {
        JButton button = new JButton(text);
        button.setFocusable(false);
        button.setForeground(new Color(255, 231, 170));
        button.setBackground(RED);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(176, 132, 48)), BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        return button;
    }
    private static JPanel card(Color color)
    {
        JPanel panel = new JPanel(); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); panel.setBackground(color);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE)); return panel;
    }
    private static JLabel bodyLabel(String text, Color color, int style, float size)
    {
        JLabel label = new JLabel(text == null ? "" : text);
        label.setForeground(color);
        label.setFont(new Font(Font.SANS_SERIF, style, Math.round(size)));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
    private static JTextArea bodyText(String text, Color color, int style, float size)
    {
        JTextArea area = new JTextArea(text == null ? "" : text);
        area.setLineWrap(true); area.setWrapStyleWord(true); area.setEditable(false); area.setFocusable(false);
        area.setOpaque(false); area.setForeground(color); area.setFont(new Font(Font.SANS_SERIF, style, Math.round(size)));
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        return area;
    }
    private static JLabel label(String text, Color color, int style, float size)
    {
        JLabel label = new JLabel(text == null ? "" : text); label.setForeground(color);
        label.setFont(label.getFont().deriveFont(style, size)); label.setAlignmentX(Component.LEFT_ALIGNMENT); return label;
    }
    private static String pluginSlug(String name)
    {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}

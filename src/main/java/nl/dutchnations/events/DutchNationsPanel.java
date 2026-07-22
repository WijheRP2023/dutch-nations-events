package nl.dutchnations.events;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.net.URL;
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

    private final Runnable refresh;
    private final BooleanSupplier canManage;
    private final BooleanSupplier isOwner;
    private final BooleanSupplier isAdministrator;
    private final BooleanSupplier canManageRoles;
    private final Consumer<EventDraft> saveEvent;
    private final Consumer<String> deleteEvent;
    private final Consumer<RoleDraft> saveRole;
    private ClanFeed feed;
    private String status = "Management-feed laden...";

    DutchNationsPanel(Runnable refresh, BooleanSupplier canManage, BooleanSupplier isOwner,
        BooleanSupplier isAdministrator, BooleanSupplier canManageRoles,
        Consumer<EventDraft> saveEvent, Consumer<String> deleteEvent, Consumer<RoleDraft> saveRole)
    {
        this.refresh = refresh; this.canManage = canManage; this.isOwner = isOwner;
        this.isAdministrator = isAdministrator; this.canManageRoles = canManageRoles;
        this.saveEvent = saveEvent; this.deleteEvent = deleteEvent; this.saveRole = saveRole;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); setBackground(BACKGROUND); render();
    }

    void update(ClanFeed value, String message) { feed = value; status = message; render(); }
    void status(String message) { status = message; render(); }
    void permissionsChanged() { render(); }

    private void render()
    {
        removeAll();
        add(header());
        add(Box.createRigidArea(new Dimension(0, 8)));
        add(statusCard());
        add(Box.createRigidArea(new Dimension(0, 7)));
        add(actionBar());
        add(Box.createRigidArea(new Dimension(0, 14)));

        if (feed != null)
        {
            List<ClanFeed.ClanEvent> upcoming = new ArrayList<>(feed.events);
            upcoming.removeIf(event -> event.end().isBefore(OffsetDateTime.now()));
            upcoming.sort(Comparator.comparing(ClanFeed.ClanEvent::start));
            List<ClanFeed.ClanEvent> learners = upcoming.stream().filter(ClanFeed.ClanEvent::learner).collect(Collectors.toList());
            List<ClanFeed.ClanEvent> bosses = upcoming.stream().filter(event -> "BOSS".equalsIgnoreCase(event.type)).collect(Collectors.toList());
            List<ClanFeed.ClanEvent> masses = upcoming.stream().filter(event -> "MASS".equalsIgnoreCase(event.type)).collect(Collectors.toList());

            addSection("LEARNER-EVENTS", "Leren met begeleiding", learners, LEARNER_COLOR);
            add(Box.createRigidArea(new Dimension(0, 14)));
            addSection("BOSS-EVENTS", "Bossen met tijdelijk codewoord", bosses, BOSS_COLOR);
            add(Box.createRigidArea(new Dimension(0, 14)));
            addSection("MASS-EVENTS", "Grootschalige clanactiviteiten", masses, MASS_COLOR);

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
        panel.add(label("Clan-eventkalender", new Color(255, 235, 235), Font.PLAIN, 12f));
        if (isOwner.getAsBoolean()) panel.add(label("Beheerstatus: OWNER", new Color(255, 225, 120), Font.BOLD, 11f));
        else if (isAdministrator.getAsBoolean()) panel.add(label("Beheerstatus: ADMINISTRATOR", GOLD, Font.BOLD, 11f));
        else if (canManage.getAsBoolean()) panel.add(label("Beheerstatus: MANAGER", new Color(220, 235, 255), Font.BOLD, 11f));
        return panel;
    }

    private JPanel statusCard()
    {
        JPanel panel = card(CARD_BROWN);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, new Color(90, 185, 110)),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        panel.add(label("STATUS", new Color(120, 220, 140), Font.BOLD, 10f));
        JTextArea text = new JTextArea(status);
        text.setLineWrap(true); text.setWrapStyleWord(true); text.setEditable(false); text.setFocusable(false);
        text.setOpaque(false); text.setForeground(Color.LIGHT_GRAY); text.setFont(text.getFont().deriveFont(12f));
        text.setAlignmentX(Component.LEFT_ALIGNMENT); panel.add(text);
        return panel;
    }

    private JPanel actionBar()
    {
        JPanel actions = new JPanel(); actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        actions.setOpaque(false); actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton reload = button("Vernieuwen"); reload.addActionListener(event -> refresh.run()); actions.add(reload);
        if (canManage.getAsBoolean())
        {
            actions.add(Box.createRigidArea(new Dimension(0, 5))); JButton create = button("+ Event maken");
            create.addActionListener(event -> { EventDraft draft = EventEditorDialog.show(); if (draft != null) saveEvent.accept(draft); }); actions.add(create);
        }
        if (canManageRoles.getAsBoolean())
        {
            actions.add(Box.createRigidArea(new Dimension(0, 5))); JButton roles = button("Managementrollen");
            roles.addActionListener(event -> { RoleDraft draft = RoleEditorDialog.show(isOwner.getAsBoolean()); if (draft != null) saveRole.accept(draft); }); actions.add(roles);
        }
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, actions.getPreferredSize().height));
        return actions;
    }

    private void addSection(String title, String subtitle, List<ClanFeed.ClanEvent> events, Color accent)
    {
        JPanel heading = card(STONE);
        heading.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 5, 0, 0, accent),
            BorderFactory.createEmptyBorder(7, 8, 7, 8)));
        heading.add(label(title + "  (" + events.size() + ")", accent, Font.BOLD, 13f));
        heading.add(label(subtitle, Color.LIGHT_GRAY, Font.PLAIN, 11f));
        add(heading); add(Box.createRigidArea(new Dimension(0, 7)));

        if (events.isEmpty())
        {
            JPanel empty = card(DARK_STONE);
            empty.add(label("Geen geplande events in deze categorie.", Color.LIGHT_GRAY, Font.ITALIC, 12f));
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
        panel.add(label("Datum: " + DATE.format(start), GOLD, Font.BOLD, 12f));
        if (start.toLocalDate().equals(end.toLocalDate()))
            panel.add(label("Tijd: " + TIME.format(start) + " - " + TIME.format(end), GOLD, Font.BOLD, 12f));
        else
        {
            panel.add(label("Start: " + TIME.format(start), GOLD, Font.BOLD, 12f));
            panel.add(label("Einde: " + DATE.format(end) + " om " + TIME.format(end), new Color(255, 155, 105), Font.BOLD, 12f));
        }
        if (!"BOSS".equalsIgnoreCase(event.type)) panel.add(label("Wereld: " + event.world, Color.WHITE, Font.PLAIN, 12f));
        if (!blank(event.host)) panel.add(label("Host: " + event.host, Color.WHITE, Font.PLAIN, 12f));
        if (!blank(event.description)) panel.add(label("Info: " + event.description, Color.WHITE, Font.PLAIN, 12f));
        String codeInfo = "BOSS".equalsIgnoreCase(event.type) ? "Codewoord verschijnt tijdens het event" : "Geen codewoord nodig";
        panel.add(Box.createRigidArea(new Dimension(0, 5))); panel.add(label(codeInfo, accent, Font.BOLD, 11f));
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
        heading.add(label("Rollen zijn beveiligd en niet zichtbaar in de openbare feed.", Color.LIGHT_GRAY, Font.PLAIN, 11f));
        add(heading);
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
    private static JLabel label(String text, Color color, int style, float size)
    {
        JLabel label = new JLabel(text == null ? "" : text); label.setForeground(color);
        label.setFont(label.getFont().deriveFont(style, size)); label.setAlignmentX(Component.LEFT_ALIGNMENT); return label;
    }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}

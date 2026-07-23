package nl.dutchnations.events;

import java.awt.GridLayout;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

final class EventEditorDialog
{
    private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final List<DateTimeFormatter> ACCEPTED_INPUTS = Arrays.asList(
        INPUT,
        DateTimeFormatter.ofPattern("yyyy-M-d H:mm"),
        DateTimeFormatter.ofPattern("d-M-yyyy H:mm"),
        DateTimeFormatter.ofPattern("d/M/yyyy H:mm")
    );
    private EventEditorDialog() { }

    static EventDraft show(List<String> pluginCatalog)
    {
        JComboBox<String> type = new JComboBox<>(new String[]{"LEARNER", "BOSS", "MASS"});
        JTextField title = new JTextField();
        JTextField start = new JTextField(LocalDateTime.now().plusDays(1).withSecond(0).withNano(0).format(INPUT));
        JTextField end = new JTextField(LocalDateTime.now().plusDays(1).plusHours(2).withSecond(0).withNano(0).format(INPUT));
        JTextField world = new JTextField("366");
        JTextField host = new JTextField();
        JTextField description = new JTextField();
        JTextField codeword = new JTextField();
        JTextField checklist = new JTextField();
        JTextField requiredPlugins = new JTextField();
        JComboBox<String> strategyWiki = new JComboBox<>(new String[]{
            "", "https://oldschool.runescape.wiki/w/Tombs_of_Amascut/Strategies", "https://oldschool.runescape.wiki/w/Theatre_of_Blood/Strategies",
            "https://oldschool.runescape.wiki/w/Chambers_of_Xeric/Strategies", "https://oldschool.runescape.wiki/w/Nex/Strategies", "https://oldschool.runescape.wiki/w/God_Wars_Dungeon",
            "https://oldschool.runescape.wiki/w/The_Nightmare/Strategies", "https://oldschool.runescape.wiki/w/Corporeal_Beast/Strategies", "https://oldschool.runescape.wiki/w/Wilderness_bosses"
        });
        strategyWiki.setEditable(true);
        JTextField pluginSearch = new JTextField();
        DefaultComboBoxModel<String> pluginResultsModel = new DefaultComboBoxModel<>();
        JComboBox<String> pluginResults = new JComboBox<>(pluginResultsModel);
        JButton addPlugin = new JButton("Plugin toevoegen");


        DocumentListener searchListener = new DocumentListener()
        {
            private void update()
            {
                String query = pluginSearch.getText().trim().toLowerCase(java.util.Locale.ROOT);
                pluginResultsModel.removeAllElements();
                if (query.length() < 2) return;
                pluginCatalog.stream()
                    .filter(name -> name.toLowerCase(java.util.Locale.ROOT).contains(query))
                    .limit(12).forEach(pluginResultsModel::addElement);
            }
            @Override public void insertUpdate(DocumentEvent e) { update(); }
            @Override public void removeUpdate(DocumentEvent e) { update(); }
            @Override public void changedUpdate(DocumentEvent e) { update(); }
        };
        pluginSearch.getDocument().addDocumentListener(searchListener);
        addPlugin.addActionListener(e ->
        {
            Object selected = pluginResults.getSelectedItem();
            if (selected == null) return;
            String name = selected.toString();
            String current = requiredPlugins.getText().trim();
            boolean exists = Arrays.stream(current.split(";"))
                .anyMatch(value -> value.trim().equalsIgnoreCase(name));
            if (!exists) requiredPlugins.setText(current.isEmpty() ? name : current + "; " + name);
            pluginSearch.setText("");
        });

        type.addActionListener(e ->
        {
            boolean learner = "LEARNER".equals(type.getSelectedItem());
            boolean boss = "BOSS".equals(type.getSelectedItem());
            boolean supportsPreparation = !boss;
            world.setEnabled(!boss);
            codeword.setEnabled(boss);
            checklist.setEnabled(supportsPreparation);
            requiredPlugins.setEnabled(supportsPreparation);
            strategyWiki.setEnabled(supportsPreparation);
            pluginSearch.setEnabled(supportsPreparation);
            pluginResults.setEnabled(supportsPreparation);
            addPlugin.setEnabled(supportsPreparation);
            if (!boss) codeword.setText("");
            if (!supportsPreparation) checklist.setText("");
            if (!supportsPreparation) requiredPlugins.setText("");
            if (!supportsPreparation) strategyWiki.setSelectedItem("");
            if (boss) world.setText("");
            if ("MASS".equals(type.getSelectedItem()) && world.getText().trim().isEmpty()) world.setText("366");
        });

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        add(form, "Soort", type); add(form, "Titel", title); add(form, "Start (bijv. 2026-08-01 20:00)", start);
        add(form, "Einde (bijv. 2026-08-01 22:00)", end); add(form, "Wereld (learner/mass)", world); add(form, "Host", host);
        add(form, "Omschrijving", description); add(form, "Voorbereiding (learner/mass; scheid met ;)", checklist);
        add(form, "Plugin zoeken (minimaal 2 letters)", pluginSearch);
        add(form, "Gevonden Plugin Hub-plugin", pluginResults);
        add(form, "", addPlugin);
        add(form, "Gekozen plugins", requiredPlugins);
        add(form, "Strategie-wikilink (kies of plak)", strategyWiki);
        add(form, "Codewoord (alleen boss)", codeword);

        while (true)
        {
            if (JOptionPane.showConfirmDialog(null, form, "Dutch Nations - event maken",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return null;
        try
        {
            EventDraft draft = new EventDraft();
            draft.type = (String) type.getSelectedItem(); draft.title = title.getText().trim();
            LocalDateTime localStart = parseDateTime(start.getText(), "startdatum en starttijd");
            LocalDateTime localEnd = parseDateTime(end.getText(), "einddatum en eindtijd");
            if (!localEnd.isAfter(localStart) && localEnd.toLocalDate().isBefore(localStart.toLocalDate()))
            {
                LocalDateTime suggestedEnd = LocalDateTime.of(localStart.toLocalDate(), localEnd.toLocalTime());
                if (suggestedEnd.isAfter(localStart))
                {
                    int choice = JOptionPane.showConfirmDialog(null,
                        "De einddatum staat voor de startdatum. Bedoelde je " + suggestedEnd.format(INPUT) + "?",
                        "Einddatum aanpassen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (choice == JOptionPane.YES_OPTION)
                    {
                        localEnd = suggestedEnd;
                        end.setText(suggestedEnd.format(INPUT));
                    }
                }
            }
            draft.startsAt = localStart.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString();
            draft.endsAt = localEnd.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString();
            draft.world = world.getText().trim(); draft.host = host.getText().trim();
            draft.description = description.getText().trim(); draft.codeword = codeword.getText().trim();
            draft.checklist = checklist.getText().trim();
            draft.requiredPlugins = requiredPlugins.getText().trim();
            Object selectedStrategy = strategyWiki.getEditor().getItem();
            draft.strategyWikiUrl = selectedStrategy == null ? "" : selectedStrategy.toString().trim();
            boolean learner = "LEARNER".equals(draft.type);
            boolean boss = "BOSS".equals(draft.type);
            boolean supportsPreparation = !boss;
            OffsetDateTime parsedStart = OffsetDateTime.parse(draft.startsAt);
            OffsetDateTime parsedEnd = OffsetDateTime.parse(draft.endsAt);
            if (draft.title.isEmpty()) throw new IllegalArgumentException("Vul een titel in.");
            if (!draft.strategyWikiUrl.isEmpty() && !draft.strategyWikiUrl.startsWith("https://oldschool.runescape.wiki/"))
                throw new IllegalArgumentException("Gebruik een geldige link van https://oldschool.runescape.wiki/.");
            if (!parsedEnd.isAfter(parsedStart)) throw new IllegalArgumentException("De einddatum en eindtijd moeten na de start liggen.");
            long durationHours = Duration.between(parsedStart, parsedEnd).toHours();
            if (!parsedStart.toLocalDate().equals(parsedEnd.toLocalDate()) || durationHours >= 12)
            {
                int confirm = JOptionPane.showConfirmDialog(null,
                    "Let op: dit event eindigt op " + parsedEnd.toLocalDate() + " om " + parsedEnd.toLocalTime() +
                        " en duurt ongeveer " + durationHours + " uur. Klopt dit?",
                    "Controleer de einddatum", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.YES_OPTION) continue;
            }
            if (!boss && draft.world.isEmpty())
            {
                JOptionPane.showMessageDialog(null, "Een learner- of mass-event heeft een wereld nodig.", "Controle", JOptionPane.WARNING_MESSAGE); continue;
            }
            if (boss && draft.codeword.isEmpty())
            {
                JOptionPane.showMessageDialog(null, "Een boss-event heeft een codewoord nodig.", "Controle", JOptionPane.WARNING_MESSAGE); continue;
            }
            if (!boss) draft.codeword = "";
            if (!supportsPreparation) draft.checklist = "";
            if (!supportsPreparation) draft.requiredPlugins = "";
            if (!supportsPreparation) draft.strategyWikiUrl = "";
            if (boss) draft.world = "";
            return draft;
        }
            catch (IllegalArgumentException e)
            {
                JOptionPane.showMessageDialog(null, e.getMessage(), "Ongeldige invoer", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    static LocalDateTime parseDateTime(String value, String fieldName)
    {
        String text = value == null ? "" : value.trim();
        for (DateTimeFormatter formatter : ACCEPTED_INPUTS)
        {
            try { return LocalDateTime.parse(text, formatter); }
            catch (DateTimeParseException ignored) { }
        }
        throw new IllegalArgumentException("Controleer de " + fieldName + ". Gebruik bijvoorbeeld 2026-08-01 20:00.");
    }

    private static void add(JPanel panel, String label, java.awt.Component field) { panel.add(new JLabel(label)); panel.add(field); }
}

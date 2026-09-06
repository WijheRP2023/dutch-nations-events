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

    static EventDraft show(List<String> pluginCatalog, boolean learnerOnly) { return show(pluginCatalog, learnerOnly, null); }

    static EventDraft edit(List<String> pluginCatalog, boolean learnerOnly, ClanFeed.ClanEvent existing)
    {
        return show(pluginCatalog, learnerOnly, existing);
    }

    private static EventDraft show(List<String> pluginCatalog, boolean learnerOnly, ClanFeed.ClanEvent existing)
    {
        boolean editing = existing != null;
        JComboBox<String> type = new JComboBox<>(learnerOnly ? new String[]{"LEARNER"} : new String[]{"LEARNER", "BOSS", "MASS"});
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
            "", "Chambers of Xeric (CoX)", "Theatre of Blood (ToB)", "Tombs of Amascut (ToA)",
            "General Graardor (Bandos)", "Commander Zilyana (Saradomin)", "Kree'arra (Armadyl)",
            "K'ril Tsutsaroth (Zamorak)", "Callisto", "Vet'ion", "Venenatis", "Artio", "Calvar'ion",
            "Spindel", "King Black Dragon (KBD)", "Nex", "Dagannoth Kings", "Corporeal Beast",
            "Sarachnis", "The Nightmare", "Hueycoatl", "Royal Titans", "Scurrius",
            "God Wars Dungeon", "Wilderness bosses"
        });
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

        if (editing)
        {
            type.setSelectedItem(existing.type.toUpperCase(java.util.Locale.ROOT));
            type.setEnabled(false);
            title.setText(existing.title == null ? "" : existing.title);
            start.setText(existing.start().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime().format(INPUT));
            end.setText(existing.end().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime().format(INPUT));
            world.setText(existing.world == null ? "" : existing.world);
            host.setText(existing.host == null ? "" : existing.host);
            description.setText(existing.description == null ? "" : existing.description);
            checklist.setText(existing.checklist == null ? "" : existing.checklist);
            requiredPlugins.setText(existing.requiredPlugins == null ? "" : existing.requiredPlugins);
            for (int i = 0; i < strategyWiki.getItemCount(); i++)
            {
                String name = strategyWiki.getItemAt(i);
                if (strategyWikiUrl(name).equals(existing.strategyWikiUrl)) { strategyWiki.setSelectedItem(name); break; }
            }
        }

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
        add(form, "Strategie (snelkeuze)", strategyWiki);
        add(form, editing ? "Codewoord (boss; leeg = behouden)" : "Codewoord (alleen boss)", codeword);

        while (true)
        {
            if (JOptionPane.showConfirmDialog(null, form, editing ? "Dutch Nations - event aanpassen" : "Dutch Nations - event maken",
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
            draft.strategyWikiUrl = selectedStrategy == null ? "" : strategyWikiUrl(selectedStrategy.toString());
            boolean learner = "LEARNER".equals(draft.type);
            boolean boss = "BOSS".equals(draft.type);
            boolean supportsPreparation = !boss;
            OffsetDateTime parsedStart = OffsetDateTime.parse(draft.startsAt);
            OffsetDateTime parsedEnd = OffsetDateTime.parse(draft.endsAt);
            if (draft.title.isEmpty()) throw new IllegalArgumentException("Vul een titel in.");
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
            if (boss && draft.codeword.isEmpty() && !editing)
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

    static String strategyWikiUrl(String name)
    {
        if (name == null) return "";
        switch (name.trim())
        {
            case "Chambers of Xeric (CoX)": return "https://oldschool.runescape.wiki/w/Chambers_of_Xeric/Strategies";
            case "Theatre of Blood (ToB)": return "https://oldschool.runescape.wiki/w/Theatre_of_Blood/Strategies";
            case "Tombs of Amascut (ToA)": return "https://oldschool.runescape.wiki/w/Tombs_of_Amascut/Strategies";
            case "General Graardor (Bandos)": return "https://oldschool.runescape.wiki/w/General_Graardor/Strategies";
            case "Commander Zilyana (Saradomin)": return "https://oldschool.runescape.wiki/w/Commander_Zilyana/Strategies";
            case "Kree'arra (Armadyl)": return "https://oldschool.runescape.wiki/w/Kree%27arra/Strategies";
            case "K'ril Tsutsaroth (Zamorak)": return "https://oldschool.runescape.wiki/w/K%27ril_Tsutsaroth/Strategies";
            case "Callisto": return "https://oldschool.runescape.wiki/w/Callisto/Strategies";
            case "Vet'ion": return "https://oldschool.runescape.wiki/w/Vet%27ion/Strategies";
            case "Venenatis": return "https://oldschool.runescape.wiki/w/Venenatis/Strategies";
            case "Artio": return "https://oldschool.runescape.wiki/w/Artio/Strategies";
            case "Calvar'ion": return "https://oldschool.runescape.wiki/w/Calvar%27ion/Strategies";
            case "Spindel": return "https://oldschool.runescape.wiki/w/Spindel/Strategies";
            case "King Black Dragon (KBD)": return "https://oldschool.runescape.wiki/w/King_Black_Dragon/Strategies";
            case "Nex": return "https://oldschool.runescape.wiki/w/Nex/Strategies";
            case "Dagannoth Kings": return "https://oldschool.runescape.wiki/w/Dagannoth_Kings/Strategies";
            case "Corporeal Beast": return "https://oldschool.runescape.wiki/w/Corporeal_Beast/Strategies";
            case "Sarachnis": return "https://oldschool.runescape.wiki/w/Sarachnis/Strategies";
            case "The Nightmare": return "https://oldschool.runescape.wiki/w/The_Nightmare/Strategies";
            case "Hueycoatl": return "https://oldschool.runescape.wiki/w/The_Hueycoatl/Strategies";
            case "Royal Titans": return "https://oldschool.runescape.wiki/w/Royal_Titans/Strategies";
            case "Scurrius": return "https://oldschool.runescape.wiki/w/Scurrius/Strategies";
            case "God Wars Dungeon": return "https://oldschool.runescape.wiki/w/God_Wars_Dungeon";
            case "Wilderness bosses": return "https://oldschool.runescape.wiki/w/Wilderness_bosses";
            default: return "";
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

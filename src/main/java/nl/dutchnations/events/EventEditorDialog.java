package nl.dutchnations.events;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Window;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.JTextArea;
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
        JComboBox<String> type = new JComboBox<>(learnerOnly ? new String[]{"LEARNER"} : new String[]{"LEARNER", "BOSS", "MASS", "CLAN_EVENT", "CLAN_VS_CLAN"});
        JTextField title = new JTextField();
        LocalDateTime defaultStart = LocalDateTime.now().plusDays(1).withSecond(0).withNano(0);
        LocalDateTime defaultEnd = defaultStart.plusHours(2);
        JTextField startDate = new JTextField(defaultStart.toLocalDate().toString());
        JTextField startTime = new JTextField(defaultStart.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        JTextField endDate = new JTextField(defaultEnd.toLocalDate().toString());
        JTextField endTime = new JTextField(defaultEnd.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        JTextField world = new JTextField("366");
        JTextField host = new JTextField();
        JTextField description = new JTextField();
        JTextField clansOne = new JTextField();
        JTextField clansTwo = new JTextField();
        JTextField activity = new JTextField();
        JTextField bossList = new JTextField();
        JTextField codeword = new JTextField();
        JTextField driveUrl = new JTextField();
        JTextField registrationUrl = new JTextField();
        JTextField youtubeUrl = new JTextField();
        JTextField registrationDeadlineDate = new JTextField(defaultStart.toLocalDate().toString());
        JTextField registrationDeadlineTime = new JTextField(defaultStart.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
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
            LocalDateTime existingStart = existing.start().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            LocalDateTime existingEnd = existing.end().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            startDate.setText(existingStart.toLocalDate().toString()); startTime.setText(existingStart.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            endDate.setText(existingEnd.toLocalDate().toString()); endTime.setText(existingEnd.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            world.setText(existing.world == null ? "" : existing.world);
            host.setText(existing.host == null ? "" : existing.host);
            description.setText(existing.description == null ? "" : existing.description);
            clansOne.setText(existing.clansOne == null ? "" : existing.clansOne);
            clansTwo.setText(existing.clansTwo == null ? "" : existing.clansTwo);
            activity.setText(existing.activity == null ? "" : existing.activity);
            bossList.setText(existing.bossList == null ? "" : existing.bossList);
            driveUrl.setText(existing.driveUrl == null ? "" : existing.driveUrl);
            registrationUrl.setText(existing.registrationUrl == null ? "" : existing.registrationUrl);
            youtubeUrl.setText(existing.youtubeUrl == null ? "" : existing.youtubeUrl);
            if (existing.registrationEndsAt != null && !existing.registrationEndsAt.trim().isEmpty())
            {
                LocalDateTime registrationEnd = OffsetDateTime.parse(existing.registrationEndsAt).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
                registrationDeadlineDate.setText(registrationEnd.toLocalDate().toString());
                registrationDeadlineTime.setText(registrationEnd.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            }
            checklist.setText(existing.checklist == null ? "" : existing.checklist);
            requiredPlugins.setText(existing.requiredPlugins == null ? "" : existing.requiredPlugins);
            for (int i = 0; i < strategyWiki.getItemCount(); i++)
            {
                String name = strategyWiki.getItemAt(i);
                if (strategyWikiUrl(name).equals(existing.strategyWikiUrl)) { strategyWiki.setSelectedItem(name); break; }
            }
        }

        JCheckBox massCodewordRequired = new JCheckBox("Codewoord nodig");
        if (editing) massCodewordRequired.setSelected(existing.codewordRequired);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        JPanel worldRow = row("Wereld (learner/mass)", world);
        JPanel preparationRow = row("Voorbereiding (; gescheiden)", checklist);
        JPanel pluginSearchRow = row("Plugin zoeken (min. 2 tekens)", pluginSearch);
        JPanel pluginResultsRow = row("Gevonden Plugin Hub-plugin", pluginResults);
        JPanel addPluginRow = row("", addPlugin);
        JPanel selectedPluginsRow = row("Gekozen plug-ins", requiredPlugins);
        JPanel strategyRow = row("Strategie (snelkeuze)", strategyWiki);
        JPanel massCodewordRow = row("Mass-event", massCodewordRequired);
        JPanel driveRow = row("Tussenstand-link (optioneel)", driveUrl);
        JPanel registrationLinkRow = row("Discord-aanmeldlink", registrationUrl);
        JPanel youtubeRow = row("YouTube-link (optioneel)", youtubeUrl);
        JPanel registrationDeadlineRow = row("Aanmelden tot", dateTimeFields(registrationDeadlineDate, registrationDeadlineTime));
        JPanel codewordRow = row(editing ? "Codewoord (leeg = behouden)" : "Codewoord", codeword);
        JPanel titleRow = row("Titel", title);
        JPanel clansOneRow = row("Clans kant 1 (; gescheiden)", clansOne);
        JPanel clansTwoRow = row("Clans kant 2 (; gescheiden)", clansTwo);
        JPanel activityRow = row("Activiteit", activity);
        JPanel bossListRow = row("Bosses/activiteiten (; gescheiden)", bossList);
        form.add(row("Eventtype", type)); form.add(titleRow);
        form.add(row("Startdatum/tijd", dateTimeFields(startDate, startTime))); form.add(row("Einddatum/tijd", dateTimeFields(endDate, endTime)));
        form.add(worldRow); form.add(clansOneRow); form.add(clansTwoRow); form.add(activityRow); form.add(bossListRow);
        form.add(row("Host (RSN)", host)); form.add(row("Beschrijving", description)); form.add(youtubeRow);
        form.add(preparationRow); form.add(pluginSearchRow); form.add(pluginResultsRow); form.add(addPluginRow);
        form.add(selectedPluginsRow); form.add(strategyRow); form.add(massCodewordRow); form.add(driveRow); form.add(registrationLinkRow); form.add(registrationDeadlineRow); form.add(codewordRow);

        Runnable applyTypeRules = () ->
        {
            boolean boss = "BOSS".equals(type.getSelectedItem());
            boolean mass = "MASS".equals(type.getSelectedItem());
            boolean learner = "LEARNER".equals(type.getSelectedItem());
            boolean clanEvent = "CLAN_EVENT".equals(type.getSelectedItem());
            boolean clanVsClan = "CLAN_VS_CLAN".equals(type.getSelectedItem());
            boolean supportsResources = learner || mass;
            boolean needsCodeword = boss || clanEvent || clanVsClan || (mass && massCodewordRequired.isSelected());
            titleRow.setVisible(!clanVsClan);
            worldRow.setVisible(!boss && !clanVsClan);
            clansOneRow.setVisible(clanVsClan); clansTwoRow.setVisible(clanVsClan); activityRow.setVisible(clanVsClan); bossListRow.setVisible(clanEvent);
            preparationRow.setVisible(learner);
            pluginSearchRow.setVisible(supportsResources); pluginResultsRow.setVisible(supportsResources);
            addPluginRow.setVisible(supportsResources); selectedPluginsRow.setVisible(supportsResources); strategyRow.setVisible(supportsResources);
            massCodewordRow.setVisible(mass); driveRow.setVisible(boss || clanEvent || clanVsClan); registrationLinkRow.setVisible(boss || clanEvent || clanVsClan); registrationDeadlineRow.setVisible(boss || clanEvent || clanVsClan); codewordRow.setVisible(needsCodeword);
            if (!needsCodeword) codeword.setText("");
            if (!boss && !clanEvent && !clanVsClan) { driveUrl.setText(""); registrationUrl.setText(""); }
            if (!learner) checklist.setText("");
            if (!supportsResources) { requiredPlugins.setText(""); strategyWiki.setSelectedItem(""); }
            if (!clanVsClan) { clansOne.setText(""); clansTwo.setText(""); activity.setText(""); }
            if (!clanEvent) bossList.setText("");
            if (boss || clanVsClan) world.setText("");
            if ((mass || clanEvent) && world.getText().trim().isEmpty()) world.setText("366");
            form.revalidate(); form.repaint();
            repackParentWindow(form);
        };
        type.addActionListener(e -> applyTypeRules.run());
        massCodewordRequired.addActionListener(e -> applyTypeRules.run());
        applyTypeRules.run();

        while (true)
        {
            if (JOptionPane.showConfirmDialog(null, form, editing ? "Dutch Nation - event aanpassen" : "Dutch Nation - event maken",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return null;
        try
        {
            EventDraft draft = new EventDraft();
            draft.type = (String) type.getSelectedItem();
            boolean clanVsClan = "CLAN_VS_CLAN".equals(draft.type);
            draft.clansOne = clansOne.getText().trim(); draft.clansTwo = clansTwo.getText().trim(); draft.activity = activity.getText().trim(); draft.bossList = bossList.getText().trim();
            draft.title = clanVsClan ? draft.activity : title.getText().trim();
            LocalDateTime localStart = parseDateTime(startDate.getText() + " " + startTime.getText(), "startdatum en starttijd");
            LocalDateTime localEnd = parseDateTime(endDate.getText() + " " + endTime.getText(), "einddatum en eindtijd");
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
                        endDate.setText(suggestedEnd.toLocalDate().toString()); endTime.setText(suggestedEnd.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                    }
                }
            }
            draft.startsAt = localStart.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString();
            draft.endsAt = localEnd.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString();
            draft.world = world.getText().trim(); draft.host = host.getText().trim();
            draft.description = description.getText().trim(); draft.codeword = codeword.getText().trim(); draft.driveUrl = driveUrl.getText().trim();
            draft.registrationUrl = registrationUrl.getText().trim();
            draft.youtubeUrl = youtubeUrl.getText().trim();
            if (!draft.registrationUrl.isEmpty())
            {
                LocalDateTime registrationEnd = parseDateTime(registrationDeadlineDate.getText() + " " + registrationDeadlineTime.getText(), "aanmelddeadline");
                draft.registrationEndsAt = registrationEnd.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString();
            }
            draft.checklist = checklist.getText().trim();
            draft.requiredPlugins = requiredPlugins.getText().trim();
            draft.codewordRequired = "BOSS".equals(draft.type) || "CLAN_EVENT".equals(draft.type) || "CLAN_VS_CLAN".equals(draft.type) || ("MASS".equals(draft.type) && massCodewordRequired.isSelected());
            Object selectedStrategy = strategyWiki.getEditor().getItem();
            draft.strategyWikiUrl = selectedStrategy == null ? "" : strategyWikiUrl(selectedStrategy.toString());
            boolean boss = "BOSS".equals(draft.type);
            boolean mass = "MASS".equals(draft.type);
            boolean clanEvent = "CLAN_EVENT".equals(draft.type);
            boolean requiresCodeword = draft.codewordRequired;
            boolean learner = "LEARNER".equals(draft.type);
            boolean supportsResources = learner || mass;
            OffsetDateTime parsedStart = OffsetDateTime.parse(draft.startsAt);
            OffsetDateTime parsedEnd = OffsetDateTime.parse(draft.endsAt);
            if ((boss || clanEvent || clanVsClan) && !draft.registrationUrl.isEmpty() && OffsetDateTime.parse(draft.registrationEndsAt).isAfter(parsedStart))
                throw new IllegalArgumentException("De aanmelddeadline mag niet na de starttijd liggen.");
            if (draft.title.isEmpty()) throw new IllegalArgumentException(clanVsClan ? "Vul een activiteit in." : "Vul een titel in.");
            if (clanVsClan && (draft.clansOne.isEmpty() || draft.clansTwo.isEmpty())) throw new IllegalArgumentException("Vul beide clanlijsten in.");
            if (clanEvent && draft.bossList.isEmpty()) throw new IllegalArgumentException("Vul minimaal één boss of activiteit in.");
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
            if ((!boss && !clanVsClan) && draft.world.isEmpty())
            {
                JOptionPane.showMessageDialog(null, "Een learner-, mass- of clan-event heeft een wereld nodig.", "Controle", JOptionPane.WARNING_MESSAGE); continue;
            }
            if (draft.host.isEmpty())
            {
                markInvalid(host);
                JOptionPane.showMessageDialog(null, "Vul voor elk event een host in.", "Controle", JOptionPane.WARNING_MESSAGE); continue;
            }
            if (requiresCodeword && draft.codeword.isEmpty() && !editing)
            {
                markInvalid(codeword);
                JOptionPane.showMessageDialog(null, "Een boss-, mass- of clan-event heeft een codewoord nodig.", "Controle", JOptionPane.WARNING_MESSAGE); continue;
            }
            if (!requiresCodeword) draft.codeword = "";
            if (!learner) draft.checklist = "";
            if (!supportsResources) { draft.requiredPlugins = ""; draft.strategyWikiUrl = ""; }
            if (!clanVsClan) { draft.clansOne = ""; draft.clansTwo = ""; draft.activity = ""; }
            if (!clanEvent) draft.bossList = "";
            if (boss || clanVsClan) draft.world = "";
            if (!boss && !clanEvent && !clanVsClan) { draft.driveUrl = ""; draft.registrationUrl = ""; draft.registrationEndsAt = ""; }
            if (!editing)
            {
                JTextArea discordText = new JTextArea(4, 24);
                discordText.setLineWrap(true);
                discordText.setWrapStyleWord(true);
                int extraTextAnswer = JOptionPane.showConfirmDialog(null, discordText,
                    "Discord-beschrijving (optioneel)", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (extraTextAnswer == JOptionPane.OK_OPTION) draft.discordText = discordText.getText().trim();
            }
            return draft;
        }
            catch (IllegalArgumentException e)
            {
                JOptionPane.showMessageDialog(null, e.getMessage(), "Ongeldige invoer", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static void markInvalid(JTextField field)
    {
        field.setBorder(BorderFactory.createLineBorder(new Color(215, 60, 60), 2));
        field.requestFocusInWindow();
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

    private static void repackParentWindow(JPanel form)
    {
        Window window = SwingUtilities.getWindowAncestor(form);
        if (window != null)
        {
            window.pack();
            window.setLocationRelativeTo(null);
        }
    }

    private static JPanel dateTimeFields(JTextField date, JTextField time)
    {
        JPanel fields = new JPanel(new GridLayout(1, 2, 6, 0));
        fields.add(date); fields.add(time); return fields;
    }

    private static JPanel row(String label, java.awt.Component field)
    {
        JPanel row = new JPanel(new GridLayout(1, 2, 6, 6));
        row.add(new JLabel(label)); row.add(field);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

}

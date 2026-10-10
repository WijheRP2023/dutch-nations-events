package nl.dutchnations.events;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.Arrays;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

final class LearnerTemplateEditorDialog
{
    private LearnerTemplateEditorDialog() { }

    static ClanFeed.LearnerTemplate create(List<String> pluginCatalog) { return show(null, pluginCatalog); }
    static ClanFeed.LearnerTemplate edit(ClanFeed.LearnerTemplate existing, List<String> pluginCatalog) { return show(existing, pluginCatalog); }

    private static ClanFeed.LearnerTemplate show(ClanFeed.LearnerTemplate existing, List<String> pluginCatalog)
    {
        boolean editing = existing != null;
        JTextField name = new JTextField(editing ? value(existing.name) : "");
        JTextField title = new JTextField(editing ? value(existing.title) : "");
        JTextArea description = textArea(editing ? value(existing.description) : "");
        JTextArea discordDescription = textArea(editing ? value(existing.discordDescription) : "");
        JTextField checklist = new JTextField(editing ? value(existing.checklist) : "");
        JTextField requiredPlugins = new JTextField(editing ? value(existing.requiredPlugins) : "");
        JTextField pluginSearch = new JTextField();
        DefaultComboBoxModel<String> pluginResultsModel = new DefaultComboBoxModel<>();
        JComboBox<String> pluginResults = new JComboBox<>(pluginResultsModel);
        JButton addPlugin = new JButton("Plugin toevoegen");
        JTextField wiki = new JTextField(editing ? value(existing.strategyWikiUrl) : "");
        JTextField youtube = new JTextField(editing ? value(existing.youtubeUrl) : "");

        pluginSearch.getDocument().addDocumentListener(new DocumentListener()
        {
            private void update()
            {
                String query = pluginSearch.getText().trim().toLowerCase(java.util.Locale.ROOT);
                pluginResultsModel.removeAllElements();
                if (query.length() < 2 || pluginCatalog == null) return;
                pluginCatalog.stream().filter(name -> name.toLowerCase(java.util.Locale.ROOT).contains(query))
                    .limit(12).forEach(pluginResultsModel::addElement);
            }
            @Override public void insertUpdate(DocumentEvent event) { update(); }
            @Override public void removeUpdate(DocumentEvent event) { update(); }
            @Override public void changedUpdate(DocumentEvent event) { update(); }
        });
        addPlugin.addActionListener(event ->
        {
            Object selected = pluginResults.getSelectedItem();
            if (selected == null) return;
            String pluginName = selected.toString();
            String current = requiredPlugins.getText().trim();
            boolean exists = Arrays.stream(current.split(";"))
                .anyMatch(value -> value.trim().equalsIgnoreCase(pluginName));
            if (!exists) requiredPlugins.setText(current.isEmpty() ? pluginName : current + "; " + pluginName);
            pluginSearch.setText("");
        });

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(row("Naam in de lijst", name));
        form.add(row("Titel van het event", title));
        form.add(row("Beschrijving", scrollableText(description)));
        form.add(row("Standaard Discord-beschrijving", scrollableText(discordDescription)));
        form.add(row("Voorbereiding (; gescheiden)", checklist));
        form.add(row("Plugin zoeken (min. 2 tekens)", pluginSearch));
        form.add(row("Gevonden Plugin Hub-plugin", pluginResults));
        form.add(row("", addPlugin));
        form.add(row("Benodigde plug-ins (; gescheiden)", requiredPlugins));
        form.add(row("OSRS Wiki-link", wiki));
        form.add(row("YouTube-link (optioneel)", youtube));

        JScrollPane formScroll = new JScrollPane(form);
        formScroll.setPreferredSize(new Dimension(760, 500));
        while (JOptionPane.showConfirmDialog(null, formScroll, editing ? "Learner-sjabloon aanpassen" : "Nieuw learner-sjabloon",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION)
        {
            ClanFeed.LearnerTemplate template = new ClanFeed.LearnerTemplate();
            template.name = name.getText().trim();
            template.title = title.getText().trim();
            template.description = description.getText().trim();
            template.discordDescription = discordDescription.getText().trim();
            template.checklist = checklist.getText().trim();
            template.requiredPlugins = requiredPlugins.getText().trim();
            template.strategyWikiUrl = wiki.getText().trim();
            template.youtubeUrl = youtube.getText().trim();
            if (template.name.isEmpty() || template.title.isEmpty())
            {
                JOptionPane.showMessageDialog(null, "Vul een naam en eventtitel in.", "Controle", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            if (!template.strategyWikiUrl.startsWith("https://oldschool.runescape.wiki/"))
            {
                JOptionPane.showMessageDialog(null, "Gebruik een geldige Old School Wiki-link.", "Controle", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            if (!template.youtubeUrl.isEmpty() && !(template.youtubeUrl.startsWith("https://www.youtube.com/") || template.youtubeUrl.startsWith("https://youtu.be/")))
            {
                JOptionPane.showMessageDialog(null, "Gebruik een geldige YouTube-link of laat dit veld leeg.", "Controle", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            return template;
        }
        return null;
    }

    private static JTextArea textArea(String value)
    {
        JTextArea area = new JTextArea(value, 3, 20);
        area.setLineWrap(true); area.setWrapStyleWord(true);
        return area;
    }

    private static JScrollPane scrollableText(JTextArea area)
    {
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(360, 72));
        return scroll;
    }

    private static JPanel row(String label, java.awt.Component field)
    {
        JPanel row = new JPanel(new GridLayout(1, 2, 6, 6));
        row.add(new JLabel(label)); row.add(field);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(row.getPreferredSize().height, 32)));
        return row;
    }

    private static String value(String text) { return text == null ? "" : text; }
}

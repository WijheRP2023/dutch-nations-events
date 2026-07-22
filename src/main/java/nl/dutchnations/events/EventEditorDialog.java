package nl.dutchnations.events;

import java.awt.GridLayout;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

final class EventEditorDialog
{
    private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private EventEditorDialog() { }

    static EventDraft show()
    {
        JComboBox<String> type = new JComboBox<>(new String[]{"LEARNER", "BOSS", "MASS"});
        JTextField title = new JTextField();
        JTextField start = new JTextField(LocalDateTime.now().plusDays(1).withSecond(0).withNano(0).format(INPUT));
        JTextField end = new JTextField(LocalDateTime.now().plusDays(1).plusHours(2).withSecond(0).withNano(0).format(INPUT));
        JTextField world = new JTextField("366");
        JTextField host = new JTextField();
        JTextField description = new JTextField();
        JTextField codeword = new JTextField();

        type.addActionListener(e ->
        {
            boolean learner = "LEARNER".equals(type.getSelectedItem());
            boolean boss = "BOSS".equals(type.getSelectedItem());
            world.setEnabled(!boss);
            codeword.setEnabled(boss);
            if (!boss) codeword.setText("");
            if (boss) world.setText("");
            if ("MASS".equals(type.getSelectedItem()) && world.getText().trim().isEmpty()) world.setText("366");
        });

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        add(form, "Soort", type); add(form, "Titel", title); add(form, "Start (lokale tijd)", start);
        add(form, "Einde (lokale tijd)", end); add(form, "Wereld (learner/mass)", world); add(form, "Host", host);
        add(form, "Omschrijving", description); add(form, "Codewoord (alleen boss)", codeword);

        if (JOptionPane.showConfirmDialog(null, form, "Dutch Nations - event maken",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return null;
        try
        {
            EventDraft draft = new EventDraft();
            draft.type = (String) type.getSelectedItem(); draft.title = title.getText().trim();
            draft.startsAt = LocalDateTime.parse(start.getText().trim(), INPUT).atZone(ZoneId.systemDefault()).toOffsetDateTime().toString();
            draft.endsAt = LocalDateTime.parse(end.getText().trim(), INPUT).atZone(ZoneId.systemDefault()).toOffsetDateTime().toString();
            draft.world = world.getText().trim(); draft.host = host.getText().trim();
            draft.description = description.getText().trim(); draft.codeword = codeword.getText().trim();
            boolean learner = "LEARNER".equals(draft.type);
            boolean boss = "BOSS".equals(draft.type);
            OffsetDateTime parsedStart = OffsetDateTime.parse(draft.startsAt);
            OffsetDateTime parsedEnd = OffsetDateTime.parse(draft.endsAt);
            if (draft.title.isEmpty() || !parsedEnd.isAfter(parsedStart)) throw new IllegalArgumentException();
            long durationHours = Duration.between(parsedStart, parsedEnd).toHours();
            if (!parsedStart.toLocalDate().equals(parsedEnd.toLocalDate()) || durationHours >= 12)
            {
                int confirm = JOptionPane.showConfirmDialog(null,
                    "Let op: dit event eindigt op " + parsedEnd.toLocalDate() + " om " + parsedEnd.toLocalTime() +
                        " en duurt ongeveer " + durationHours + " uur. Klopt dit?",
                    "Controleer de einddatum", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.YES_OPTION) return null;
            }
            if (!boss && draft.world.isEmpty())
            {
                JOptionPane.showMessageDialog(null, "Een learner- of mass-event heeft een wereld nodig.", "Controle", JOptionPane.WARNING_MESSAGE); return null;
            }
            if (boss && draft.codeword.isEmpty())
            {
                JOptionPane.showMessageDialog(null, "Een boss-event heeft een codewoord nodig.", "Controle", JOptionPane.WARNING_MESSAGE); return null;
            }
            if (!boss) draft.codeword = "";
            if (boss) draft.world = "";
            return draft;
        }
        catch (RuntimeException e)
        {
            JOptionPane.showMessageDialog(null, "Controleer titel, datum en tijden.", "Ongeldige invoer", JOptionPane.ERROR_MESSAGE); return null;
        }
    }

    private static void add(JPanel panel, String label, java.awt.Component field) { panel.add(new JLabel(label)); panel.add(field); }
}

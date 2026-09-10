package nl.dutchnations.events;

import java.awt.GridLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

final class RoleEditorDialog
{
    private RoleEditorDialog() { }

    static RoleDraft show(boolean owner, boolean administrator)
    {
        JTextField rsn = new JTextField();
        String[] choices = owner ? new String[]{"MANAGER", "ADMINISTRATOR", "TEACHER"} :
            (administrator ? new String[]{"MANAGER", "TEACHER"} : new String[0]);
        JComboBox<String> role = new JComboBox<>(choices);
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Exacte RuneScape-naam")); form.add(rsn);
        form.add(new JLabel("Nieuwe rol")); form.add(role);
        int result = JOptionPane.showConfirmDialog(null, form, "Dutch Nation - rollen beheren",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION || rsn.getText().trim().isEmpty()) return null;
        return new RoleDraft(rsn.getText().trim(), (String) role.getSelectedItem());
    }
}

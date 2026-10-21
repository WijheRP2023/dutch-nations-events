package nl.dutchnations.events;

import java.awt.Component;
import java.awt.Container;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class DutchNationsPanelTest
{
    @Test public void ownerCanConfigureScreenshotChannelWithoutTestFlags() throws Exception
    {
        assertFalse(Boolean.getBoolean("dutch.nations.local"));
        assertFalse(Boolean.getBoolean("dutch.nations.dropTest"));
        SwingUtilities.invokeAndWait(() ->
        {
            DutchNationsPanel panel = panel(true);
            JButton settings = findButton(panel, "Instellingen");
            assertNotNull(settings);
            settings.doClick();
            assertNotNull(findButton(panel, "Screenshotkanaal instellen"));
            ClanFeed feed = new ClanFeed();
            feed.dropChannelUrl = "https://discord.com/channels/123/456";
            panel.update(feed, "Geladen");
            panel.dropChannelSaved("https://discord.com/channels/123/789");
            assertEquals("https://discord.com/channels/123/789", feed.dropChannelUrl);
            assertFalse(new DutchNationsConfig() { }.shareDropScreenshots());
        });
    }

    @Test public void ordinaryMemberDoesNotGetOwnerSettings() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            DutchNationsPanel panel = panel(false);
            assertNull(findButton(panel, "Instellingen"));
            assertNull(findButton(panel, "Screenshotkanaal instellen"));
        });
    }

    private static DutchNationsPanel panel(boolean owner)
    {
        return new DutchNationsPanel(new DutchNationsConfig() { }, () -> { }, () -> owner, () -> owner,
            () -> false, () -> false, () -> owner, () -> { }, () -> { }, () -> true,
            event -> { }, (id, event) -> { }, id -> { }, template -> { }, (id, template) -> { }, id -> { },
            role -> { }, url -> { }, url -> { }, url -> { }, visible -> { }, () -> 0L, sequence -> { }, () -> "Tester");
    }

    private static JButton findButton(Container container, String title)
    {
        for (Component component : container.getComponents())
        {
            if (component instanceof JButton && title.equals(((JButton) component).getText())) return (JButton) component;
            if (component instanceof Container)
            {
                JButton found = findButton((Container) component, title);
                if (found != null) return found;
            }
        }
        return null;
    }
}

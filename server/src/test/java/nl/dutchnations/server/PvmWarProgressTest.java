package nl.dutchnations.server;

import org.junit.Test;
import static org.junit.Assert.*;

public class PvmWarProgressTest
{
    private static String row(String name, String first, String second, String third)
    {
        return "<tr data-item=\"" + name + "\"><td class=\"col-item\"><span class=\"item-name__title\">"
            + name + "</span></td><td class=\"" + first + "\"></td><td class=\"" + second
            + "\"></td><td class=\"" + third + "\"></td></tr>";
    }

    @Test public void countsApprovedAndWaitingSeparately() throws Exception
    {
        PvmWarProgress progress = PvmWarProgress.parse("<table>"
            + row("Dragon claws", "approved", "approved", "waiting")
            + row("Twisted bow", "approved", "approved", "approved")
            + row("Empty item", "", "", "") + "</table>");
        assertEquals(2, progress.lookup("dragon claws").approved);
        assertEquals(1, progress.lookup("dragon claws").waiting);
        assertEquals(3, progress.lookup("Twisted bow").approved);
        assertEquals(0, progress.lookup("Empty item").approved);
        assertNull(progress.lookup("Missing item"));
    }

    @Test public void jarsShareTheirTierAndUnknownNamesAreNotAccepted() throws Exception
    {
        PvmWarProgress progress = PvmWarProgress.parse("<table>"
            + row("Jar Tier 1", "approved", "", "")
            + row("Jar Tier 2", "approved", "approved", "approved")
            + row("Jar Tier 3", "", "", "") + "</table>");
        assertEquals(1, progress.lookup("Jar of dreams").approved);
        assertEquals(1, progress.lookup("Jar of stone").approved);
        assertEquals(3, progress.lookup("Jar of venom").approved);
        assertEquals(0, progress.lookup("Jar of smoke").approved);
        assertNull(progress.lookup("Jar of unknown"));
    }

    @Test public void ambiguousOrChangedRowsAreNotAssumedEmpty() throws Exception
    {
        PvmWarProgress progress = PvmWarProgress.parse("<table>"
            + row("duplicate", "", "", "") + row("duplicate", "approved", "", "")
            + row("changed", "new-slot-class", "", "") + "</table>");
        assertNull(progress.lookup("duplicate"));
        assertNull(progress.lookup("changed"));
    }
}

package nl.dutchnations.server;

import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class DropGroupsTest
{
    @Test public void barrowsIncludesAllSixBrothersAndTheirWeapons()
    {
        Set<String> items = DropGroups.resolve("8x any barrows piece");
        assertNotNull(items);
        assertEquals(24, items.size());
        for (String weapon : new String[] {"Ahrim's staff", "Dharok's greataxe", "Guthan's warspear",
            "Karil's crossbow", "Torag's hammers", "Verac's flail"}) assertTrue(weapon, items.contains(weapon));
        assertTrue(items.contains("Guthan's chainskirt"));
        assertFalse(items.contains("Guthan's spear"));
        assertFalse(items.contains("Guthan's platelegs"));
        assertFalse(items.contains("Barrows gloves"));
        assertEquals(4, DropGroups.resolve("Full set Guthan").size());
        assertEquals(DropGroups.resolve("Guthan's set"), DropGroups.resolve("Guthans's set"));
        assertEquals(java.util.Collections.singleton("Guthan's warspear"), DropGroups.resolve("Guthan's spear"));
    }

    @Test public void moonsIncludesWeaponsWithoutRegularChestLoot()
    {
        Set<String> items = DropGroups.resolve("8x piece moons");
        assertEquals(12, items.size());
        assertTrue(items.contains("Dual macuahuitl"));
        assertTrue(items.contains("Blue moon spear"));
        assertTrue(items.contains("Eclipse atlatl"));
        assertTrue(items.contains("Blue moon helm"));
        assertFalse(items.contains("Atlatl dart"));
        assertEquals(4, DropGroups.resolve("blue moon set").size());
    }

    @Test public void resolvesEveryUniqueTaskInTheBattleshipSheet()
    {
        for (String label : new String[] {"2x Any tob unique", "3x Tormented demon Unique", "2x yama unique",
            "2x any doom unique", "any nightmare unique", "3x cerb unqiue", "Any Duke unique geen quartz",
            "any nex unique", "2x Toa unique", "any raid unique", "4x zulrah unique", "2x any cox unique",
            "Any Vardorvis unique geen quartz"})
        {
            assertNotNull(label, DropGroups.resolve(label));
            assertFalse(label, DropGroups.resolve(label).isEmpty());
        }
    }

    @Test public void raidGroupsContainDropsRatherThanCraftedEquipmentOrCommonLoot()
    {
        Set<String> cox = DropGroups.resolve("any CoX uniques");
        assertEquals(12, cox.size());
        assertTrue(cox.contains("Kodai insignia"));
        assertFalse(cox.contains("Kodai wand"));
        Set<String> raids = DropGroups.resolve("any raid unique");
        assertEquals(26, raids.size());
        assertTrue(raids.contains("Avernic defender hilt"));
        assertTrue(raids.contains("Scythe of vitur (uncharged)"));
        assertTrue(raids.contains("Tumeken's shadow (uncharged)"));
        assertFalse(raids.contains("Rune arrow"));
        assertFalse(raids.contains("Thread of elidinis"));
    }

    @Test public void bossGroupsUseActualDropVariantsAndRespectQuartzExclusion()
    {
        assertTrue(DropGroups.resolve("any nex unique").contains("Torva full helm (damaged)"));
        assertFalse(DropGroups.resolve("any nex unique").contains("Torva full helm"));
        assertTrue(DropGroups.resolve("any doom unique").contains("Eye of ayak (uncharged)"));
        assertTrue(DropGroups.resolve("any doom unique").contains("Mokhaiotl cloth"));
        assertTrue(DropGroups.resolve("any nightmare unique").contains("Inquisitor's great helm"));
        assertTrue(DropGroups.resolve("any Duke unique geen quartz").contains("Magus vestige"));
        assertFalse(DropGroups.resolve("any Duke unique geen quartz").contains("Ice quartz"));
        assertFalse(DropGroups.resolve("any Vardorvis unique geen quartz").contains("Blood quartz"));
        assertNull(DropGroups.resolve("any unknown boss unique"));
        assertNull(DropGroups.resolve("Blue moon helm"));
    }
}

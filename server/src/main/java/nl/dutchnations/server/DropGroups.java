package nl.dutchnations.server;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Server-owned translations from event task labels to concrete loot item names. */
final class DropGroups
{
    private static final Map<String, Set<String>> ALIASES = createAliases();

    private DropGroups() { }

    /** null means the label is not a registered group; ordinary matching can handle it. */
    static Set<String> resolve(String label)
    {
        return label == null ? null : ALIASES.get(key(label));
    }

    private static String key(String label)
    {
        return label.replace('\u2019', '\'').trim().toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", " ")
            .replaceFirst("^[0-9]+\\s*x\\s*", "")
            .replaceFirst("^any\\s+", "")
            .replaceAll("\\buniques\\b", "unique")
            .replaceAll("\\bunqiue\\b", "unique");
    }

    private static Map<String, Set<String>> createAliases()
    {
        Map<String, Set<String>> aliases = new LinkedHashMap<>();
        Set<String> ahrim = items("Ahrim's hood", "Ahrim's robetop", "Ahrim's robeskirt", "Ahrim's staff");
        Set<String> dharok = items("Dharok's helm", "Dharok's platebody", "Dharok's platelegs", "Dharok's greataxe");
        Set<String> guthan = items("Guthan's helm", "Guthan's platebody", "Guthan's chainskirt", "Guthan's warspear");
        Set<String> karil = items("Karil's coif", "Karil's leathertop", "Karil's leatherskirt", "Karil's crossbow");
        Set<String> torag = items("Torag's helm", "Torag's platebody", "Torag's platelegs", "Torag's hammers");
        Set<String> verac = items("Verac's helm", "Verac's brassard", "Verac's plateskirt", "Verac's flail");
        brother(aliases, ahrim, "ahrim");
        brother(aliases, dharok, "dharok");
        brother(aliases, guthan, "guthan");
        brother(aliases, karil, "karil");
        brother(aliases, torag, "torag");
        brother(aliases, verac, "verac");
        register(aliases, union(ahrim, dharok, guthan, karil, torag, verac),
            "barrows", "barrows piece", "barrows pieces", "barrows item", "barrows items", "barrows unique");
        // Historical spellings present in the supplied event spreadsheets.
        register(aliases, guthan, "guthans's set");
        register(aliases, items("Guthan's warspear"), "guthan's spear");
        register(aliases, items("Guthan's chainskirt"), "guthan's platelegs");

        Set<String> bloodMoon = items("Blood moon helm", "Blood moon chestplate", "Blood moon tassets", "Dual macuahuitl");
        Set<String> blueMoon = items("Blue moon helm", "Blue moon chestplate", "Blue moon tassets", "Blue moon spear");
        Set<String> eclipseMoon = items("Eclipse moon helm", "Eclipse moon chestplate", "Eclipse moon tassets", "Eclipse atlatl");
        register(aliases, bloodMoon, "blood moon set", "full blood moon", "blood moon unique", "blood moon piece", "blood moon pieces");
        register(aliases, blueMoon, "blue moon set", "full blue moon", "blue moon unique", "blue moon piece", "blue moon pieces");
        register(aliases, eclipseMoon, "eclipse moon set", "full eclipse moon", "eclipse moon unique", "eclipse moon piece", "eclipse moon pieces");
        register(aliases, union(bloodMoon, blueMoon, eclipseMoon), "moons", "moons of peril", "moons unique",
            "moons of peril unique", "moons piece", "moons pieces", "moon piece", "moon pieces", "piece moons", "pieces moons");

        Set<String> cox = items("Dexterous prayer scroll", "Arcane prayer scroll", "Twisted buckler",
            "Dragon hunter crossbow", "Dinh's bulwark", "Ancestral hat", "Ancestral robe top", "Ancestral robe bottom",
            "Dragon claws", "Elder maul", "Kodai insignia", "Twisted bow");
        Set<String> tob = items("Avernic defender hilt", "Ghrazi rapier", "Sanguinesti staff (uncharged)",
            "Justiciar faceguard", "Justiciar chestguard", "Justiciar legguards", "Scythe of vitur (uncharged)");
        Set<String> toa = items("Osmumten's fang", "Lightbearer", "Elidinis' ward", "Masori mask", "Masori body",
            "Masori chaps", "Tumeken's shadow (uncharged)");
        register(aliases, cox, "cox unique", "chambers unique", "chambers of xeric unique");
        register(aliases, tob, "tob unique", "theatre of blood unique");
        register(aliases, toa, "toa unique", "tombs of amascut unique");
        register(aliases, union(cox, tob, toa), "raid unique", "raids unique");

        register(aliases, items("Ancient hilt", "Nihil horn", "Zaryte vambraces", "Torva full helm (damaged)",
            "Torva platebody (damaged)", "Torva platelegs (damaged)"), "nex unique");
        register(aliases, items("Inquisitor's great helm", "Inquisitor's hauberk", "Inquisitor's plateskirt",
            "Inquisitor's mace", "Nightmare staff", "Volatile orb", "Harmonised orb", "Eldritch orb"),
            "nightmare unique", "the nightmare unique", "phosani unique", "phosani's nightmare unique");
        register(aliases, items("Oathplate helm", "Oathplate chest", "Oathplate legs", "Soulflame horn"), "yama unique");
        register(aliases, items("Avernic treads", "Eye of ayak (uncharged)", "Mokhaiotl cloth"),
            "doom unique", "doom of mokhaiotl unique", "mokhaiotl unique");
        register(aliases, items("Tormented synapse", "Burning claw"), "tormented demon unique", "tormented demons unique");
        register(aliases, items("Primordial crystal", "Pegasian crystal", "Eternal crystal", "Smouldering stone"),
            "cerb unique", "cerberus unique");
        register(aliases, items("Tanzanite fang", "Magic fang", "Serpentine visage", "Uncut onyx"), "zulrah unique");

        // Main unique table plus Awakener's orb. Quartz, one-time tablets, jars and pets are not included.
        register(aliases, items("Magus vestige", "Chromium ingot", "Eye of the duke", "Virtus mask",
            "Virtus robe top", "Virtus robe bottom", "Awakener's orb"),
            "duke unique", "duke sucellus unique", "duke unique geen quartz", "duke sucellus unique geen quartz");
        register(aliases, items("Ultor vestige", "Chromium ingot", "Executioner's axe head", "Virtus mask",
            "Virtus robe top", "Virtus robe bottom", "Awakener's orb"), "vardorvis unique", "vardorvis unique geen quartz");
        return Collections.unmodifiableMap(aliases);
    }

    private static void brother(Map<String, Set<String>> aliases, Set<String> items, String brother)
    {
        register(aliases, items, brother + " set", brother + "'s set", "full set " + brother,
            "full " + brother, brother + " piece", brother + " pieces", brother + " unique");
    }

    private static Set<String> items(String... names)
    {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(names)));
    }

    @SafeVarargs private static Set<String> union(Set<String>... groups)
    {
        Set<String> names = new LinkedHashSet<>();
        for (Set<String> group : groups) names.addAll(group);
        return Collections.unmodifiableSet(names);
    }

    private static void register(Map<String, Set<String>> aliases, Set<String> names, String... labels)
    {
        for (String label : labels)
        {
            if (aliases.putIfAbsent(key(label), names) != null)
                throw new IllegalStateException("Duplicate drop group alias: " + label);
        }
    }
}

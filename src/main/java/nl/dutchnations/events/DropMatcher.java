package nl.dutchnations.events;

import java.time.OffsetDateTime;

/** Exact names and explicit name-fragment rules supplied by Sheet bingo tasks. */
final class DropMatcher
{
    private DropMatcher() { }

    static boolean matches(ClanFeed.ClanEvent event, String item, OffsetDateTime now)
    { return matches(event, event == null ? null : event.dropItems, item, now); }

    static boolean matches(ClanFeed.ClanEvent event, String selectedItems, String item, OffsetDateTime now)
    {
        if (event == null || selectedItems == null || item == null || item.trim().isEmpty()) return false;
        try
        {
            if (!event.active(now)) return false;
            for (String candidate : selectedItems.split(";"))
                if (matchesItem(candidate, item)) return true;
        }
        catch (RuntimeException ignored) { return false; }
        return false;
    }
    static String normalize(String name)
    { return name.trim().replace('’', '\'').toLowerCase(java.util.Locale.ROOT); }

    static boolean matchesItem(String rule, String item)
    {
        if (rule == null || item == null || item.trim().isEmpty()) return false;
        String selected = normalize(rule);
        String name = normalize(item);
        if (selected.startsWith("@namecontains:"))
        {
            String fragment = selected.substring("@namecontains:".length()).trim();
            return !fragment.isEmpty() && java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}])" + java.util.regex.Pattern.quote(fragment) + "(?![\\p{L}\\p{N}])")
                .matcher(name).find();
        }
        if (!selected.startsWith("@contains:")) return selected.equals(name);
        return java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}'])" + java.util.regex.Pattern.quote(name) + "(?![\\p{L}\\p{N}'])")
            .matcher(selected.substring("@contains:".length())).find();
    }
}

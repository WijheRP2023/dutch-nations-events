package nl.dutchnations.events;

import java.time.OffsetDateTime;

/** Matching is deliberately by exact item name, not a partial search. */
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
                if (!candidate.trim().isEmpty() && normalize(candidate).equals(normalize(item))) return true;
        }
        catch (RuntimeException ignored) { return false; }
        return false;
    }
    static String normalize(String name)
    { return name.trim().replace('’', '\'').toLowerCase(java.util.Locale.ROOT); }
}

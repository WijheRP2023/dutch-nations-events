package nl.dutchnations.events;

import java.time.OffsetDateTime;

/** Screenshot participation does not grant or require a management role. */
final class DropScreenshotPolicy
{
    private DropScreenshotPolicy() { }

    static boolean allowed(boolean optedIn, boolean clanAccess, ClanFeed feed, String eventId, OffsetDateTime now)
    {
        if (!optedIn || !clanAccess || feed == null || feed.events == null || eventId == null) return false;
        for (ClanFeed.ClanEvent event : feed.events)
        {
            try { if (event != null && eventId.equals(event.id) && event.active(now)) return true; }
            catch (RuntimeException ignored) { /* Invalid or expired event data fails closed. */ }
        }
        return false;
    }
}

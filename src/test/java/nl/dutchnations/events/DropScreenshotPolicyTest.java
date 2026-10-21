package nl.dutchnations.events;

import java.time.OffsetDateTime;
import org.junit.Test;
import static org.junit.Assert.*;

public class DropScreenshotPolicyTest
{
    private final OffsetDateTime start = OffsetDateTime.parse("2026-10-21T16:00:00Z");

    private ClanFeed feed()
    {
        ClanFeed feed = new ClanFeed();
        ClanFeed.ClanEvent event = new ClanFeed.ClanEvent();
        event.id = "event"; event.startsAt = start.toString(); event.endsAt = start.plusHours(1).toString();
        feed.events.add(event);
        return feed;
    }

    @Test public void ordinaryClanMemberNeedsNoManagementRole()
    { assertTrue(DropScreenshotPolicy.allowed(true, true, feed(), "event", start)); }

    @Test public void explicitOptInAndClanAccessAreRequired()
    {
        assertFalse(new DutchNationsConfig() { }.shareDropScreenshots());
        assertFalse(DropScreenshotPolicy.allowed(false, true, feed(), "event", start));
        assertFalse(DropScreenshotPolicy.allowed(true, false, feed(), "event", start));
    }

    @Test public void neverCapturesBeforeStartOrAtEnd()
    {
        assertFalse(DropScreenshotPolicy.allowed(true, true, feed(), "event", start.minusNanos(1)));
        assertTrue(DropScreenshotPolicy.allowed(true, true, feed(), "event", start.plusMinutes(59)));
        assertFalse(DropScreenshotPolicy.allowed(true, true, feed(), "event", start.plusHours(1)));
    }

    @Test public void deletedOrInvalidEventFailsClosed()
    {
        ClanFeed feed = feed();
        assertFalse(DropScreenshotPolicy.allowed(true, true, feed, "other", start));
        feed.events.get(0).endsAt = "invalid";
        assertFalse(DropScreenshotPolicy.allowed(true, true, feed, "event", start));
        assertFalse(DropScreenshotPolicy.allowed(true, true, null, "event", start));
    }
}

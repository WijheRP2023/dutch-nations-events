package nl.dutchnations.events;

import java.time.OffsetDateTime;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class WomCompetitionTest
{
    @Test public void activeCompetitionWinsOverUpcoming()
    {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-23T12:00:00Z");
        assertEquals(1, WomCompetition.currentOrNext(Arrays.asList(
            competition(2, "Volgende", "2026-07-30T00:00:00Z", "2026-08-06T00:00:00Z"),
            competition(1, "Actief", "2026-07-20T00:00:00Z", "2026-07-27T00:00:00Z")), now).id);
    }
    @Test public void earliestUpcomingCompetitionIsSelected()
    {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-23T12:00:00Z");
        assertEquals(1, WomCompetition.currentOrNext(Arrays.asList(
            competition(2, "Later", "2026-08-06T00:00:00Z", "2026-08-13T00:00:00Z"),
            competition(1, "Volgende", "2026-07-30T00:00:00Z", "2026-08-06T00:00:00Z")), now).id);
    }
    private static WomCompetition competition(int id, String title, String start, String end)
    {
        WomCompetition value = new WomCompetition(); value.id = id; value.title = title;
        value.startsAt = start; value.endsAt = end; return value;
    }
}
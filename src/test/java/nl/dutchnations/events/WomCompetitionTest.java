package nl.dutchnations.events;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class WomCompetitionTest
{
    @Test public void allActiveCompetitionsAreVisible()
    {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-23T12:00:00Z");
        List<WomCompetition> visible = WomCompetition.visible(Arrays.asList(
            competition(3, "Later", "2026-08-01T00:00:00Z", "2026-08-08T00:00:00Z"),
            competition(2, "Actief twee", "2026-07-21T00:00:00Z", "2026-07-27T00:00:00Z"),
            competition(1, "Actief een", "2026-07-20T00:00:00Z", "2026-07-27T00:00:00Z")), now);
        assertEquals(3, visible.size());
        assertEquals(1, visible.get(0).id);
        assertEquals(2, visible.get(1).id);
        assertEquals(3, visible.get(2).id);
    }
    @Test public void nextCompetitionWithinSevenDaysIsShownWithTheActiveCompetition()
    {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-23T12:00:00Z");
        List<WomCompetition> visible = WomCompetition.visible(Arrays.asList(
            competition(2, "Volgende", "2026-07-29T12:00:00Z", "2026-08-05T12:00:00Z"),
            competition(1, "Actief", "2026-07-20T00:00:00Z", "2026-07-27T00:00:00Z")), now);
        assertEquals(2, visible.size());
        assertEquals(1, visible.get(0).id);
        assertEquals(2, visible.get(1).id);
    }
    @Test public void activeCompetitionsWinOverUpcoming()
    {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-23T12:00:00Z");
        assertEquals(1, WomCompetition.visible(Arrays.asList(
            competition(2, "Volgende", "2026-07-30T00:00:00Z", "2026-08-06T00:00:00Z"),
            competition(1, "Actief", "2026-07-20T00:00:00Z", "2026-07-27T00:00:00Z")), now).get(0).id);
    }
    @Test public void earliestUpcomingCompetitionIsSelected()
    {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-23T12:00:00Z");
        assertEquals(1, WomCompetition.visible(Arrays.asList(
            competition(2, "Later", "2026-08-06T00:00:00Z", "2026-08-13T00:00:00Z"),
            competition(1, "Volgende", "2026-07-30T00:00:00Z", "2026-08-06T00:00:00Z")), now).get(0).id);
    }
    private static WomCompetition competition(int id, String title, String start, String end)
    {
        WomCompetition value = new WomCompetition(); value.id = id; value.title = title;
        value.startsAt = start; value.endsAt = end; return value;
    }
}
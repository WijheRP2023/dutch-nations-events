package nl.dutchnations.events;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

final class WomCompetition
{
    int id;
    String title;
    String metric;
    String startsAt;
    String endsAt;
    int participantCount;

    OffsetDateTime start() { return OffsetDateTime.parse(startsAt); }
    OffsetDateTime end() { return OffsetDateTime.parse(endsAt); }
    boolean active(OffsetDateTime now) { return !now.isBefore(start()) && now.isBefore(end()); }

    static WomCompetition currentOrNext(List<WomCompetition> competitions, OffsetDateTime now)
    {
        if (competitions == null) return null;
        return competitions.stream()
            .filter(competition -> competition != null && competition.startsAt != null && competition.endsAt != null)
            .filter(competition -> competition.end().isAfter(now))
            .sorted(Comparator.comparing((WomCompetition competition) -> !competition.active(now))
                .thenComparing(WomCompetition::start))
            .findFirst().orElse(null);
    }
}
package nl.dutchnations.events;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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

    static List<WomCompetition> visible(List<WomCompetition> competitions, OffsetDateTime now)
    {
        if (competitions == null) return Collections.emptyList();
        List<WomCompetition> candidates = competitions.stream()
            .filter(competition -> competition != null && competition.startsAt != null && competition.endsAt != null)
            .filter(competition -> competition.end().isAfter(now))
            .sorted(Comparator.comparing((WomCompetition competition) -> !competition.active(now))
                .thenComparing(WomCompetition::start))
            .collect(Collectors.toList());
        if (candidates.isEmpty()) return Collections.emptyList();
        List<WomCompetition> visible = candidates.stream()
            .filter(competition -> competition.active(now)).collect(Collectors.toList());
        WomCompetition next = candidates.stream().filter(competition -> !competition.active(now)).findFirst().orElse(null);
        if (next != null) visible.add(next);
        return visible;
    }
}
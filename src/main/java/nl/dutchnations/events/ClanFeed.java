package nl.dutchnations.events;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

final class ClanFeed
{
    String updatedAt;
    List<ClanEvent> events = new ArrayList<>();


    static final class ClanEvent
    {
        String id;
        String startsAt;
        String endsAt;
        String type;
        String title;
        String world;
        String host;
        String description;
        String codeword;
        String checklist;
        String requiredPlugins;

        OffsetDateTime start() { return OffsetDateTime.parse(startsAt); }
        OffsetDateTime end() { return OffsetDateTime.parse(endsAt); }
        boolean active(OffsetDateTime now) { return !now.isBefore(start()) && now.isBefore(end()); }
        boolean learner() { return "LEARNER".equalsIgnoreCase(type); }
    }
}

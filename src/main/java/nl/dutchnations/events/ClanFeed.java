package nl.dutchnations.events;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

final class ClanFeed
{
    String updatedAt;
    String announcementsUrl;
    Boolean announcementsVisible = true;
    String eventAnnouncementsUrl;
    // Kept for compatibility with older feed responses.
    @SuppressWarnings("unused") String dropChannelUrl;
    long announcementsSequence;
    List<ClanEvent> events = new ArrayList<>();
    List<LearnerTemplate> learnerTemplates = new ArrayList<>();


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
        String strategyWikiUrl;
        String driveUrl;
        String dropListUrl;
        String registrationUrl;
        String registrationEndsAt;
        String youtubeUrl;
        String clansOne;
        String clansTwo;
        String activity;
        String bossList;
        String dropItems;
        String dropChannelUrl;
        String dropSource;
        boolean codewordRequired;

        OffsetDateTime start() { return OffsetDateTime.parse(startsAt); }
        OffsetDateTime end() { return OffsetDateTime.parse(endsAt); }
        boolean active(OffsetDateTime now) { return !now.isBefore(start()) && now.isBefore(end()); }
        boolean learner() { return "LEARNER".equalsIgnoreCase(type); }
        boolean supportsPreparation() { return learner() || "MASS".equalsIgnoreCase(type); }
    }

    static final class LearnerTemplate
    {
        String id;
        String name;
        String title;
        String description;
        String discordDescription;
        String checklist;
        String requiredPlugins;
        String strategyWikiUrl;
        String youtubeUrl;
    }
}

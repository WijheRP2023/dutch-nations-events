package nl.dutchnations.events;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(DutchNationsConfig.GROUP)
public interface DutchNationsConfig extends Config
{
    String GROUP = "dutchnationsevents";

    @ConfigItem(keyName = "feedUrl", name = "Management-feed",
        description = "Publieke feed-URL van de Dutch Nations API", position = 0)
    default String feedUrl() { return "https://dutch-nations-events.onrender.com/feed.json"; }

    @ConfigItem(keyName = "managementApiUrl", name = "Event-API",
        description = "Beveiligde API voor toevoegen en verwijderen", position = 1)
    default String managementApiUrl() { return "https://dutch-nations-events.onrender.com/api/events"; }

    @ConfigItem(keyName = "rolesApiUrl", name = "Rollen-API",
        description = "Beveiligde API voor eigen rol en rollenbeheer", position = 2)
    default String rolesApiUrl() { return "https://dutch-nations-events.onrender.com/api/roles"; }

    @ConfigItem(keyName = "managementToken", name = "Management-token",
        description = "Persoonlijke token; deel deze nooit met anderen", secret = true, position = 3)
    default String managementToken() { return ""; }

    @ConfigItem(keyName = "showOverlay", name = "Codewoord-popup", position = 4,
        description = "Toon het codewoord alleen tijdens een actief boss-event")
    default boolean showOverlay() { return true; }

    @ConfigItem(keyName = "showLearner", name = "Toon learner-events", position = 5,
        description = "Toon learner-events in de kalender")
    default boolean showLearner() { return true; }

    @ConfigItem(keyName = "showBoss", name = "Toon boss-events", position = 6,
        description = "Toon boss-events in de kalender")
    default boolean showBoss() { return true; }

    @ConfigItem(keyName = "showMass", name = "Toon mass-events", position = 7,
        description = "Toon mass-events in de kalender")
    default boolean showMass() { return true; }

    @ConfigItem(keyName = "notifyLearner", name = "Meld learner-events", position = 8,
        description = "Toon lokale chatboxmeldingen voor learner-events")
    default boolean notifyLearner() { return true; }

    @ConfigItem(keyName = "notifyBoss", name = "Meld boss-events", position = 9,
        description = "Toon lokale chatboxmeldingen voor boss-events")
    default boolean notifyBoss() { return true; }

    @ConfigItem(keyName = "notifyMass", name = "Meld mass-events", position = 10,
        description = "Toon lokale chatboxmeldingen voor mass-events")
    default boolean notifyMass() { return true; }

    @Range(min = 1, max = 60)
    @ConfigItem(keyName = "reminderMinutes", name = "Herinnering vooraf", position = 11,
        description = "Aantal minuten voor de start voor de lokale melding")
    default int reminderMinutes() { return 30; }

    @ConfigItem(keyName = "notifyAtStart", name = "Melding bij start", position = 12,
        description = "Toon een lokale chatboxmelding zodra het event start")
    default boolean notifyAtStart() { return true; }
}

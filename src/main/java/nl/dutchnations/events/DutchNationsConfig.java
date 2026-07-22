package nl.dutchnations.events;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

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
}

package nl.dutchnations.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Event configuration, not a player/team membership or score authority. */
final class EventTeams
{
    private EventTeams() { }

    static List<String> names(String value)
    {
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();
        if (value.length() > 300) throw new IllegalArgumentException("Teamnamen zijn te lang");
        List<String> names = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (String part : value.split(";", -1))
        {
            String name = part.trim();
            if (name.isEmpty() || name.length() > 40 || name.chars().anyMatch(c -> Character.isISOControl(c) || c == '<' || c == '>'))
                throw new IllegalArgumentException("Gebruik teamnamen van 1 tot 40 tekens, gescheiden door ;");
            if (!unique.add(name.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Teamnamen moeten uniek zijn");
            names.add(name);
        }
        if (names.size() < 2 || names.size() > 7) throw new IllegalArgumentException("Vul 2 tot en met 7 teams in, of laat teamnamen leeg");
        return Collections.unmodifiableList(names);
    }
}

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

    static String validatePages(String type, String teams, String one, String two)
    {
        if ((one == null || one.trim().isEmpty()) && (two == null || two.trim().isEmpty())) return null;
        if (!"CLAN_EVENT".equalsIgnoreCase(type)) return "Teamdroppagina's zijn alleen beschikbaar bij clan-events";
        try
        {
            if (names(teams).size() != 2) return "Gebruik precies twee teamnamen voor twee teamdroppagina's";
            String first = GoogleSheetDrops.tabKey(one), second = GoogleSheetDrops.tabKey(two);
            if (first == null || second == null) return "Vul voor beide teams een Google Sheet-link naar het eigen tabblad in (met gid)";
            if (first.equals(second)) return "Gebruik twee verschillende teamdroppagina's";
        }
        catch (IllegalArgumentException ex) { return ex.getMessage(); }
        return null;
    }

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

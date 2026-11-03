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
    { return validatePages(type,teams,one,two,null); }

    static String validatePages(String type, String teams, String one, String two, String three)
    {
        if (blank(one) && blank(two) && blank(three)) return null;
        if (!"CLAN_EVENT".equalsIgnoreCase(type)) return "Teamdroppagina's zijn alleen beschikbaar bij clan-events";
        try
        {
            int count=blank(three)?2:3;
            if (names(teams).size() != count) return "Vul voor elk van de twee of drie teams een eigen droppagina in; laat Team 3 leeg bij twee teams";
            Set<String> seen=new HashSet<>();String workbook=null;
            for(String page:count==2?new String[]{one,two}:new String[]{one,two,three})
            {
                String key=GoogleSheetDrops.tabKey(page);
                if(key==null)return "Vul voor ieder team een Google Sheet-link naar het eigen tabblad in (met gid)";
                if(!seen.add(key))return "Gebruik voor ieder team een verschillende droppagina";
                String id=GoogleSheetDrops.sheetId(page);
                if(workbook!=null&&!workbook.equals(id))return "Gebruik teamtabbladen uit hetzelfde kijkbestand voor de teamindeling";
                workbook=id;
            }
        }
        catch (IllegalArgumentException ex) { return ex.getMessage(); }
        return null;
    }
    private static boolean blank(String value){return value==null||value.trim().isEmpty();}

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

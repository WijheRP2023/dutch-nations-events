package nl.dutchnations.server;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Team slots come from Instellingen; their display names come from the event. */
final class SheetTeamRoster
{
    static final String UNKNOWN = "Team onbekend";
    private final Map<String, Integer> slots;
    private final int teamCount;

    private SheetTeamRoster(Map<String, Integer> slots,int teamCount) { this.slots = Map.copyOf(slots);this.teamCount=teamCount; }

    static SheetTeamRoster parseCsv(String csv) throws IOException
    { return parseCsv(csv,2); }

    static SheetTeamRoster parseCsv(String csv,int teamCount) throws IOException
    {
        if(teamCount<2||teamCount>3)throw new IOException("Ongeldig aantal aparte teams");
        List<List<String>> rows = GoogleSheetDrops.csvRows(csv);
        // Only configured player columns A/E (and I for three teams), never payment details.
        int header = -1;
        for (int i = 0; i < Math.min(rows.size(), 4); i++)
        {
            List<String> row = rows.get(i);
            if (row.size() == teamCount && row.stream().allMatch(cell -> "speler (in-game)".equals(normalize(cell))))
            {
                if (header != -1) throw new IOException("Dubbele teamindeling-header");
                header = i;
            }
        }
        if (header < 0) throw new IOException("Teamindeling ontbreekt in Instellingen");
        Map<String, Integer> slots = new HashMap<>();
        for (int i = header + 1; i < rows.size(); i++)
        {
            List<String> row = rows.get(i);
            if (row.size() > teamCount) throw new IOException("Onverwachte teamindeling");
            for (int team = 0; team < row.size(); team++)
            {
                String player = normalize(row.get(team));
                if (player.isEmpty()) continue;
                // Duplicate in either column, including twice in one team: unknown.
                slots.merge(player, team + 1, (previous, next) -> 0);
            }
        }
        return new SheetTeamRoster(slots,teamCount);
    }

    String teamFor(String rsn, String eventTeamNames)
    {
        try
        {
            List<String> names = EventTeams.names(eventTeamNames);
            Integer slot = slots.get(normalize(rsn));
            return names.size() == teamCount && slot != null && slot > 0 ? names.get(slot - 1) : UNKNOWN;
        }
        catch (IllegalArgumentException ex) { return UNKNOWN; }
    }

    static String sourceId(String one, String two, String fallback)
    { return sourceId(one,two,null,fallback); }

    static String sourceId(String one, String two, String three, String fallback)
    {
        boolean separate = one != null && !one.trim().isEmpty() || two != null && !two.trim().isEmpty() || three != null && !three.trim().isEmpty();
        if (!separate) return GoogleSheetDrops.sheetId(fallback);
        String first = GoogleSheetDrops.sheetId(one), second = GoogleSheetDrops.sheetId(two);
        String third=three==null||three.trim().isEmpty()?first:GoogleSheetDrops.sheetId(three);
        return first != null && first.equals(second) && first.equals(third) ? first : null;
    }

    private static String normalize(String value)
    {
        return value == null ? "" : value.replace('\u00a0', ' ').replace('_', ' ').trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    interface Loader { SheetTeamRoster load(String sheetId,int teamCount) throws IOException; }

    /** Cache names/slots, not event display names; do not reuse stale data on failure. */
    static final class Lookup
    {
        private final Loader loader;
        private final Map<String, Cached> cache = new HashMap<>();
        Lookup(Loader loader) { this.loader = loader; }

        synchronized String teamFor(String sheetId, String rsn, String eventNames, long now)
        {
            int teamCount;
            try { teamCount=EventTeams.names(eventNames).size();if (sheetId == null || teamCount<2 || teamCount>3) return UNKNOWN; }
            catch (IllegalArgumentException ex) { return UNKNOWN; }
            cache.values().removeIf(value -> value.until <= now);
            String key=sheetId+"\n"+teamCount;
            Cached hit = cache.get(key);
            if (hit == null)
            {
                if (cache.size() >= 64) return UNKNOWN;
                try { hit = new Cached(loader.load(sheetId,teamCount), now + 30000); }
                catch (IOException | RuntimeException ex) { hit = new Cached(null, now + 5000); }
                cache.put(key, hit);
            }
            return hit.roster == null ? UNKNOWN : hit.roster.teamFor(rsn, eventNames);
        }
        private static final class Cached
        {
            final SheetTeamRoster roster;
            final long until;
            Cached(SheetTeamRoster roster, long until) { this.roster = roster; this.until = until; }
        }
    }
}

package nl.dutchnations.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Private durable game state. Never serialize this object into a public response. */
final class BattleshipGame
{
    static final class RuleFailure extends IllegalArgumentException
    { RuleFailure(String message) { super(message); } }
    static final int SIZE = 8;
    static final int[] LENGTHS = {5, 4, 3, 3, 2};
    String key;
    String binding;
    boolean started, ready, retired;
    List<String> teamNames = new ArrayList<>();
    List<String> messages = new ArrayList<>(java.util.Arrays.asList("", ""));
    long startsAt, endsAt;
    List<Map<String, Integer>> fleets = new ArrayList<>();
    Map<String, Integer> goals = new LinkedHashMap<>();
    List<List<Shot>> shots = new ArrayList<>();

    static final class Shot
    {
        String coordinate;
        long at;
        Shot(String coordinate, long at) { this.coordinate = coordinate; this.at = at; }
    }

    static BattleshipGame create(String key, long start, long end, Map<String, Integer> goals,
                                 List<List<String>> first, List<List<String>> second)
    {
        if (key == null || key.length() > 200 || end <= start || goals.isEmpty() || goals.size() > 500)
            throw new RuleFailure("Ongeldige eventperiode of dropselectie");
        goals.forEach((id, goal) -> {
            if (id == null || !id.matches("DROP-[0-9]+") || goal == null || goal < 1 || goal > 7)
                throw new RuleFailure("Ongeldig dropdoel");
        });
        BattleshipGame game = new BattleshipGame();
        game.key = key; game.startsAt = start; game.endsAt = end; game.goals.putAll(goals);
        game.fleets.add(fleet(first)); game.fleets.add(fleet(second));
        game.shots.add(new ArrayList<>()); game.shots.add(new ArrayList<>());
        return game;
    }

    static String coordinate(String raw)
    {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-H][1-8]")) throw new RuleFailure("Kies een vak van A1 tot H8");
        return value;
    }

    static Map<String, Integer> fleet(List<List<String>> placements)
    {
        if (placements == null || placements.size() != LENGTHS.length)
            throw new RuleFailure("Plaats alle vijf boten");
        Map<String, Integer> cells = new HashMap<>();
        for (int ship = 0; ship < LENGTHS.length; ship++)
        {
            List<String> input = placements.get(ship);
            if (input == null || input.size() != 2) throw new RuleFailure("Een boot heeft een beginvak en richting nodig");
            String origin = coordinate(input.get(0));
            String direction = input.get(1) == null ? "" : input.get(1).trim();
            boolean horizontal = "Horizontaal".equalsIgnoreCase(direction);
            if (!horizontal && !"Verticaal".equalsIgnoreCase(direction)) throw new RuleFailure("Kies Horizontaal of Verticaal");
            int x = origin.charAt(0) - 'A', y = Integer.parseInt(origin.substring(1)) - 1;
            for (int n = 0; n < LENGTHS[ship]; n++)
            {
                int col = x + (horizontal ? n : 0), row = y + (horizontal ? 0 : n);
                if (col >= SIZE || row >= SIZE) throw new RuleFailure("Een boot valt buiten het bord");
                String cell = "" + (char) ('A' + col) + (row + 1);
                if (cells.putIfAbsent(cell, ship) != null) throw new RuleFailure("Boten mogen niet overlappen");
            }
        }
        return cells;
    }

    int earned(Set<String> completed)
    {
        if (completed == null || !goals.keySet().containsAll(completed)) throw new RuleFailure("Onbekend dropdoel");
        return completed.size(); // A set of stable goal IDs, not completion transitions or item quantity.
    }

    int available(int team, Set<String> completed)
    { checkTeam(team); return Math.max(0, earned(completed) - shots.get(team).size()); }

    String fire(int team, String raw, Set<String> completed, long now)
    { return fire(team, raw, completed, now, false); }

    private String fire(int team, String raw, Set<String> completed, long now, boolean sameBatch)
    {
        checkTeam(team);
        if (now < startsAt || now >= endsAt) throw new RuleFailure("Het event is niet actief");
        if (!sameBatch && winner() != 0) throw new RuleFailure("Het spel is afgelopen");
        String target = coordinate(raw);
        for (Shot shot : shots.get(team)) if (shot.coordinate.equals(target)) throw new RuleFailure("Op dit vak is al geschoten");
        if (available(team, completed) == 0) throw new RuleFailure("Geen schoten beschikbaar");
        shots.get(team).add(new Shot(target, now));
        return outcome(team, target);
    }

    void fireBatch(List<String> targets, List<Set<String>> completed, long now)
    {
        boolean finishedBefore = winner() != 0;
        for (int team = 0; team < 2; team++) if (targets.get(team) != null)
        {
            try
            {
                if (finishedBefore) throw new RuleFailure("Het spel is afgelopen");
                String target = coordinate(targets.get(team));
                fire(team, target, completed.get(team), now, true);
                messages.set(team, target + ": " + outcome(team, target));
            }
            catch (IllegalArgumentException | IllegalStateException rejected) { messages.set(team, rejected.getMessage()); }
        }
    }

    String outcome(int team, String cell)
    {
        checkTeam(team);
        Set<String> fired = fired(team);
        if (!fired.contains(cell)) return "";
        Map<String, Integer> enemy = fleets.get(1 - team);
        Integer ship = enemy.get(cell);
        if (ship == null) return "MIS";
        boolean sunk = enemy.entrySet().stream().filter(e -> e.getValue().equals(ship)).allMatch(e -> fired.contains(e.getKey()));
        return sunk ? "GEZONKEN" : "RAAK";
    }

    int winner()
    {
        int winner = 0;
        for (int team = 0; team < 2; team++)
            if (fired(team).containsAll(fleets.get(1 - team).keySet())) winner += team + 1;
        return winner; // 3 means both teams sank the last ship in the same poll batch.
    }

    List<List<String>> publicBoard(int team)
    {
        checkTeam(team);
        List<List<String>> rows = new ArrayList<>();
        for (int y = 1; y <= SIZE; y++)
        {
            List<String> row = new ArrayList<>();
            for (int x = 0; x < SIZE; x++) row.add(outcome(team, "" + (char) ('A' + x) + y));
            rows.add(row);
        }
        return rows;
    }

    private Set<String> fired(int team)
    {
        Set<String> result = new HashSet<>();
        for (Shot shot : shots.get(team)) result.add(shot.coordinate);
        return result;
    }

    private static void checkTeam(int team)
    { if (team != 0 && team != 1) throw new RuleFailure("Onbekend team"); }
}

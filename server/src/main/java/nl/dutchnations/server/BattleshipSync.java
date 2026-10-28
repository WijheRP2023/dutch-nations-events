package nl.dutchnations.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Disabled until all three files and a Google service account are configured. */
final class BattleshipSync
{
    private final DutchNationsApi.Store store;
    private BattleshipGateway google;
    private String lastError = "";
    private final String main, first, second, credential;
    private final java.util.function.LongSupplier clock;

    BattleshipSync(DutchNationsApi.Store store)
    { this(store,null,System.getenv("DN_BATTLESHIP_PUBLIC_SHEET"),System.getenv("DN_BATTLESHIP_TEAM_ONE_SHEET"),System.getenv("DN_BATTLESHIP_TEAM_TWO_SHEET"),System.getenv("DN_GOOGLE_SERVICE_ACCOUNT_JSON"),System::currentTimeMillis); }
    BattleshipSync(DutchNationsApi.Store store, BattleshipGateway google, String main, String first, String second, String credential, java.util.function.LongSupplier clock)
    { this.store=store; this.google=google; this.main=main; this.first=first; this.second=second; this.credential=credential; this.clock=clock; }

    static boolean configured()
    {
        try
        {
            String one = BattleshipSheets.id(System.getenv("DN_BATTLESHIP_TEAM_ONE_SHEET"));
            String two = BattleshipSheets.id(System.getenv("DN_BATTLESHIP_TEAM_TWO_SHEET"));
            String main = BattleshipSheets.id(System.getenv("DN_BATTLESHIP_PUBLIC_SHEET"));
            String key = System.getenv("DN_GOOGLE_SERVICE_ACCOUNT_JSON");
            return !one.equals(two) && !main.equals(one) && !main.equals(two) && key != null && !key.trim().isEmpty() && key.length() <= 50000;
        }
        catch (IllegalArgumentException ex) { return false; }
    }

    void tick()
    {
        if (google == null && !configured()) return;
        try
        {
            if (google == null) google = new BattleshipSheets(credential);
            sync(); lastError = "";
        }
        catch (Exception ex)
        {
            // Never print Google response bodies, keys, placement values or private file IDs.
            String message = ex instanceof BattleshipGame.RuleFailure ? ex.getMessage() : "De zeeslagcontrole kon niet veilig worden afgerond. Controleer servertoegang, bladrechten en instellingen.";
            if (message == null) message = "Zeeslagcontrole mislukt";
            if (!message.equals(lastError)) { store.error("Zeeslag", message); lastError = message; }
            if (google != null) try
            {
                google.protect(main, Set.of(), Set.of());
                publish(null, List.of(Set.of(), Set.of()), "CONTROLE", message, new boolean[2]);
            }
            catch (Exception ignored) { /* Leave protected inputs closed on any read/write failure. */ }
        }
    }

    private void sync() throws Exception
    {
        google.protect(main, Set.of(), Set.of());
        BattleshipSheets.Access a = google.privateAccess(first), b = google.privateAccess(second);
        BattleshipSheets.separated(a, b);
        List<List<List<Object>>> root = google.batchValues(main, "Instellingen!B10:F12", "Instellingen!A17:G17", "Borddata!Z2", "Screenshotlijst!A1:H1001");
        long now = clock.getAsLong();
        if (!(cell(root.get(0),0,0) instanceof Number) || !(cell(root.get(0),0,4) instanceof Number))
        {
            BattleshipGame old = store.latestBattleship(main + "@");
            if (old != null && old.started && now >= old.endsAt)
            {
                resetPrivate(first,old.key); resetPrivate(second,old.key);
                google.sourceLocked(main,false,false);
            }
            publish(null,List.of(Set.of(),Set.of()),"VOORBEREIDING","Vul start- en einddatum met tijd in. Er wordt niet geschoten.",new boolean[]{true,true});
            return;
        }
        if (!"Zeeslag".equals(text(root.get(2), 0, 0)))
        { publish(null, List.of(Set.of(), Set.of()), "UITGESCHAKELD", "Kies Zeeslag bij Instellingen. Er wordt niet geschoten.", new boolean[2]); return; }
        long start = date(cell(root.get(0), 0, 0)), end = date(cell(root.get(0), 0, 4));
        if (end <= start) throw new BattleshipGame.RuleFailure("Vul een geldige start- en einddatum met tijd in");
        String key = main + "@" + start + ":" + end;
        BattleshipGame game = store.battleship(key);
        if (game != null)
        {
            for (Map<String,Integer> fleet : game.fleets) for (String coordinate : fleet.keySet())
                if (!coordinate.matches("[A-H][1-8]")) throw new BattleshipGame.RuleFailure("De opgeslagen vloot past niet op het 8×8-bord. Bereid een nieuw event voor.");
            for (List<BattleshipGame.Shot> shots : game.shots) for (BattleshipGame.Shot shot : shots)
                if (!shot.coordinate.matches("[A-H][1-8]")) throw new BattleshipGame.RuleFailure("Het opgeslagen spel bevat schoten buiten het 8×8-bord. Bereid een nieuw event voor.");
        }
        BattleshipGame latest = store.activeBattleship(main + "@", now);
        if (latest != null && latest.started && now < latest.endsAt && !key.equals(latest.key))
            throw new BattleshipGame.RuleFailure("De vastgezette eventperiode is gewijzigd; herstel de oorspronkelijke periode");
        Progress progress = progress(root.get(3), text(root.get(1), 0, 0), text(root.get(1), 0, 4));
        String binding = first + "\n" + second;
        if (now < start)
        {
            google.sourceLocked(main,false,false);
            try
            {
                google.privateLocked(first, false); google.privateLocked(second, false);
                List<List<String>> p1 = null, p2 = null;
                String error = null;
                try { p1 = placement(first, key); } catch (IllegalArgumentException e) { error = e.getMessage(); }
                try { p2 = placement(second, key); } catch (IllegalArgumentException e) { error = e.getMessage(); }
                if (error != null) throw new BattleshipGame.RuleFailure(error);
                BattleshipGame draft = BattleshipGame.create(key, start, end, progress.goals, p1, p2);
                draft.binding = binding; draft.teamNames = progress.names; draft.ready = true;
                if (game != null && game.started) throw new BattleshipGame.RuleFailure("Een gestart spel kan niet terug naar voorbereiding");
                store.saveBattleship(draft);
                publish(draft, progress.completed, "VOORBEREIDING", "Beide vloten zijn gereed. Schieten kan vanaf de starttijd.", new boolean[] {true, true});
            }
            catch (IllegalArgumentException | IllegalStateException failure)
            {
                if (game != null && !game.started) { game.ready = false; store.saveBattleship(game); }
                publish(null, progress.completed, "VOORBEREIDING", failure.getMessage(), new boolean[] {true, true});
            }
            return;
        }
        if (game == null || !game.ready) throw new BattleshipGame.RuleFailure("De server moet vóór de start beide complete vloten vastzetten. Plan een nieuwe toekomstige starttijd.");
        if (!binding.equals(game.binding) || !game.goals.equals(progress.goals) || !game.teamNames.equals(progress.names))
            throw new BattleshipGame.RuleFailure("Vlootkoppeling, teamnamen of dropdoelen zijn na vastzetten gewijzigd");
        google.sourceLocked(main,true,Boolean.TRUE.equals(cell(root.get(0),2,0)));
        if (!game.started) { game.started = true; store.saveBattleship(game); }
        google.privateLocked(first, true); google.privateLocked(second, true);
        boolean[] consumed = new boolean[2];
        if (now < end && game.winner() == 0)
        {
            List<List<List<Object>>> inputs = google.batchValues(main, "Tussenstand!D65:H65", "Tussenstand!M65:Q65");
            List<String> targets = new ArrayList<>(Arrays.asList(null, null));
            for (int team = 0; team < 2; team++) if (Boolean.TRUE.equals(cell(inputs.get(team), 0, 4)))
            {
                consumed[team] = true;
                targets.set(team, text(inputs.get(team), 0, 0));
            }
            game.fireBatch(targets, progress.completed, clock.getAsLong());
            if (consumed[0] || consumed[1]) store.saveBattleship(game); // Commit before publishing; retries cannot double charge.
        }
        boolean active = clock.getAsLong() < end && game.winner() == 0;
        String message = active ? "Schieten kan met verdiende schoten; de bootopstellingen zijn vastgezet."
            : game.winner() == 0 ? "Het event is afgelopen. Er worden geen schoten meer verwerkt."
            : game.winner() == 3 ? "Gelijkspel: beide teams zonken de laatste boot in dezelfde verwerkingsronde."
            : game.teamNames.get(game.winner() - 1) + " wint: alle vijandelijke boten zijn gezonken.";
        publish(game, progress.completed, active ? "ACTIEF" : "AFGELOPEN", message, consumed);
        google.protect(main, active && game.available(0, progress.completed.get(0)) > 0 ? a.writers : Set.of(),
            active && game.available(1, progress.completed.get(1)) > 0 ? b.writers : Set.of());
    }

    private List<List<String>> placement(String file, String key) throws Exception
    {
        List<List<List<Object>>> data = google.batchValues(file, "Vloot!C6:D10", "Vloot!Q1", "Vloot!B17");
        String code = hash(key);
        if (!code.equals(text(data.get(1), 0, 0)))
        {
            google.write(file, Map.of("Vloot!Q1", List.of(List.of(code)), "Vloot!B17", List.of(List.of(false)),
                "Vloot!A2", List.of(List.of("Nieuw event gekoppeld. Controleer de boten en vink Gereed aan."))));
            throw new BattleshipGame.RuleFailure("Controleer beide privévloten en vink daar Gereed aan");
        }
        if (!Boolean.TRUE.equals(cell(data.get(2), 0, 0))) throw new BattleshipGame.RuleFailure("Beide leiders moeten hun privévloot Gereed melden");
        List<List<String>> out = new ArrayList<>();
        for (int n = 0; n < 5; n++) out.add(List.of(text(data.get(0), n, 0), text(data.get(0), n, 1)));
        return out;
    }

    private void resetPrivate(String file,String oldKey) throws Exception
    {
        List<List<List<Object>>> data=google.batchValues(file,"Vloot!Q1");
        if (!hash(oldKey).equals(text(data.get(0),0,0))) return;
        google.privateLocked(file,false);
        google.write(file,Map.of("Vloot!C6:D10",List.of(List.of("",""),List.of("",""),List.of("",""),List.of("",""),List.of("","")),
            "Vloot!Q1",List.of(List.of("")),"Vloot!B17",List.of(List.of(false)),"Vloot!A2",List.of(List.of("Event gereset. Plaats de boten voor het volgende event opnieuw."))));
    }

    private void publish(BattleshipGame game, List<Set<String>> complete, String phase, String message, boolean[] consumed) throws IOException
    {
        Map<String, List<List<?>>> data = new LinkedHashMap<>();
        data.put("Zeeslagdata!A2:A4", List.of(List.of(phase), List.of(message), List.of(Instant.ofEpochMilli(clock.getAsLong()).toString())));
        List<List<?>> counts = new ArrayList<>();
        for (int team = 0; team < 2; team++)
        {
            int earned = game == null ? 0 : game.earned(complete.get(team)), used = game == null ? 0 : game.shots.get(team).size();
            counts.add(List.of(team + 1, earned, used, Math.max(0, earned - used), game == null ? "" : game.messages.get(team)));
            data.put(team == 0 ? "Zeeslagdata!A12:J21" : "Zeeslagdata!M12:V21", sheetBoard(game, team));
            if (consumed[team])
            {
                data.put(team == 0 ? "Tussenstand!D65" : "Tussenstand!M65", List.of(List.of("")));
                data.put(team == 0 ? "Tussenstand!H65" : "Tussenstand!Q65", List.of(List.of(false)));
            }
        }
        data.put("Zeeslagdata!B8:F9", counts);
        google.write(main, data);
    }

    static final class Progress
    {
        final Map<String, Integer> goals = new LinkedHashMap<>();
        final List<Set<String>> completed = List.of(new HashSet<>(), new HashSet<>());
        final List<String> names;
        Progress(String a, String b) { names = List.of(a, b); }
    }

    static Progress progress(List<List<Object>> rows, String first, String second)
    {
        if (first.isEmpty() || second.isEmpty() || first.equalsIgnoreCase(second)) throw new BattleshipGame.RuleFailure("Vul twee verschillende teamnamen in");
        Progress p = new Progress(first, second);
        if (rows.isEmpty() || !"Drop-ID".equals(string(cell(rows, 0, 6)))) throw new BattleshipGame.RuleFailure("De screenshot-droplijst mist stabiele Drop-ID's");
        List<Map<String, Integer>> teams = List.of(new LinkedHashMap<>(), new LinkedHashMap<>());
        for (int r = 1; r < rows.size(); r++)
        {
            String item = text(rows, r, 0); if (item.isEmpty()) continue;
            String name = text(rows, r, 1), id = text(rows, r, 6);
            int team = first.equalsIgnoreCase(name) ? 0 : second.equalsIgnoreCase(name) ? 1 : -1;
            int goal = integer(cell(rows, r, 2)), obtained = integer(cell(rows, r, 3));
            if (team < 0 || !id.matches("DROP-[0-9]+") || goal < 1 || goal > 7 || obtained < 0 || obtained > goal
                || teams.get(team).putIfAbsent(id, goal) != null) throw new BattleshipGame.RuleFailure("Ongeldige of dubbele dropdoelregistratie");
            if (obtained == goal) p.completed.get(team).add(id);
        }
        if (teams.get(0).isEmpty() || teams.get(0).size() > 500 || !teams.get(0).equals(teams.get(1)))
            throw new BattleshipGame.RuleFailure("Beide teams moeten dezelfde geselecteerde dropdoelen hebben");
        p.goals.putAll(teams.get(0)); return p;
    }

    static long date(Object value)
    {
        if (!(value instanceof Number)) throw new BattleshipGame.RuleFailure("Vul eerst start- en einddatum met tijd in");
        double serial = ((Number) value).doubleValue();
        if (!Double.isFinite(serial) || serial < 43831 || serial >= 73416) throw new BattleshipGame.RuleFailure("Ongeldige eventdatum");
        long days = (long) Math.floor(serial), millis = Math.round((serial - days) * 86400000);
        LocalDateTime local = LocalDateTime.of(1899, 12, 30, 0, 0).plusDays(days).plusNanos(millis * 1000000);
        ZoneId zone = ZoneId.of("Europe/Amsterdam");
        if (zone.getRules().getValidOffsets(local).size() != 1) throw new BattleshipGame.RuleFailure("Kies een tijd buiten de zomertijdwisseling");
        return local.atZone(zone).toInstant().toEpochMilli();
    }

    private static int integer(Object value)
    {
        if (!(value instanceof Number)) return -1;
        double n = ((Number) value).doubleValue();
        return Double.isFinite(n) && n == Math.rint(n) && n >= 0 && n <= 500 ? (int) n : -1;
    }
    private static Object cell(List<List<Object>> rows, int r, int c) { return r < rows.size() && c < rows.get(r).size() ? rows.get(r).get(c) : ""; }
    private static String text(List<List<Object>> rows, int r, int c) { return string(cell(rows, r, c)); }
    private static String string(Object v) { return v instanceof String ? ((String) v).trim() : ""; }
    // Keep the existing transport ranges, explicitly clearing the removed rows/columns.
    static List<List<?>> sheetBoard(BattleshipGame game, int team)
    {
        List<List<?>> rows = new ArrayList<>();
        List<List<String>> board = game == null ? List.of() : game.publicBoard(team);
        for (int y = 0; y < 10; y++)
        {
            List<String> row = new ArrayList<>(java.util.Collections.nCopies(10, ""));
            if (game != null && y < BattleshipGame.SIZE)
                for (int x = 0; x < BattleshipGame.SIZE; x++) row.set(x, board.get(y).get(x));
            rows.add(row);
        }
        return rows;
    }
    private static String hash(String text) throws Exception { byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)); StringBuilder out = new StringBuilder(); for (byte b : digest) out.append(String.format("%02x", b)); return out.toString(); }
}

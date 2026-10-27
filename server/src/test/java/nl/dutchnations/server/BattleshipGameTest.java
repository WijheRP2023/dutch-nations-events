package nl.dutchnations.server;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class BattleshipGameTest
{
    static List<List<String>> fleet()
    { return List.of(List.of("A1", "Horizontaal"), List.of("A3", "Horizontaal"), List.of("A5", "Horizontaal"), List.of("A7", "Horizontaal"), List.of("A9", "Horizontaal")); }
    static Map<String, Integer> goals()
    { Map<String, Integer> m = new LinkedHashMap<>(); for (int i = 1; i <= 40; i++) m.put("DROP-" + i, 1); return m; }
    static BattleshipGame game() { return BattleshipGame.create("test", 100, 1000, goals(), fleet(), fleet()); }
    static Set<String> all() { return goals().keySet(); }
    static void rejects(Runnable r)
    { try { r.run(); fail("Moet weigeren"); } catch (IllegalArgumentException | IllegalStateException expected) { } }

    @Test public void validatesAndNormalizesCoordinates()
    { assertEquals("J10", BattleshipGame.coordinate(" j10 ")); for (String x : List.of("A0", "K1", "A11", "A01", "A1;B1", "")) rejects(() -> BattleshipGame.coordinate(x)); }
    @Test public void rejectsOverlapAndOffBoardAndIncomplete()
    {
        List<List<String>> rows = new ArrayList<>(fleet()); rows.set(1, List.of("A1", "Verticaal")); rejects(() -> BattleshipGame.fleet(rows));
        rows.set(1, List.of("J10", "Verticaal")); rejects(() -> BattleshipGame.fleet(rows));
        rejects(() -> BattleshipGame.fleet(List.of()));
    }
    @Test public void noCreditNoChargeAndInvalidNoCharge()
    { BattleshipGame g = game(); rejects(() -> g.fire(0, "A1", Set.of(), 500)); rejects(() -> g.fire(0, "Z1", all(), 500)); assertEquals(0, g.shots.get(0).size()); }
    @Test public void onlyAcceptsDuringExactPeriod()
    { BattleshipGame g = game(); rejects(() -> g.fire(0, "A1", all(), 99)); assertEquals("RAAK", g.fire(0, "A1", all(), 100)); rejects(() -> g.fire(0, "B1", all(), 1000)); }
    @Test public void duplicatesDoNotCostCreditAndTeamsAreIndependent()
    { BattleshipGame g = game(); g.fire(0, "J10", Set.of("DROP-1"), 500); rejects(() -> g.fire(0, "J10", all(), 500)); assertEquals(1, g.shots.get(0).size()); assertEquals(0, g.shots.get(1).size()); assertEquals(1, g.available(1, Set.of("DROP-1"))); }
    @Test public void correctionsAndRepeatCompletionCannotFarmCredits()
    { BattleshipGame g = game(); g.fire(0, "J10", Set.of("DROP-1"), 500); assertEquals(0, g.available(0, Set.of())); assertEquals(0, g.available(0, Set.of("DROP-1"))); rejects(() -> g.fire(0, "J9", Set.of("DROP-1"), 500)); }
    @Test public void sinksWholeShipAndNeverRevealsUnfiredCells()
    {
        BattleshipGame g = game(); assertTrue(g.publicBoard(0).stream().flatMap(List::stream).allMatch(String::isEmpty));
        g.fire(0, "A9", all(), 500); assertEquals("", g.outcome(0, "B9"));
        assertEquals("GEZONKEN", g.fire(0, "B9", all(), 500)); assertEquals("GEZONKEN", g.outcome(0, "A9"));
        assertEquals("", g.outcome(1, "A9")); assertEquals("MIS", g.fire(1, "J10", all(), 500));
    }
    @Test public void simultaneousLastShotsCanDrawRatherThanFavorTeamOne()
    {
        BattleshipGame g = game();
        for (String c : new HashSet<>(g.fleets.get(0).keySet())) if (!c.equals("A1")) { g.fire(0,c,all(),500); g.fire(1,c,all(),500); }
        g.fireBatch(Arrays.asList("A1", "A1"), List.of(all(), all()), 500); assertEquals(3, g.winner());
        rejects(() -> g.fire(0,"J10",all(),500));
    }
    @Test public void unknownGoalsAndInvalidTeamsAreRejected()
    { BattleshipGame g = game(); rejects(() -> g.fire(2,"A1",all(),500)); rejects(() -> g.earned(Set.of("DROP-99999"))); }
    @Test public void resetFindsPlayedEventInsteadOfAbandonedFutureDraft() throws Exception
    {
        DutchNationsApi.Store store = new DutchNationsApi.Store(Files.createTempDirectory("battleship-reset-").resolve("state.json"));
        BattleshipGame played = BattleshipGame.create("main@100:1000",100,1000,goals(),fleet(),fleet());
        played.started = true; store.saveBattleship(played);
        BattleshipGame draft = BattleshipGame.create("main@2000:3000",2000,3000,goals(),fleet(),fleet());
        store.saveBattleship(draft);
        assertEquals(played.key,store.latestBattleship("main@").key);
        assertEquals(played.key,store.activeBattleship("main@",500).key);
    }
    @Test public void durableStateIsNotInPublicFeedAndSurvivesRestart() throws Exception
    {
        java.nio.file.Path f = Files.createTempDirectory("battleship-test-").resolve("state.json");
        DutchNationsApi.Store store = new DutchNationsApi.Store(f);
        BattleshipGame g = game(); g.fire(0,"J10",all(),500); store.saveBattleship(g);
        String publicJson = new Gson().toJson(store.feed()); assertFalse(publicJson.contains("fleets")); assertFalse(publicJson.contains("shots"));
        DutchNationsApi.Store reload = new DutchNationsApi.Store(f); BattleshipGame saved = reload.battleship("test");
        assertEquals(1, saved.shots.get(0).size()); rejects(() -> saved.fire(0,"J10",all(),600));
        saved.shots.get(0).clear(); assertEquals(1, reload.battleship("test").shots.get(0).size());
    }
}

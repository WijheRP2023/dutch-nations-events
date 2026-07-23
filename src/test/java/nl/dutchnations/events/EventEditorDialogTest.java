package nl.dutchnations.events;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

public class EventEditorDialogTest
{
    @Test public void convertsEveryStrategyNameToAnOfficialWikiLink()
    {
        String[] names = {
            "Chambers of Xeric (CoX)", "Theatre of Blood (ToB)", "Tombs of Amascut (ToA)",
            "General Graardor (Bandos)", "Commander Zilyana (Saradomin)", "Kree'arra (Armadyl)",
            "K'ril Tsutsaroth (Zamorak)", "Callisto", "Vet'ion", "Venenatis", "Artio", "Calvar'ion",
            "Spindel", "King Black Dragon (KBD)", "Nex", "Dagannoth Kings", "Corporeal Beast",
            "Sarachnis", "The Nightmare", "Hueycoatl", "Royal Titans", "Scurrius",
            "God Wars Dungeon", "Wilderness bosses"
        };
        for (String name : names)
            assertTrue(name, EventEditorDialog.strategyWikiUrl(name).startsWith("https://oldschool.runescape.wiki/"));
    }
}
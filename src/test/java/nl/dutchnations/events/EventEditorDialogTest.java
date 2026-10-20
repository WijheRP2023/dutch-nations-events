package nl.dutchnations.events;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
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

    @Test public void identifiesPvmWarFromTheDropListLink()
    {
        EventDraft draft = new EventDraft();
        draft.dropListUrl = "https://osrs-nlbe.com/author/dutch_nation/pvm-war-2026/";
        EventEditorDialog.configureDropSource(draft, null);
        assertEquals("PVM_WAR_2026", draft.dropSource);
        assertEquals("", draft.dropItems);
        draft.dropListUrl = "https://osrs-nlbe.com.attacker.example/author/dutch_nation/pvm-war-2026/";
        EventEditorDialog.configureDropSource(draft, null);
        assertEquals("", draft.dropSource);
    }

    @Test public void editingKeepsSavedDropsAndChannelButUpdatesSourceWhenLinkChanges()
    {
        ClanFeed.ClanEvent existing = new ClanFeed.ClanEvent();
        existing.dropItems = "Blue moon helm; Dragon claws";
        existing.dropChannelUrl = "https://discord.com/channels/123/456";
        existing.dropSource = "PVM_WAR_2026";
        existing.driveUrl = "https://osrs-nlbe.com/author/dutch_nation/pvm-war-2026/";
        EventDraft draft = new EventDraft(); draft.dropListUrl = existing.driveUrl;
        EventEditorDialog.configureDropSource(draft, existing);
        assertEquals(existing.dropItems, draft.dropItems);
        assertEquals(existing.dropChannelUrl, draft.dropChannelUrl);
        assertEquals("PVM_WAR_2026", draft.dropSource);
        draft.dropListUrl = "https://docs.google.com/spreadsheets/d/1CbSSkKPdpghw9mhFW1yPkv7cXtZrrgEA8r49Yr_nQdQ/edit";
        EventEditorDialog.configureDropSource(draft, existing);
        assertEquals(existing.dropItems, draft.dropItems);
        assertEquals(existing.dropChannelUrl, draft.dropChannelUrl);
        assertEquals("", draft.dropSource);
    }
}

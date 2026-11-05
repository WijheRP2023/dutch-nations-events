package nl.dutchnations.events;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EventEditorDialogTest
{
    @Test public void formFitsViewportEvenWithLongLinksAndStillScrollsDown() throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            EventEditorDialog.EventForm form = new EventEditorDialog.EventForm();
            javax.swing.JLabel heading = new javax.swing.JLabel("Teams en droppagina's");
            form.add(heading);
            javax.swing.JPanel row = EventEditorDialog.row("Team 1-droppagina", new javax.swing.JTextField("https://docs.google.com/" + "x".repeat(300)));
            form.add(row);
            for (int i = 0; i < 20; i++) form.add(EventEditorDialog.row("Overig veld", new javax.swing.JTextField()));
            javax.swing.JScrollPane pane = form.scrollPane();
            pane.setSize(450, 160);
            pane.doLayout(); pane.getViewport().doLayout(); form.doLayout(); row.doLayout();
            assertEquals(javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, pane.getHorizontalScrollBarPolicy());
            assertEquals(pane.getViewport().getExtentSize().width, form.getWidth());
            assertEquals(0, heading.getX());
            assertEquals(0, row.getX());
            assertTrue(row.getComponent(1).getX() + row.getComponent(1).getWidth() <= form.getWidth());
            assertTrue(pane.getVerticalScrollBar().isVisible());
        });
    }
    @Test public void threePagesAreOptionalButMustMatchThreeNames()
    {
        String base="https://docs.google.com/spreadsheets/d/12345678901234567890/edit";
        EventDraft d=new EventDraft();d.type="CLAN_EVENT";d.teamNames="Rood;Blauw;Groen";
        d.teamOneDropListUrl=base+"?gid=1";d.teamTwoDropListUrl=base+"?gid=2";d.teamThreeDropListUrl=base+"?gid=3";
        EventEditorDialog.validateTeamPages(d);
        for(String bad:new String[]{"",base+"?gid=1",base.replace("12345678901234567890","98765432109876543210")+"?gid=3"})
        {
            d.teamThreeDropListUrl=bad;
            try{EventEditorDialog.validateTeamPages(d);org.junit.Assert.fail(bad);}catch(IllegalArgumentException expected){}
        }
        d.teamNames="Rood;Blauw";d.teamThreeDropListUrl="";EventEditorDialog.validateTeamPages(d);
    }
    @Test public void standardDropLinkFollowsTheCheckboxButRemainsAvailableForLegacyEvents()
    {
        assertTrue(EventEditorDialog.showSharedDropList("CLAN_EVENT", false, false));
        org.junit.Assert.assertFalse(EventEditorDialog.showSharedDropList("CLAN_EVENT", true, false));
        assertTrue(EventEditorDialog.showSharedDropList("CLAN_EVENT", true, true));
        assertTrue(EventEditorDialog.showSharedDropList("BOSS", false, false));
        org.junit.Assert.assertFalse(EventEditorDialog.showSharedDropList("LEARNER", false, false));
    }

    @Test public void switchingOffTeamsClearsSavedTeamFieldsButKeepsTheStandardLink()
    {
        String base = "https://docs.google.com/spreadsheets/d/12345678901234567890/edit";
        EventDraft draft = new EventDraft(); draft.type = "CLAN_EVENT"; draft.dropListUrl = base + "#gid=5";
        EventEditorDialog.configureTeams(draft, true, false, "Rood;Blauw", base + "#gid=1", base + "#gid=2", "");
        assertEquals(base + "#gid=2", draft.teamTwoDropListUrl);
        EventEditorDialog.configureTeams(draft, false, false, "Rood;Blauw", base + "#gid=1", base + "#gid=2", "");
        assertEquals("", draft.teamNames);
        assertEquals("", draft.teamOneDropListUrl);
        assertEquals("", draft.teamTwoDropListUrl);
        assertEquals("", draft.teamThreeDropListUrl);
        assertEquals(base + "#gid=5", draft.dropListUrl);
    }

    @Test public void newTeamEventsCannotSilentlyFallBackToTheStandardList()
    {
        EventDraft draft = new EventDraft(); draft.type = "CLAN_EVENT";
        draft.dropListUrl = "https://docs.google.com/spreadsheets/d/12345678901234567890/edit#gid=5";
        try
        {
            EventEditorDialog.configureTeams(draft, true, false, "Rood;Blauw", "", "", "");
            org.junit.Assert.fail("Aparte teamlinks ontbreken");
        }
        catch (IllegalArgumentException expected) { }
        EventEditorDialog.configureTeams(draft, true, true, "Rood;Blauw", "", "", "");
        assertEquals("Rood; Blauw", draft.teamNames);
    }
    @Test public void separateTeamPagesRequireTwoTeamsAndDistinctTabs()
    {
        String base = "https://docs.google.com/spreadsheets/d/12345678901234567890/edit";
        EventDraft draft = new EventDraft(); draft.type="CLAN_EVENT"; draft.teamNames="Rood;Blauw";
        draft.teamOneDropListUrl=base+"?gid=1"; draft.teamTwoDropListUrl=base+"#gid=2";
        EventEditorDialog.validateTeamPages(draft);
        for (String bad : new String[]{"",base+"#gid=1",base+"?gid=2#gid=3","https://attacker.example"})
        {
            draft.teamTwoDropListUrl=bad;
            try { EventEditorDialog.validateTeamPages(draft); org.junit.Assert.fail(bad); }
            catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void teamsAreOptionalAndOnlyForClanEvents()
    {
        assertEquals("", EventEditorDialog.validatedTeams("BOSS", "Rood;Blauw"));
        assertEquals("", EventEditorDialog.validatedTeams("CLAN_VS_CLAN", "Rood;Blauw"));
        assertEquals("", EventEditorDialog.validatedTeams("CLAN_EVENT", ""));
        assertEquals("Rood; Blauw", EventEditorDialog.validatedTeams("CLAN_EVENT", " Rood ;Blauw "));
        for (String names : new String[]{"Rood", "Rood;rood", "Rood;", "1;2;3;4;5;6;7;8", "Rood;<Blauw>"})
        {
            try { EventEditorDialog.validatedTeams("CLAN_EVENT", names); org.junit.Assert.fail(names); }
            catch (IllegalArgumentException expected) { }
        }
    }
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

package nl.dutchnations.server;

import java.time.Duration;
import org.junit.Test;
import static org.junit.Assert.*;

public class BattleshipSheetsTest
{
    @Test public void initialPublicProjectionHasTimeToCommitLargeAtomicBatch()
    {
        assertEquals(Duration.ofSeconds(60), BattleshipSheets.requestTimeout("POST", true));
    }

    @Test public void readsAndPrivateOperationsRetainShortDeadline()
    {
        assertEquals(Duration.ofSeconds(12), BattleshipSheets.requestTimeout("GET", true));
        assertEquals(Duration.ofSeconds(12), BattleshipSheets.requestTimeout("GET", false));
        assertEquals(Duration.ofSeconds(12), BattleshipSheets.requestTimeout("POST", false));
        assertEquals(Duration.ofSeconds(12), BattleshipSheets.requestTimeout("DELETE", false));
    }
}

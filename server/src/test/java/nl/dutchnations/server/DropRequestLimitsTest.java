package nl.dutchnations.server;

import org.junit.Test;
import static org.junit.Assert.*;

public class DropRequestLimitsTest
{
    @Test public void limitsEachKeyAndExpiresItsWindow()
    {
        DropRequestLimits limits = new DropRequestLimits();
        assertTrue(limits.allow("player:a", 2, 0));
        assertTrue(limits.allow("player:a", 2, 1));
        assertFalse(limits.allow("player:a", 2, 2));
        assertTrue(limits.allow("player:b", 2, 2));
        assertTrue(limits.allow("player:a", 2, 60000));
    }

    @Test public void duplicateScreenshotsAreTemporarilyRejected()
    {
        DropRequestLimits limits = new DropRequestLimits();
        assertTrue(limits.remember("digest", 0));
        assertFalse(limits.remember("digest", 1));
        assertTrue(limits.remember("other", 1));
        assertTrue(limits.remember("digest", 300000));
    }

    @Test public void randomKeysCannotGrowTheMapsWithoutBound()
    {
        DropRequestLimits limits = new DropRequestLimits();
        for (int i=0; i<4096; i++)
        {
            assertTrue(limits.allow("key"+i, 1, 0));
            assertTrue(limits.remember("key"+i, 0));
        }
        assertFalse(limits.allow("overflow", 1, 1));
        assertFalse(limits.remember("overflow", 1));
        assertTrue(limits.allow("new", 1, 60000));
        assertTrue(limits.remember("new", 300000));
    }
}

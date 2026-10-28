package nl.dutchnations.server;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiscordStartupTest
{
    @Test(timeout=5000) public void blockedDiscordDoesNotBlockStartingTheSheetWorker() throws Exception
    {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Thread connector = DutchNationsApi.startDiscordInBackground(() -> {
            entered.countDown();
            try { release.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        });
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTrue(connector.isAlive());assertTrue(connector.isDaemon());
            CountDownLatch sheetTick = new CountDownLatch(1);
            java.util.concurrent.ScheduledExecutorService worker = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
            try { worker.execute(sheetTick::countDown);assertTrue(sheetTick.await(2,TimeUnit.SECONDS)); }
            finally { worker.shutdownNow(); }
        } finally { release.countDown();connector.join(2000); }
        assertFalse(connector.isAlive());
    }
}

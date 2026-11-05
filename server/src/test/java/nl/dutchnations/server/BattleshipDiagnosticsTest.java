package nl.dutchnations.server;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class BattleshipDiagnosticsTest
{
    private static final String MAIN="public_sheet_123456789012345", ONE="private_one_123456789012345", TWO="private_two_123456789012345";
    private static final String SECRET="PRIVATE_KEY secret@example.test hidden-file-id C7";

    @Test public void rawMessagesAndCausesNeverReachDiagnosticSummary()
    {
        BattleshipDiagnostics d=new BattleshipDiagnostics();d.stage=BattleshipDiagnostics.Stage.WRITE_FLEET_2;
        for(Exception failure:List.of(new IOException(SECRET),new RuntimeException(SECRET),
            new HttpTimeoutException(SECRET),new BattleshipDiagnostics.Failure(BattleshipDiagnostics.Code.GOOGLE_AUTH,SECRET),
            new BattleshipDiagnostics.Failure(BattleshipDiagnostics.Code.GOOGLE_HTTP,403,SECRET)))
        {
            failure.initCause(new RuntimeException(SECRET));
            String result=d.summary(failure);
            assertTrue(result.contains("ZSL_"));assertTrue(result.contains("WRITE_FLEET_2"));
            for(String forbidden:SECRET.split(" "))assertFalse(result.contains(forbidden));
        }
    }

    @Test public void httpCodesDifferentiateAccessQuotaAndTemporaryFailures()
    {
        BattleshipDiagnostics d=new BattleshipDiagnostics();
        for(int status:new int[]{400,401,403,404,429,500,503})
            assertTrue(d.summary(new BattleshipDiagnostics.Failure(BattleshipDiagnostics.Code.GOOGLE_HTTP,status,SECRET)).contains("HTTP="+status));
        assertTrue(d.summary(new BattleshipDiagnostics.Failure(BattleshipDiagnostics.Code.GOOGLE_HTTP,500,SECRET)).contains("tijdelijke storing"));
        assertTrue(d.summary(new BattleshipDiagnostics.Failure(BattleshipDiagnostics.Code.GOOGLE_HTTP,429,SECRET)).contains("beperkt"));
        assertFalse(d.summary(new BattleshipDiagnostics.Failure(BattleshipDiagnostics.Code.GOOGLE_HTTP,-1,SECRET)).contains("HTTP="));
    }

    @Test public void trackedGatewayKeepsPrivateIdsOutAndTracksFailingOperation() throws Exception
    {
        BattleshipDiagnostics d=new BattleshipDiagnostics();
        BattleshipWorkerTest.Google fake=new BattleshipWorkerTest.Google();fake.failPublish=true;
        BattleshipGateway gateway=d.track(fake,MAIN,ONE);
        gateway.batchValues(ONE,"Vloot!Q1");assertEquals(BattleshipDiagnostics.Stage.GAME_STATE,d.stage);
        try { gateway.write(MAIN,Map.of("test",List.of(List.of("x"))));fail(); }
        catch(IOException error)
        {
            String result=d.summary(error);
            assertTrue(result.contains("WRITE_PUBLIC"));assertFalse(result.contains("SECRET"));
            assertFalse(result.contains(MAIN));assertFalse(result.contains(ONE));assertFalse(result.contains(TWO));
        }
        gateway.privateAccess(TWO);assertEquals(BattleshipDiagnostics.Stage.GAME_STATE,d.stage);
    }

    @Test public void workerLogsFixedCodeAndStageWithoutLeakingFleetOnFailure() throws Exception
    {
        BattleshipWorkerTest.Google fake=new BattleshipWorkerTest.Google();fake.failPublish=true;
        DutchNationsApi.Store store=new DutchNationsApi.Store(Files.createTempDirectory("battleship-safe-diagnostics-").resolve("state.json"));
        BattleshipSync worker=new BattleshipSync(store,fake,MAIN,ONE,TWO,"test",()->BattleshipSync.date(46023.4));
        worker.tick();
        String message=store.ownerLogs().errors.get(0).message;
        assertTrue(message.contains("ZSL_CONNECTION"));assertTrue(message.contains("WRITE_PUBLIC"));
        assertFalse(message.contains("SECRET"));assertFalse(message.contains("J9"));assertFalse(message.contains(ONE));
        assertTrue(fake.openOne.isEmpty()&&fake.openTwo.isEmpty());
        worker.tick();assertEquals(1,store.ownerLogs().errors.size());
    }

    @Test public void invalidCredentialsExposeOnlyAuthenticationCategory()
    {
        try {new BattleshipSheets("{not-json-"+SECRET);fail();}
        catch(IOException error)
        {
            assertTrue(error instanceof BattleshipDiagnostics.Failure);
            assertTrue(new BattleshipDiagnostics().summary(error).contains("ZSL_GOOGLE_AUTH"));
            assertFalse(error.toString().contains(SECRET));
        }
    }
}

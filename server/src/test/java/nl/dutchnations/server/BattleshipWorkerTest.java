package nl.dutchnations.server;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class BattleshipWorkerTest
{
    private static final String MAIN="public_sheet_123456789012345", ONE="private_one_123456789012345", TWO="private_two_123456789012345";
    static final class Google implements BattleshipGateway
    {
        Map<String,List<List<?>>> data=new HashMap<>();
        List<Map<String,List<List<?>>>> publicWrites=new ArrayList<>();
        Set<String> openOne=Set.of(),openTwo=Set.of();
        boolean failPublish;
        Google()
        {
            data.put(MAIN+"Instellingen!B10:F12",List.of(List.of(46023.5,"","","",46023.6)));
            data.put(MAIN+"Instellingen!A17:G17",List.of(List.of("Rood","","","","Blauw")));
            data.put(MAIN+"Borddata!Z2",List.of(List.of("Zeeslag")));
            data.put(MAIN+"Screenshotlijst!A1:H1001",new ArrayList<>(BattleshipSyncTest.rows()));
            for(String f:List.of(ONE,TWO))
            {
                data.put(f+"Vloot!C6:D10",new ArrayList<>(BattleshipGameTest.fleet()));
                data.put(f+"Vloot!Q1",List.of(List.of("")));
                data.put(f+"Vloot!B17",List.of(List.of(false)));
            }
        }
        @SuppressWarnings("unchecked") public List<List<List<Object>>> batchValues(String f,String...ranges)
        {
            List<List<List<Object>>> out=new ArrayList<>();
            for(String r:ranges)
            {
                if(r.equals("Zeeslag!E20:J20")||r.equals("Zeeslag!Q20:V20"))
                {
                    assertTrue("must lock before reading",openOne.isEmpty()&&openTwo.isEmpty());
                    boolean first=r.contains("E20");
                    Object target=cell(data.get(f+(first?"Zeeslag!E20":"Zeeslag!Q20")));
                    Object submit=cell(data.get(f+(first?"Zeeslag!J20":"Zeeslag!V20")));
                    out.add(List.of(List.of(target,"","","","",submit)));
                }
                else out.add((List<List<Object>>)(Object)data.getOrDefault(f+r,List.of()));
            }
            return out;
        }
        static Object cell(List<List<?>> r){return r==null?"":r.get(0).get(0);}
        public void write(String f,Map<String,List<List<?>>> values) throws IOException
        {
            if(f.equals(MAIN))
            {
                if(failPublish){failPublish=false;throw new IOException("SECRET PRIVATE FLEET J9");}
                publicWrites.add(new HashMap<>(values));
            }
            values.forEach((k,v)->data.put(f+k,v));
        }
        public BattleshipSheets.Access privateAccess(String f)
        {
            BattleshipSheets.Access access=new BattleshipSheets.Access();
            access.readers.add("owner@test"); access.owners.add("owner@test");
            access.writers.add(f.equals(ONE)?"red@test":"blue@test");return access;
        }
        public void protect(String f,Set<String>a,Set<String>b){openOne=a;openTwo=b;}
        public void privateLocked(String f,boolean locked){}
        public void sourceLocked(String f,boolean locked,boolean payment){}
        void submit(String target){data.put(MAIN+"Zeeslag!E20",List.of(List.of(target)));data.put(MAIN+"Zeeslag!J20",List.of(List.of(true)));}
        void ready(){for(String f:List.of(ONE,TWO))data.put(f+"Vloot!B17",List.of(List.of(true)));}
    }
    @Test public void publicationRetryAndRestartCannotDoubleChargeOrLeakFleet() throws Exception
    {
        Google google=new Google();long start=BattleshipSync.date(46023.5);long[] now={start-1000};
        java.nio.file.Path f=Files.createTempDirectory("battleship-worker-").resolve("state.json");
        DutchNationsApi.Store store=new DutchNationsApi.Store(f);
        BattleshipSync sync=new BattleshipSync(store,google,MAIN,ONE,TWO,"test",()->now[0]);
        sync.tick();google.ready();sync.tick();
        String key=MAIN+"@"+start+":"+BattleshipSync.date(46023.6);
        assertTrue(store.battleship(key).ready);
        now[0]=start+1; google.submit("J10");google.failPublish=true;sync.tick();
        assertEquals(1,store.battleship(key).shots.get(0).size());assertTrue(google.openOne.isEmpty());
        DutchNationsApi.Store reload=new DutchNationsApi.Store(f);
        new BattleshipSync(reload,google,MAIN,ONE,TWO,"test",()->now[0]).tick();
        assertEquals(1,reload.battleship(key).shots.get(0).size());assertEquals(false,Google.cell(google.data.get(MAIN+"Zeeslag!J20")));
        assertTrue(google.openOne.isEmpty());assertTrue(google.openTwo.isEmpty());
        String published=new Gson().toJson(google.publicWrites);
        assertFalse(published.contains("SECRET"));assertFalse(published.contains(ONE));assertFalse(published.contains(TWO));assertFalse(published.contains("fleets"));
    }
    @Test public void cannotArmLateAndInvalidatedReadinessDoesNotStartOldFleet() throws Exception
    {
        Google google=new Google();long start=BattleshipSync.date(46023.5);long[]now={start+1};
        DutchNationsApi.Store store=new DutchNationsApi.Store(Files.createTempDirectory("battleship-late-").resolve("state.json"));
        BattleshipSync sync=new BattleshipSync(store,google,MAIN,ONE,TWO,"test",()->now[0]);
        sync.tick();assertNull(store.battleship(MAIN+"@"+start+":"+BattleshipSync.date(46023.6)));
        now[0]=start-1000;sync.tick();google.ready();sync.tick();
        String key=MAIN+"@"+start+":"+BattleshipSync.date(46023.6);assertTrue(store.battleship(key).ready);
        google.data.put(ONE+"Vloot!B17",List.of(List.of(false)));sync.tick();assertFalse(store.battleship(key).ready);
        now[0]=start+1;google.submit("A1");sync.tick();assertEquals(0,store.battleship(key).shots.get(0).size());
    }
    @Test public void changingFleetAfterStartCannotMoveStoredBoats() throws Exception
    {
        Google google=new Google();long start=BattleshipSync.date(46023.5);long[]now={start-1000};
        DutchNationsApi.Store store=new DutchNationsApi.Store(Files.createTempDirectory("battleship-frozen-").resolve("state.json"));
        BattleshipSync sync=new BattleshipSync(store,google,MAIN,ONE,TWO,"test",()->now[0]);
        sync.tick();google.ready();sync.tick();now[0]=start+1;sync.tick();
        google.data.put(TWO+"Vloot!C6:D10",List.of());google.submit("A1");sync.tick();
        String key=MAIN+"@"+start+":"+BattleshipSync.date(46023.6);
        assertEquals("RAAK",store.battleship(key).outcome(0,"A1"));
    }
}

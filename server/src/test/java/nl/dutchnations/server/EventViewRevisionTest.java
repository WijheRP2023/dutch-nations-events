package nl.dutchnations.server;

import org.junit.Test;
import static org.junit.Assert.*;

public class EventViewRevisionTest
{
    @Test public void initialPublicationAndFailedRetryRemainImmediate()
    {
        EventViewSync.RevisionGate gate=new EventViewSync.RevisionGate();
        assertTrue(gate.due("100",0));
        assertTrue("not acknowledged before successful publication",gate.due("100",1000));
        gate.committed("100",1000);
        assertFalse(gate.due("100",2000));
    }
    @Test public void editsSettleAndReceiveOnlyOneRecalculationFollowup()
    {
        EventViewSync.RevisionGate gate=new EventViewSync.RevisionGate();gate.committed("100",0);
        assertFalse(gate.due("101",1000));assertTrue(gate.pending());
        assertFalse(gate.due("102",2000));
        assertFalse(gate.due("102",3000));assertTrue(gate.due("102",4000));
        assertTrue("failed publish must retry",gate.due("102",5000));
        gate.committed("102",5000);assertFalse(gate.pending());
        assertFalse(gate.due("102",19999));assertTrue(gate.due("102",20000));
        gate.committed("102",20000);
        assertFalse(gate.due("102",40000));
        assertFalse(gate.due("102",EventViewSync.RevisionGate.REPAIR_MILLIS+19999));
        assertTrue(gate.due("102",EventViewSync.RevisionGate.REPAIR_MILLIS+20000));
    }
    @Test public void versionAdvancingDuringPublicationIsNotLost()
    {
        EventViewSync.RevisionGate gate=new EventViewSync.RevisionGate();gate.committed("100",0);
        assertFalse(gate.due("101",1000));assertTrue(gate.due("101",3000));
        gate.committed("101",9000);
        assertFalse(gate.due("102",10000));assertTrue(gate.due("102",12000));
    }
    @Test public void invalidVersionIsNeverConsideredCurrent()
    {
        for(String version:new String[]{null,"","x","1\n2"})
        {
            try { new EventViewSync.RevisionGate().due(version,0);fail("invalid version"); }
            catch(IllegalArgumentException expected){ }
        }
    }
    @Test public void continuousEditorsCannotPostponeUpdatesForever()
    {
        EventViewSync.RevisionGate gate=new EventViewSync.RevisionGate();gate.committed("100",0);
        for(int n=1;n<=10;n++)assertFalse(gate.due(Integer.toString(100+n),n*1000));
        assertTrue(gate.due("111",11000));
    }
}

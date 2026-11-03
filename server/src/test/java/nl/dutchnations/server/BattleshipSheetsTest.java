package nl.dutchnations.server;

import java.time.Duration;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;
import org.junit.Test;
import static org.junit.Assert.*;

public class BattleshipSheetsTest
{
    @Test public void compressedAndPlainGoogleResponsesHaveTheSameBoundedContent() throws Exception
    {
        byte[] value="{\"version\":\"12345678901234567890\"}".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        try(GZIPOutputStream zip=new GZIPOutputStream(buffer)){zip.write(value);}
        assertArrayEquals(value,BattleshipSheets.responseBytes(new ByteArrayInputStream(value),"",value.length));
        assertArrayEquals(value,BattleshipSheets.responseBytes(new ByteArrayInputStream(buffer.toByteArray()),"gzip",value.length));
        try { BattleshipSheets.responseBytes(new ByteArrayInputStream(buffer.toByteArray()),"gzip",value.length-1);fail("decoded size must be bounded"); }
        catch(IOException expected){assertEquals("Google-antwoord is te groot",expected.getMessage());}
    }
    @Test public void malformedAndUnknownCompressionFailClosed() throws Exception
    {
        for(String encoding:new String[]{"gzip","br"})
        {
            try { BattleshipSheets.responseBytes(new ByteArrayInputStream(new byte[]{1,2,3}),encoding,100);fail("invalid response"); }
            catch(IOException expected){ }
        }
    }
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

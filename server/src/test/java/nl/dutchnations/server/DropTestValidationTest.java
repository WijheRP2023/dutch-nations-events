package nl.dutchnations.server;

import java.util.Base64;
import org.junit.Test;
import static org.junit.Assert.*;

public class DropTestValidationTest
{
    private static DutchNationsApi.DropTestReport report()
    {
        DutchNationsApi.DropTestReport report = new DutchNationsApi.DropTestReport();
        report.eventId = "test-event";
        report.rsn = "Q-Dance Jr";
        report.item = "Dragon claws";
        report.quantity = 1;
        report.screenshot = Base64.getEncoder().encodeToString(new byte[] {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0
        });
        return report;
    }

    @Test public void acceptsImageWithSafeDropMetadata()
    {
        assertNotNull(DutchNationsApi.validateDropTest(report()));
    }

    @Test public void rejectsMissingOrUnsafeData()
    {
        assertNull(DutchNationsApi.validateDropTest(null));
        DutchNationsApi.DropTestReport invalid = report();
        invalid.rsn = "@everyone";
        assertNull(DutchNationsApi.validateDropTest(invalid));
        invalid = report(); invalid.item = "<@123>";
        assertNull(DutchNationsApi.validateDropTest(invalid));
        invalid = report(); invalid.quantity = 0;
        assertNull(DutchNationsApi.validateDropTest(invalid));
        invalid = report(); invalid.eventId = "";
        assertNull(DutchNationsApi.validateDropTest(invalid));
        invalid = report(); invalid.screenshot = "not-base64";
        assertNull(DutchNationsApi.validateDropTest(invalid));
        invalid = report(); invalid.screenshot = Base64.getEncoder().encodeToString(new byte[] {1, 2, 3});
        assertNull(DutchNationsApi.validateDropTest(invalid));
        invalid = report(); invalid.screenshot = "A".repeat(5_592_409);
        assertNull(DutchNationsApi.validateDropTest(invalid));
    }
}

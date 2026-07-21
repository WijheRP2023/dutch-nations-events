package nl.dutchnations.events;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import okhttp3.OkHttpClient;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class FeedFileTest
{
    @Test
    public void githubFeedIsValid() throws Exception
    {
        String json = new String(Files.readAllBytes(Paths.get("feed.json")), StandardCharsets.UTF_8);
        FeedService service = new FeedService(new OkHttpClient(), new Gson());
        assertNotNull("feed.json voldoet niet aan het verwachte formaat", service.parse(json));
    }
}

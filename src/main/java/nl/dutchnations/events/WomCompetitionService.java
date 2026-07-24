package nl.dutchnations.events;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

final class WomCompetitionService
{
    static final String GROUP_URL = "https://wiseoldman.net/groups/1476";
    private static final String API_URL = "https://api.wiseoldman.net/v2/groups/1476/competitions?limit=50";
    private final OkHttpClient client;
    private final Gson gson;

    WomCompetitionService(OkHttpClient client, Gson gson) { this.client = client; this.gson = gson; }

    void fetch(Listener listener)
    {
        Request request = new Request.Builder().url(API_URL)
            .header("User-Agent", "Dutch-Nations-RuneLite-Plugin").get().build();
        client.newCall(request).enqueue(new Callback()
        {
            @Override public void onFailure(Call call, IOException exception) { listener.failure(); }
            @Override public void onResponse(Call call, Response response)
            {
                try (Response closeable = response)
                {
                    if (!response.isSuccessful() || response.body() == null) { listener.failure(); return; }
                    WomCompetition[] responseValues = gson.fromJson(response.body().charStream(), WomCompetition[].class);
                    List<WomCompetition> values = responseValues == null
                        ? java.util.Collections.emptyList()
                        : Arrays.asList(responseValues);
                    listener.success(WomCompetition.currentOrNext(values, java.time.OffsetDateTime.now()));
                }
                catch (RuntimeException exception) { listener.failure(); }
            }
        });
    }

    interface Listener
    {
        void success(WomCompetition competition);
        void failure();
    }
}
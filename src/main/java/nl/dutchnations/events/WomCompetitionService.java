package nl.dutchnations.events;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
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
    private static final Type LIST_TYPE = new TypeToken<List<WomCompetition>>() { }.getType();
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
                    List<WomCompetition> values = gson.fromJson(response.body().charStream(), LIST_TYPE);
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
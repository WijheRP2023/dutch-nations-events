package nl.dutchnations.server;

import java.util.HashMap;
import java.util.Map;

/** Bounded, transient abuse protection; no screenshots or identifiers are persisted. */
final class DropRequestLimits
{
    private final Map<String, Window> windows = new HashMap<>();
    private final Map<String, Long> recent = new HashMap<>();

    synchronized boolean allow(String key, int maximum, long now)
    {
        windows.values().removeIf(value -> value.expiresAt <= now);
        Window window = windows.get(key);
        if (window == null)
        {
            if (windows.size() >= 4096) return false;
            window = new Window(now + 60000);
            windows.put(key, window);
        }
        if (window.count >= maximum) return false;
        window.count++;
        return true;
    }

    synchronized boolean remember(String digest, long now)
    {
        recent.values().removeIf(expiry -> expiry <= now);
        if (recent.containsKey(digest) || recent.size() >= 4096) return false;
        recent.put(digest, now + 300000);
        return true;
    }

    private static final class Window
    {
        final long expiresAt;
        int count;
        Window(long expiresAt) { this.expiresAt = expiresAt; }
    }
}

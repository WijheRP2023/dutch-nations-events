package nl.dutchnations.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

/** Read-only view of the public PvM War progress for the screenshot test. */
final class PvmWarProgress
{
    static final String SOURCE = "PVM_WAR_2026";
    private static final URI PAGE = URI.create("https://osrs-nlbe.com/author/dutch_nation/pvm-war-2026/");
    private static final int MAX_PAGE_BYTES = 2_000_000;
    private final Map<String, Slots> items;

    private PvmWarProgress(Map<String, Slots> items) { this.items = items; }

    static PvmWarProgress fetch() throws IOException
    {
        HttpURLConnection connection = (HttpURLConnection) PAGE.toURL().openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(10000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "DutchNationDropTest/1.0");
        try
        {
            if (connection.getResponseCode() != 200) throw new IOException("PvM War-pagina niet beschikbaar");
            byte[] page;
            try (InputStream body = connection.getInputStream()) { page = body.readNBytes(MAX_PAGE_BYTES + 1); }
            if (page.length > MAX_PAGE_BYTES) throw new IOException("PvM War-pagina te groot");
            PvmWarProgress progress = parse(new String(page, StandardCharsets.UTF_8));
            // Reject error pages or changed layouts; missing items must never silently become zero.
            if (progress.items.size() < 100 || progress.lookup("Jar Tier 1") == null
                || progress.lookup("Dragon claws") == null)
                throw new IOException("PvM War-pagina heeft een onbekende indeling");
            return progress;
        }
        finally { connection.disconnect(); }
    }

    static PvmWarProgress parse(String html) throws IOException
    {
        Map<String, Slots> result = new HashMap<>();
        HTMLEditorKit.ParserCallback callback = new HTMLEditorKit.ParserCallback()
        {
            String item;
            int cells, approved, waiting;
            boolean title, invalid;

            @Override public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position)
            {
                if (tag == HTML.Tag.TR)
                {
                    item = attr(attributes, "data-item"); cells = 0; approved = 0; waiting = 0;
                    title = false; invalid = false;
                }
                else if (item != null && tag == HTML.Tag.TD)
                {
                    cells++;
                    if (cells > 1)
                    {
                        String css = attr(attributes, "class");
                        css = css == null ? "" : css.trim();
                        if ("approved".equals(css)) approved++;
                        else if ("waiting".equals(css)) waiting++;
                        else if (!css.isEmpty()) invalid = true;
                    }
                }
                else if (item != null && tag == HTML.Tag.SPAN)
                {
                    String css = attr(attributes, "class");
                    if (css != null && (" " + css + " ").contains(" item-name__title ")) title = true;
                }
            }

            @Override public void handleEndTag(HTML.Tag tag, int position)
            {
                if (tag == HTML.Tag.TR && item != null)
                {
                    if (title && cells == 4 && !invalid && approved + waiting <= 3)
                    {
                        String key = normalize(item);
                        // Duplicates are ambiguous; reject them rather than assuming availability.
                        if (result.containsKey(key)) result.put(key, new Slots(-1, -1));
                        else result.put(key, new Slots(approved, waiting));
                    }
                    item = null;
                }
            }
        };
        try (Reader reader = new StringReader(html)) { new ParserDelegator().parse(reader, callback, true); }
        return new PvmWarProgress(result);
    }

    Slots lookup(String item)
    {
        if (item == null) return null;
        String key = normalize(item);
        if (key.startsWith("jar of "))
        {
            String name = key.substring("jar of ".length());
            if ("|dreams|spirits|stone|".contains("|" + name + "|")) key = "jar tier 1";
            else if (("|chemicals|decay|sand|souls|swamp|venom|").contains("|" + name + "|")) key = "jar tier 2";
            else if (("|dirt|eyes|feathers|light|miasma|smoke|").contains("|" + name + "|")) key = "jar tier 3";
        }
        Slots slots = items.get(key);
        return slots == null || slots.approved < 0 ? null : slots;
    }

    private static String normalize(String item) { return item.trim().replace('\u2019', '\'').toLowerCase(Locale.ROOT); }
    private static String attr(MutableAttributeSet set, String name)
    {
        for (Enumeration<?> keys = set.getAttributeNames(); keys.hasMoreElements();)
        {
            Object key = keys.nextElement();
            if (name.equalsIgnoreCase(key.toString())) return String.valueOf(set.getAttribute(key));
        }
        return null;
    }

    static final class Slots
    {
        final int approved, waiting;
        Slots(int approved, int waiting) { this.approved = approved; this.waiting = waiting; }
    }
}

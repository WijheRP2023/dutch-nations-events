package nl.dutchnations.server;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads drop names from the event Sheet; it never writes to Google. */
final class GoogleSheetDrops
{
    private static final String SCOPE = "https://www.googleapis.com/auth/spreadsheets.readonly";
    private static final Pattern PATH = Pattern.compile("^/spreadsheets/d/([A-Za-z0-9_-]{20,128})(?:/.*)?$");
    private static final Pattern GID = Pattern.compile("(?:^|&)gid=([0-9]{1,20})(?:&|$)");
    private static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_ITEMS = 500;
    private static GoogleCredentials credentials;

    private GoogleSheetDrops() { }

    static String sheetId(String url)
    {
        if (url == null || url.length() > 2000) return null;
        try
        {
            URI uri = URI.create(url.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"docs.google.com".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) return null;
            Matcher match = PATH.matcher(uri.getRawPath());
            return match.matches() ? match.group(1) : null;
        }
        catch (RuntimeException ex) { return null; }
    }

    static Set<String> fetch(String link) throws IOException
    {
        String id = sheetId(link);
        if (id == null) throw new IOException("Geen ondersteunde Google Sheets-link");
        // Een openbare Sheet heeft geen Google-account of service-account nodig.
        // Probeer die route eerst, zodat de eventbeheerder niets extra's hoeft te delen.
        try
        {
            URI publicCsv = publicCsv(link, id);
            return parseCsv(new String(read(publicCsv, null), StandardCharsets.UTF_8));
        }
        catch (IOException publicFailure)
        {
            return fetchPrivate(id, publicFailure);
        }
    }

    private static URI publicCsv(String link, String id)
    {
        URI original = URI.create(link.trim());
        Matcher gid = GID.matcher(original.getRawQuery() == null ? "" : original.getRawQuery());
        String tab = gid.find() ? "gid=" + gid.group(1) : "sheet=Instellingen";
        return URI.create("https://docs.google.com/spreadsheets/d/" + id + "/gviz/tq?tqx=out:csv&" + tab);
    }

    private static Set<String> fetchPrivate(String id, IOException publicFailure) throws IOException
    {
        String jsonKey = System.getenv("DN_GOOGLE_SERVICE_ACCOUNT_JSON");
        if (jsonKey == null || jsonKey.trim().isEmpty() || jsonKey.length() > 50000)
            throw new IOException("Sheet is niet openbaar bereikbaar; deel hem openbaar of stel Google-leestoegang in", publicFailure);
        GoogleCredentials access = credentials(jsonKey);
        access.refreshIfExpired();
        if (access.getAccessToken() == null) throw new IOException("Google-leestoegang niet beschikbaar");
        URI api = URI.create("https://sheets.googleapis.com/v4/spreadsheets/" + id + "/values/Instellingen%21G3%3AH1000");
        return parse(new String(read(api, "Bearer " + access.getAccessToken().getTokenValue()), StandardCharsets.UTF_8));
    }

    private static byte[] read(URI uri, String authorization) throws IOException
    {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(6000); connection.setReadTimeout(10000);
        connection.setInstanceFollowRedirects(false);
        if (authorization != null) connection.setRequestProperty("Authorization", authorization);
        try
        {
            if (connection.getResponseCode() != 200) throw new IOException("Sheet gaf HTTP " + connection.getResponseCode());
            byte[] bytes;
            try (java.io.InputStream input = connection.getInputStream()) { bytes = input.readNBytes(MAX_BYTES + 1); }
            if (bytes.length > MAX_BYTES) throw new IOException("Sheetgegevens zijn te groot");
            return bytes;
        }
        finally { connection.disconnect(); }
    }

    private static synchronized GoogleCredentials credentials(String jsonKey) throws IOException
    {
        if (credentials != null) return credentials;
        try (ByteArrayInputStream key = new ByteArrayInputStream(jsonKey.getBytes(StandardCharsets.UTF_8)))
        {
            credentials = ServiceAccountCredentials.fromStream(key).createScoped(Collections.singleton(SCOPE));
        }
        return credentials;
    }

    static Set<String> parse(String json) throws IOException
    {
        try
        {
            JsonObject data = JsonParser.parseString(json).getAsJsonObject();
            JsonArray rows = data.getAsJsonArray("values");
            if (rows == null || rows.size() < 2) throw new IOException("Geen droplijst gevonden");
            List<List<String>> table = new ArrayList<>();
            for (int i = 1; i < rows.size(); i++)
            {
                JsonElement value = rows.get(i);
                if (!value.isJsonArray()) throw new IOException("Ongeldige rij in droplijst");
                JsonArray row = value.getAsJsonArray();
                List<String> values = new ArrayList<>();
                for (JsonElement cell : row) values.add(cell.isJsonNull() ? "" : cell.getAsString());
                table.add(values);
            }
            JsonArray header = rows.get(0).getAsJsonArray();
            List<String> headerValues = new ArrayList<>();
            for (JsonElement cell : header) headerValues.add(cell.isJsonNull() ? "" : cell.getAsString());
            table.add(0, headerValues);
            return parseTable(table);
        }
        catch (IOException ex) { throw ex; }
        catch (RuntimeException ex) { throw new IOException("Sheetgegevens hebben een onbekende indeling", ex); }
    }

    static Set<String> parseCsv(String csv) throws IOException
    {
        return parseTable(csvRows(csv));
    }

    private static Set<String> parseTable(List<List<String>> rows) throws IOException
    {
        int headerRow = -1;
        int itemColumn = -1;
        for (int rowIndex = 0; rowIndex < rows.size() && headerRow == -1; rowIndex++)
        {
            List<String> row = rows.get(rowIndex);
            for (int column = 0; column + 1 < row.size(); column++)
            {
                if ("drop (bewerkbaar)".equals(row.get(column).replace("\ufeff", "").trim().toLowerCase(Locale.ROOT))
                    && "doel".equals(row.get(column + 1).trim().toLowerCase(Locale.ROOT)))
                { headerRow = rowIndex; itemColumn = column; break; }
            }
        }
        if (headerRow == -1)
            throw new IOException("Sheet heeft niet de verwachte indeling");
        Set<String> names = new LinkedHashSet<>();
        Set<String> normalized = new LinkedHashSet<>();
        for (int i = headerRow + 1; i < rows.size(); i++)
        {
            List<String> row = rows.get(i);
            String item = row.size() <= itemColumn ? "" : row.get(itemColumn).trim();
            if (item.isEmpty()) continue;
            if (item.length() > 100 || item.indexOf(';') >= 0 || item.chars().anyMatch(Character::isISOControl))
                throw new IOException("Ongeldige dropnaam in sheet");
            String key = item.replace('\u2019', '\'').toLowerCase(Locale.ROOT);
            if (!normalized.add(key) || names.size() >= MAX_ITEMS) throw new IOException("Dubbele of te veel drops in sheet");
            names.add(item);
        }
        if (names.isEmpty()) throw new IOException("Geen dropnamen gevonden");
        return Collections.unmodifiableSet(names);
    }

    private static List<List<String>> csvRows(String csv) throws IOException
    {
        List<List<String>> rows = new ArrayList<>(); List<String> row = new ArrayList<>(); StringBuilder cell = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < csv.length(); i++)
        {
            char value = csv.charAt(i);
            if (value == '"')
            {
                if (quoted && i + 1 < csv.length() && csv.charAt(i + 1) == '"') { cell.append(value); i++; }
                else quoted = !quoted;
            }
            else if (value == ',' && !quoted) { row.add(cell.toString()); cell.setLength(0); }
            else if ((value == '\n' || value == '\r') && !quoted)
            {
                if (value == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') i++;
                row.add(cell.toString()); cell.setLength(0); rows.add(row); row = new ArrayList<>();
            }
            else cell.append(value);
        }
        if (quoted) throw new IOException("Ongeldige CSV in Sheet");
        if (cell.length() > 0 || !row.isEmpty()) { row.add(cell.toString()); rows.add(row); }
        return rows;
    }
}

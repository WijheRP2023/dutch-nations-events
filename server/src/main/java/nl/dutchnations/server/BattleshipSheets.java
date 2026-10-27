package nl.dutchnations.server;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fixed Google API hosts only. No credentials or fleet data are logged. */
final class BattleshipSheets implements BattleshipGateway
{
    private static final Gson JSON = new Gson();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ServiceAccountCredentials credentials;

    BattleshipSheets(String key) throws IOException
    {
        if (key == null || key.length() > 50000) throw new IOException("Google-servertoegang ontbreekt");
        try
        {
            credentials = (ServiceAccountCredentials) ServiceAccountCredentials.fromStream(new ByteArrayInputStream(key.getBytes(StandardCharsets.UTF_8)))
                .createScoped(Arrays.asList("https://www.googleapis.com/auth/spreadsheets", "https://www.googleapis.com/auth/drive.metadata.readonly"));
        }
        catch (Exception failure) { throw new IOException("Google-servertoegang is ongeldig"); }
    }

    static String id(String value)
    {
        if (value == null || !value.matches("[A-Za-z0-9_-]{20,128}")) throw new IllegalArgumentException("Ongeldige Sheet-koppeling");
        return value;
    }

    List<List<Object>> values(String file, String range) throws IOException
    {
        JsonObject result = request("GET", "https://sheets.googleapis.com/v4/spreadsheets/" + id(file) + "/values/" + encode(range)
            + "?valueRenderOption=UNFORMATTED_VALUE", null);
        List<List<Object>> rows = new ArrayList<>();
        JsonArray data = result.getAsJsonArray("values");
        if (data != null) for (JsonElement r : data)
        {
            List<Object> row = new ArrayList<>();
            for (JsonElement c : r.getAsJsonArray()) row.add(c.isJsonNull() ? "" : c.getAsJsonPrimitive().isBoolean() ? c.getAsBoolean()
                : c.getAsJsonPrimitive().isNumber() ? c.getAsDouble() : c.getAsString());
            rows.add(row);
        }
        return rows;
    }

    public List<List<List<Object>>> batchValues(String file, String... ranges) throws IOException
    {
        StringBuilder url = new StringBuilder("https://sheets.googleapis.com/v4/spreadsheets/").append(id(file)).append("/values:batchGet?valueRenderOption=UNFORMATTED_VALUE");
        for (String range : ranges) url.append("&ranges=").append(encode(range));
        JsonArray values = request("GET", url.toString(), null).getAsJsonArray("valueRanges");
        if (values == null || values.size() != ranges.length) throw new IOException("Onvolledig Google-antwoord");
        List<List<List<Object>>> out = new ArrayList<>();
        for (JsonElement value : values)
        {
            List<List<Object>> rows = new ArrayList<>();
            JsonArray grid = value.getAsJsonObject().getAsJsonArray("values");
            if (grid != null) for (JsonElement r : grid)
            {
                List<Object> row = new ArrayList<>();
                for (JsonElement c : r.getAsJsonArray()) row.add(c.isJsonNull() ? "" : c.getAsJsonPrimitive().isBoolean() ? c.getAsBoolean()
                    : c.getAsJsonPrimitive().isNumber() ? c.getAsDouble() : c.getAsString());
                rows.add(row);
            }
            out.add(rows);
        }
        return out;
    }

    public void write(String file, Map<String, List<List<?>>> data) throws IOException
    {
        List<Object> values = new ArrayList<>();
        data.forEach((range, rows) -> values.add(map("range", range, "values", rows)));
        request("POST", "https://sheets.googleapis.com/v4/spreadsheets/" + id(file) + "/values:batchUpdate",
            map("valueInputOption", "RAW", "data", values));
    }

    static final class Access
    {
        final Set<String> owners = new HashSet<>(), readers = new HashSet<>(), writers = new HashSet<>();
    }

    public Access privateAccess(String file) throws IOException
    {
        Access access = new Access(); String page = "";
        for (int n = 0; n < 10; n++)
        {
            JsonObject result = request("GET", "https://www.googleapis.com/drive/v3/files/" + id(file)
                + "/permissions?pageSize=100&fields=" + encode("nextPageToken,permissions(type,emailAddress,role,deleted)")
                + (page.isEmpty() ? "" : "&pageToken=" + encode(page)), null);
            JsonArray permissions = result.getAsJsonArray("permissions");
            if (permissions == null) throw new IOException("Privérechten konden niet worden gecontroleerd");
            for (JsonElement p : permissions)
            {
                JsonObject permission = p.getAsJsonObject();
                if (permission.has("deleted") && permission.get("deleted").getAsBoolean()) continue;
                if (!"user".equals(string(permission, "type"))) throw new IOException("Een privévloot mag niet openbaar of met een groep/domein worden gedeeld");
                String email = string(permission, "emailAddress").toLowerCase(java.util.Locale.ROOT), role = string(permission, "role");
                if (email.isEmpty()) throw new IOException("Onbekende privétoegang");
                if (email.equals(credentials.getClientEmail().toLowerCase(java.util.Locale.ROOT))) continue;
                access.readers.add(email);
                if ("owner".equals(role)) access.owners.add(email);
                if ("owner".equals(role) || "writer".equals(role)) access.writers.add(email);
            }
            page = string(result, "nextPageToken");
            if (page.isEmpty()) return access;
        }
        throw new IOException("Te veel privétoegangsregels");
    }

    static void separated(Access first, Access second) throws IOException
    {
        Set<String> overlap = new HashSet<>(first.readers); overlap.retainAll(second.readers);
        Set<String> owners = new HashSet<>(first.owners); owners.retainAll(second.owners); overlap.removeAll(owners);
        if (!overlap.isEmpty()) throw new IOException("Een niet-eigenaar heeft toegang tot beide privévloten");
    }

    /** Freeze both input areas before reading a request, preventing edit/publish races. */
    public void protect(String file, Set<String> first, Set<String> second) throws IOException
    {
        JsonObject metadata = request("GET", "https://sheets.googleapis.com/v4/spreadsheets/" + id(file)
            + "?fields=" + encode("properties(timeZone),sheets(properties(sheetId,title),protectedRanges(protectedRangeId,description,range))"), null);
        if (!"Europe/Amsterdam".equals(string(metadata.getAsJsonObject("properties"), "timeZone")))
            throw new IOException("De eventsheet moet tijdzone Europe/Amsterdam gebruiken");
        List<Object> updates = new ArrayList<>();
        int board = sheet(metadata, "Zeeslag"), data = sheet(metadata, "Zeeslagdata");
        List<Object> free = Arrays.asList(range(board, 19, 20, 4, 6), range(board, 19, 20, 9, 10),
            range(board, 19, 20, 16, 18), range(board, 19, 20, 21, 22));
        protection(metadata, updates, board, "board", map("sheetId", board), Set.of(), free);
        protection(metadata, updates, data, "public-data", map("sheetId", data), Set.of(), List.of());
        for (int team = 0; team < 2; team++)
        {
            int offset = team * 12; Set<String> editors = team == 0 ? first : second;
            protection(metadata, updates, board, "coordinate-" + team, range(board, 19, 20, 4 + offset, 6 + offset), editors, null);
            protection(metadata, updates, board, "submit-" + team, range(board, 19, 20, 9 + offset, 10 + offset), editors, null);
        }
        request("POST", "https://sheets.googleapis.com/v4/spreadsheets/" + id(file) + ":batchUpdate", map("requests", updates));
    }

    public void privateLocked(String file, boolean locked) throws IOException
    {
        JsonObject metadata = request("GET", "https://sheets.googleapis.com/v4/spreadsheets/" + id(file)
            + "?fields=" + encode("sheets(properties(sheetId,title),protectedRanges(protectedRangeId,description,range))"), null);
        int sheet = sheet(metadata, "Vloot");
        List<Object> updates = new ArrayList<>();
        if (locked) protection(metadata, updates, sheet, "private-fleet", map("sheetId", sheet), Set.of(), List.of());
        else for (JsonElement s : metadata.getAsJsonArray("sheets"))
        {
            JsonObject sh = s.getAsJsonObject();
            if (!sh.has("protectedRanges")) continue;
            for (JsonElement p : sh.getAsJsonArray("protectedRanges"))
                if ("DN_ZEESLAG_private-fleet".equals(string(p.getAsJsonObject(), "description")))
                    updates.add(map("deleteProtectedRange", map("protectedRangeId", p.getAsJsonObject().get("protectedRangeId").getAsInt())));
        }
        if (!updates.isEmpty()) request("POST", "https://sheets.googleapis.com/v4/spreadsheets/" + id(file) + ":batchUpdate", map("requests", updates));
    }

    public void sourceLocked(String file, boolean locked, boolean payment) throws IOException
    {
        JsonObject metadata = request("GET", "https://sheets.googleapis.com/v4/spreadsheets/" + id(file)
            + "?fields=" + encode("sheets(properties(sheetId,title,gridProperties(rowCount)),protectedRanges(protectedRangeId,description,range))"), null);
        List<Object> updates = new ArrayList<>();
        if (locked)
        {
            int settings = sheet(metadata, "Instellingen"), drops = sheet(metadata, "Drops"), max = 150;
            for (JsonElement s : metadata.getAsJsonArray("sheets"))
            {
                JsonObject p = s.getAsJsonObject().getAsJsonObject("properties");
                if (p.get("sheetId").getAsInt() == settings) max = p.getAsJsonObject("gridProperties").get("rowCount").getAsInt();
            }
            List<Object> free = payment ? Arrays.asList(range(settings,18,max,1,2),range(settings,18,max,5,6)) : List.of();
            protection(metadata,updates,settings,"source-settings",map("sheetId",settings),Set.of(),free);
            protection(metadata,updates,drops,"source-drops",map("sheetId",drops),Set.of(),List.of());
        }
        else for (JsonElement s : metadata.getAsJsonArray("sheets"))
        {
            JsonObject sh = s.getAsJsonObject(); if (!sh.has("protectedRanges")) continue;
            for (JsonElement p : sh.getAsJsonArray("protectedRanges"))
                if (Set.of("DN_ZEESLAG_source-settings","DN_ZEESLAG_source-drops").contains(string(p.getAsJsonObject(),"description")))
                    updates.add(map("deleteProtectedRange",map("protectedRangeId",p.getAsJsonObject().get("protectedRangeId").getAsInt())));
        }
        if (!updates.isEmpty()) request("POST", "https://sheets.googleapis.com/v4/spreadsheets/" + id(file) + ":batchUpdate", map("requests",updates));
    }

    private void protection(JsonObject metadata, List<Object> updates, int sheetId, String suffix, Object range,
                            Set<String> allowed, List<Object> free) throws IOException
    {
        String tag = "DN_ZEESLAG_" + suffix;
        List<String> users = new ArrayList<>(allowed); users.add(credentials.getClientEmail());
        Map<String, Object> value = map("description", tag, "range", range, "warningOnly", false,
            "editors", map("users", users, "domainUsersCanEdit", false));
        if (free != null) value.put("unprotectedRanges", free);
        Integer found = null;
        for (JsonElement s : metadata.getAsJsonArray("sheets"))
        {
            JsonObject sh = s.getAsJsonObject();
            if (sh.getAsJsonObject("properties").get("sheetId").getAsInt() != sheetId || !sh.has("protectedRanges")) continue;
            for (JsonElement p : sh.getAsJsonArray("protectedRanges"))
            {
                JsonObject pr = p.getAsJsonObject();
                if (tag.equals(string(pr, "description"))) found = pr.get("protectedRangeId").getAsInt();
                else if (!pr.getAsJsonObject("range").has("startRowIndex") && !string(pr, "description").startsWith("DN_ZEESLAG_"))
                    throw new IOException("Bestaande bladbeveiliging wordt niet overschreven");
            }
        }
        if (found == null) updates.add(map("addProtectedRange", map("protectedRange", value)));
        else { value.put("protectedRangeId", found); updates.add(map("updateProtectedRange", map("protectedRange", value, "fields", "range,description,warningOnly,editors" + (free == null ? "" : ",unprotectedRanges")))); }
    }

    private static int sheet(JsonObject meta, String title) throws IOException
    {
        for (JsonElement s : meta.getAsJsonArray("sheets"))
        {
            JsonObject p = s.getAsJsonObject().getAsJsonObject("properties");
            if (title.equals(string(p, "title"))) return p.get("sheetId").getAsInt();
        }
        throw new IOException("Zeeslagtabblad ontbreekt");
    }

    private JsonObject request(String method, String url, Object data) throws IOException
    {
        credentials.refreshIfExpired();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12))
            .header("Authorization", "Bearer " + credentials.getAccessToken().getTokenValue());
        if (data == null) request.GET(); else request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(JSON.toJson(data)));
        try
        {
            HttpResponse<java.io.InputStream> response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (java.io.InputStream body = response.body())
            {
                if (response.statusCode() != 200) throw new IOException("Google-koppeling: HTTP " + response.statusCode());
                byte[] bytes = body.readNBytes(2_000_001);
                if (bytes.length > 2_000_000) throw new IOException("Google-antwoord is te groot");
                return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IOException("Google-controle onderbroken"); }
        catch (RuntimeException ex) { throw new IOException("Ongeldig Google-antwoord"); }
    }

    static Map<String, Object> map(Object... pairs)
    { Map<String, Object> out = new LinkedHashMap<>(); for (int n = 0; n < pairs.length; n += 2) out.put((String) pairs[n], pairs[n + 1]); return out; }
    private static Map<String, Object> range(int sheet, int r1, int r2, int c1, int c2)
    { return map("sheetId", sheet, "startRowIndex", r1, "endRowIndex", r2, "startColumnIndex", c1, "endColumnIndex", c2); }
    private static String string(JsonObject object, String key) { return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : ""; }
    private static String encode(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}

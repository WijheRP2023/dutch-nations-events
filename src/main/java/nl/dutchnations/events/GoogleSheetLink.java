package nl.dutchnations.events;

import java.net.URI;

/** Identifies supported sheet links without ever fetching the supplied URL. */
final class GoogleSheetLink
{
    private GoogleSheetLink() { }
    static String tabKey(String link)
    {
        if (!supported(link)) return null;
        URI uri = URI.create(link.trim());
        java.util.regex.Pattern gid = java.util.regex.Pattern.compile("(?:^|&)gid=([0-9]{1,20})(?:&|$)");
        java.util.regex.Matcher q = gid.matcher(uri.getRawQuery() == null ? "" : uri.getRawQuery());
        java.util.regex.Matcher f = gid.matcher(uri.getRawFragment() == null ? "" : uri.getRawFragment());
        String query = q.find() ? q.group(1) : null, fragment = f.find() ? f.group(1) : null;
        if (query != null && fragment != null && !query.equals(fragment)) return null;
        if (query == null && fragment == null) return null;
        return uri.getRawPath().split("/")[3] + "/" + (query == null ? fragment : query);
    }
    static boolean supported(String link)
    {
        try
        {
            if (link == null || link.length() > 2000) return false;
            URI uri = URI.create(link.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && "docs.google.com".equalsIgnoreCase(uri.getHost())
                && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)
                && uri.getRawPath().matches("^/spreadsheets/d/[A-Za-z0-9_-]{20,128}(/.*)?$");
        }
        catch (RuntimeException ex) { return false; }
    }
}

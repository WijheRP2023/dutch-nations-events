package nl.dutchnations.events;

import java.net.URI;

/** Identifies supported sheet links without ever fetching the supplied URL. */
final class GoogleSheetLink
{
    private GoogleSheetLink() { }
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

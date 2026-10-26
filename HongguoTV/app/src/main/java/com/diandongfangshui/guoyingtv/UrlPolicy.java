package com.diandongfangshui.guoyingtv;

import java.net.URI;
import java.util.Locale;

/** Restrict catalog artwork and media URLs to explicit HTTPS URLs. */
public final class UrlPolicy {
    private UrlPolicy() {}

    public static boolean isHttpsUrl(String raw) {
        if (raw == null || raw.length() > 4096) return false;
        try {
            URI uri = new URI(raw);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && host != null && !host.isEmpty()
                    && uri.getRawUserInfo() == null
                    && uri.getRawFragment() == null;
        } catch (Exception e) {
            return false;
        }
    }
}

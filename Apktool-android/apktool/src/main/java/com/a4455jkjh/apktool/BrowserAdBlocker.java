package com.a4455jkjh.apktool;

import android.net.Uri;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Small, local host-based ad and tracker filter for WebView subresources.
 *
 * This intentionally blocks only known advertising/measurement hosts. It does
 * not inspect page contents, credentials, or HTTPS traffic.
 */
final class BrowserAdBlocker {
    private static final Set<String> BLOCKED_HOSTS = new HashSet<String>();

    static {
        String[] hosts = new String[] {
                "2mdn.net", "33across.com", "adap.tv", "adcolony.com",
                "adform.net", "adhese.org", "adkernel.com", "adnxs.com",
                "adroll.com", "adsafeprotected.com", "adsrvr.org",
                "advertising.com", "adzerk.net", "amazon-adsystem.com",
                "analytics.google.com", "atdmt.com", "bidswitch.net",
                "bluekai.com", "casalemedia.com", "cedexis.com",
                "chartbeat.com", "contextweb.com", "criteo.com",
                "demdex.net", "dotomi.com", "doubleclick.net",
                "everesttech.net", "exelator.com", "eyeota.net",
                "facebook.net", "google-analytics.com", "googleadservices.com",
                "googlesyndication.com", "googletagmanager.com", "hotjar.com",
                " lijit.com", "mathtag.com", "media.net", "mgid.com",
                "moatads.com", "mxp4.net", "nrich.ai", "omnitagjs.com",
                "openx.net", "outbrain.com", "owneriq.net", "parsely.com",
                "permutive.com", "pgpartner.com", "pippio.com",
                "pixel.rubiconproject.com", "pubmatic.com", "quantserve.com",
                "rlcdn.com", "roidads.net", "rubiconproject.com",
                "scorecardresearch.com", "serving-sys.com", "sharethrough.com",
                "simpli.fi", "smartadserver.com", "spotxchange.com",
                "taboola.com", "tapad.com", "teads.tv", "tidaltv.com",
                "tradedesk.com", "turn.com", "undertone.com", "yieldmo.com"
        };
        for (String host : hosts) {
            BLOCKED_HOSTS.add(host.trim());
        }
    }

    private BrowserAdBlocker() {
    }

    static boolean shouldBlock(String url) {
        if (url == null || url.length() == 0) {
            return false;
        }
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            host = host.toLowerCase(Locale.US);
            for (String blockedHost : BLOCKED_HOSTS) {
                if (host.equals(blockedHost) || host.endsWith("." + blockedHost)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // Malformed resource URLs are left to WebView to handle.
        }
        return false;
    }
}
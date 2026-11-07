package com.harshith.resumematcher.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Finds the real client IP for the per-IP rate limits.
 *
 * <p>Every proxy appends the address it received the request from to X-Forwarded-For, so the
 * trustworthy part of the header is at the <b>right</b> end; anything further left may have been
 * typed in by the client. The rule:
 * <ol>
 *   <li>If the connection doesn't come from a trusted proxy, use the connection's address and
 *       ignore the header entirely (a client talking to us directly can't fake anything).</li>
 *   <li>Otherwise walk the header from the right, skipping at most {@code max-proxy-hops} entries
 *       that are trusted proxies. The next entry is the client.</li>
 * </ol>
 * The cap matters: on Render the chain is "client, Cloudflare edge, Render internal". With a cap of 2,
 * someone connecting through a Cloudflare address of their own (WARP, Workers) still can't push a
 * fake entry into the client position, because we stop skipping after Render's and Cloudflare's hops.
 *
 * <p>Configured with app.client-ip.trusted-proxies (CIDRs) and app.client-ip.max-proxy-hops.
 */
@Component
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);
    // Only plain IP literals are parsed (never hostnames, which would trigger DNS lookups).
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9a-fA-F:.]*:[0-9a-fA-F:.]*");

    private final List<IpAddressMatcher> trusted;
    private final int maxProxyHops;
    private final AtomicBoolean shapeLogged = new AtomicBoolean();
    private final AtomicBoolean untrustedLogged = new AtomicBoolean();

    public ClientIpResolver(@Value("${app.client-ip.trusted-proxies}") List<String> trustedProxies,
                            @Value("${app.client-ip.max-proxy-hops}") int maxProxyHops) {
        if (maxProxyHops < 0) {
            throw new IllegalStateException("app.client-ip.max-proxy-hops must be 0 or more");
        }
        this.trusted = trustedProxies.stream().map(String::strip).filter(s -> !s.isEmpty())
                .map(IpAddressMatcher::new).toList();
        this.maxProxyHops = maxProxyHops;
    }

    public String resolve(HttpServletRequest request) {
        return resolve(request.getRemoteAddr(), request.getHeader("X-Forwarded-For"));
    }

    String resolve(String remoteAddr, String forwardedFor) {
        String socket = remoteAddr == null || remoteAddr.isBlank() ? "unknown" : remoteAddr;
        if (!isTrusted(socket)) {
            if (forwardedFor != null && !forwardedFor.isBlank() && untrustedLogged.compareAndSet(false, true)) {
                // Behind a real proxy this means the trusted list is wrong and every user would share
                // the proxy's address for the per-IP limits. No addresses are logged.
                log.warn("Client IP: requests carry X-Forwarded-For but don't come from a trusted proxy, so the"
                        + " header is ignored. Expected locally; on Render, check app.client-ip.trusted-proxies.");
            }
            return socket; // not via our proxy: the header can't be trusted at all
        }
        List<String> entries = parse(forwardedFor);
        if (entries.isEmpty()) {
            return socket;
        }
        int skipped = 0;
        for (int i = entries.size() - 1; i >= 0; i--) {
            String entry = entries.get(i);
            if (!isIpLiteral(entry)) {
                return socket; // malformed: don't guess
            }
            if (skipped < maxProxyHops && isTrusted(entry) && i > 0) {
                skipped++;
                continue;
            }
            logShapeOnce(entries.size(), skipped);
            return entry;
        }
        return socket;
    }

    /** Once per start, record how the header looked (counts only, never addresses) to help verify setup. */
    private void logShapeOnce(int entries, int skipped) {
        if (shapeLogged.compareAndSet(false, true)) {
            log.info("Client IP: first proxied request had {} X-Forwarded-For entries; skipped {} trusted proxy"
                    + " hop(s) (max {}). On Render expect 3 entries and 2 skipped.", entries, skipped, maxProxyHops);
        }
    }

    private static List<String> parse(String header) {
        if (header == null || header.isBlank()) {
            return List.of();
        }
        return new ArrayList<>(Arrays.stream(header.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList());
    }

    private boolean isTrusted(String address) {
        if (!isIpLiteral(address)) {
            return false;
        }
        for (IpAddressMatcher m : trusted) {
            try {
                if (m.matches(address)) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return false;
    }

    private static boolean isIpLiteral(String s) {
        if (IPV4.matcher(s).matches()) {
            // "999.1.1.1" isn't an address; Java would treat it as a hostname and do a DNS lookup.
            return Arrays.stream(s.split("\\.")).allMatch(part -> Integer.parseInt(part) <= 255);
        }
        return IPV6.matcher(s).matches(); // hostnames can't contain ':', so no DNS either
    }
}

package com.harshith.resumematcher.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The caller's IP address.
 *
 * <p>Behind a proxy this is still just {@code getRemoteAddr()}: Tomcat's RemoteIpValve
 * (server.forward-headers-strategy=native) replaces it with the address from X-Forwarded-For,
 * but only when the request comes from a proxy listed in server.tomcat.remoteip.internal-proxies.
 * Anyone else's X-Forwarded-For header is ignored, so clients can't fake their IP.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }
}

package com.campusclaw.agent.proxy;

import java.io.IOException;
import java.net.*;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Proxy configuration for HTTP/HTTPS connections.
 * Supports environment variables (HTTP_PROXY, HTTPS_PROXY, NO_PROXY)
 * and programmatic configuration.
 */
public class ProxyConfig {
    private static final Logger log = LoggerFactory.getLogger(ProxyConfig.class);

    public enum ProxyType { HTTP, SOCKS5, DIRECT }

    public record ProxyEntry(
        ProxyType type,
        String host,
        int port,
        String username,   // nullable
        String password    // nullable
    ) {
        /** Convert to java.net.Proxy. */
        public Proxy toJavaProxy() {
            return switch (type) {
                case HTTP -> new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
                case SOCKS5 -> new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(host, port));
                case DIRECT -> Proxy.NO_PROXY;
            };
        }

        /** Format as URL string. */
        public String toUrl() {
            String scheme = type == ProxyType.SOCKS5 ? "socks5" : "http";
            if (username != null) {
                return scheme + "://" + username + "@" + host + ":" + port;
            }
            return scheme + "://" + host + ":" + port;
        }
    }

    private ProxyEntry httpProxy;
    private ProxyEntry httpsProxy;
    private final List<String> noProxy = new ArrayList<>();

    /** Create config from a proxy URL string (e.g. from --proxy flag). */
    public static ProxyConfig fromUrl(String proxyUrl) {
        ProxyConfig config = new ProxyConfig();
        ProxyEntry entry = parseProxyUrl(proxyUrl);
        if (entry != null) {
            config.httpProxy = entry;
            config.httpsProxy = entry;
            log.info("Proxy configured from URL: {}", entry.toUrl());
        }
        return config;
    }

    /** Create config from environment variables, falling back to Windows registry. */
    public static ProxyConfig fromEnvironment() {
        ProxyConfig config = new ProxyConfig();
        // HTTP_PROXY / http_proxy
        String httpProxyUrl = coalesce(System.getenv("HTTP_PROXY"), System.getenv("http_proxy"));
        if (httpProxyUrl != null) {
            config.httpProxy = parseProxyUrl(httpProxyUrl);
        }
        // HTTPS_PROXY / https_proxy
        String httpsProxyUrl = coalesce(System.getenv("HTTPS_PROXY"), System.getenv("https_proxy"));
        if (httpsProxyUrl != null) {
            config.httpsProxy = parseProxyUrl(httpsProxyUrl);
        }
        // NO_PROXY / no_proxy
        String noProxy = coalesce(System.getenv("NO_PROXY"), System.getenv("no_proxy"));
        if (noProxy != null) {
            for (String entry : noProxy.split(",")) {
                String trimmed = entry.trim();
                if (!trimmed.isEmpty()) {
                    config.noProxy.add(trimmed.toLowerCase());
                }
            }
        }
        if (config.httpProxy != null || config.httpsProxy != null) {
            log.info("Proxy configured: HTTP={}, HTTPS={}, NO_PROXY={}",
                config.httpProxy != null ? config.httpProxy.toUrl() : "none",
                config.httpsProxy != null ? config.httpsProxy.toUrl() : "none",
                config.noProxy);
        }
        return config;
    }

    /** Get proxy for a given URL. Returns null if direct connection should be used. */
    public ProxyEntry getProxyFor(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host != null && shouldBypass(host)) {
                return null;
            }
            String scheme = uri.getScheme();
            if ("https".equalsIgnoreCase(scheme) && httpsProxy != null) {
                return httpsProxy;
            }
            if ("http".equalsIgnoreCase(scheme) && httpProxy != null) {
                return httpProxy;
            }
            // HTTPS falls back to HTTP proxy
            if ("https".equalsIgnoreCase(scheme) && httpProxy != null) {
                return httpProxy;
            }
        } catch (Exception e) {
            log.debug("Failed to parse URL for proxy resolution: {}", url, e);
        }
        return null;
    }

    /** Check if a host should bypass the proxy. */
    public boolean shouldBypass(String host) {
        String lowerHost = host.toLowerCase();
        for (String pattern : noProxy) {
            if (pattern.equals("*")) return true;
            if (pattern.startsWith(".") && lowerHost.endsWith(pattern)) return true;
            if (lowerHost.equals(pattern)) return true;
            if (lowerHost.endsWith("." + pattern)) return true;
        }
        return false;
    }

    /** Check if any proxy is configured. */
    public boolean isConfigured() {
        return httpProxy != null || httpsProxy != null;
    }

    public ProxyEntry getHttpProxy() { return httpProxy; }
    public ProxyEntry getHttpsProxy() { return httpsProxy; }
    public List<String> getNoProxy() { return Collections.unmodifiableList(noProxy); }

    public void setHttpProxy(ProxyEntry proxy) { this.httpProxy = proxy; }
    public void setHttpsProxy(ProxyEntry proxy) { this.httpsProxy = proxy; }

    /** Install as system-wide proxy selector. */
    public void installAsDefault() {
        ProxyConfig self = this;
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                ProxyEntry entry = self.getProxyFor(uri.toString());
                if (entry != null) {
                    return List.of(entry.toJavaProxy());
                }
                return List.of(Proxy.NO_PROXY);
            }
            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                log.warn("Proxy connection failed: {} via {}", uri, sa, ioe);
            }
        });
        // Install authenticator if needed
        if ((httpProxy != null && httpProxy.username() != null)
            || (httpsProxy != null && httpsProxy.username() != null)) {
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    ProxyEntry proxy = getRequestingProtocol().equalsIgnoreCase("https")
                        ? httpsProxy : httpProxy;
                    if (proxy != null && proxy.username() != null) {
                        return new PasswordAuthentication(proxy.username(),
                            proxy.password() != null ? proxy.password().toCharArray() : new char[0]);
                    }
                    return null;
                }
            });
        }
        log.info("Installed proxy selector for system-wide use");
    }

    /** Parse a proxy URL string like http://user:pass@host:port */
    static ProxyEntry parseProxyUrl(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            // Handle socks5:// prefix
            ProxyType type = ProxyType.HTTP;
            String parseUrl = url;
            if (url.startsWith("socks5://") || url.startsWith("socks://")) {
                type = ProxyType.SOCKS5;
                parseUrl = "http" + url.substring(url.indexOf("://"));
            }
            URI uri = URI.create(parseUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            if (host == null) return null;
            if (port < 0) port = 8080;
            String username = null;
            String password = null;
            String userInfo = uri.getUserInfo();
            if (userInfo != null) {
                int colon = userInfo.indexOf(':');
                if (colon >= 0) {
                    username = userInfo.substring(0, colon);
                    password = userInfo.substring(colon + 1);
                } else {
                    username = userInfo;
                }
            }
            return new ProxyEntry(type, host, port, username, password);
        } catch (Exception e) {
            log.warn("Failed to parse proxy URL: {}", url, e);
            return null;
        }
    }

    /**
     * Read Windows Internet Settings from registry to detect system proxy.
     * Reads HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings
     * for ProxyEnable and ProxyServer values.
     */
    static ProxyEntry detectWindowsRegistryProxy() {
        try {
            // Check if proxy is enabled
            String enableOutput = regQuery("ProxyEnable");
            if (enableOutput == null || !enableOutput.contains("0x1")) {
                return null;
            }
            // Read proxy server value (e.g. "127.0.0.1:7890" or "http=...:8080;https=...:8080")
            String serverOutput = regQuery("ProxyServer");
            if (serverOutput == null) {
                return null;
            }
            // Extract the value after REG_SZ
            String proxyServer = null;
            for (String line : serverOutput.split("\n")) {
                line = line.trim();
                if (line.contains("ProxyServer") && line.contains("REG_SZ")) {
                    int idx = line.indexOf("REG_SZ");
                    proxyServer = line.substring(idx + "REG_SZ".length()).trim();
                    break;
                }
            }
            if (proxyServer == null || proxyServer.isBlank()) {
                return null;
            }
            // Handle compound format: "http=host:port;https=host:port;..."
            if (proxyServer.contains("=")) {
                for (String part : proxyServer.split(";")) {
                    part = part.trim();
                    if (part.startsWith("https=") || part.startsWith("http=")) {
                        String addr = part.substring(part.indexOf('=') + 1);
                        return parseHostPort(addr);
                    }
                }
                return null;
            }
            // Simple format: "host:port"
            return parseHostPort(proxyServer);
        } catch (Exception e) {
            log.debug("Failed to read Windows registry proxy: {}", e.getMessage());
            return null;
        }
    }

    private static String regQuery(String valueName) {
        try {
            var process = new ProcessBuilder(
                    "reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                    "/v", valueName
            ).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            return exitCode == 0 ? output : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static ProxyEntry parseHostPort(String hostPort) {
        if (hostPort == null || hostPort.isBlank()) return null;
        // If it already looks like a URL, delegate to parseProxyUrl
        if (hostPort.contains("://")) return parseProxyUrl(hostPort);
        int colon = hostPort.lastIndexOf(':');
        if (colon > 0) {
            String host = hostPort.substring(0, colon);
            try {
                int port = Integer.parseInt(hostPort.substring(colon + 1));
                return new ProxyEntry(ProxyType.HTTP, host, port, null, null);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static String coalesce(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}

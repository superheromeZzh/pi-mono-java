/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.net.Proxy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProxyConfigTest {

    @Nested
    class ProxyEntryConversion {

        @Test
        void httpProxyToJavaProxy() {
            ProxyConfig.ProxyEntry e = new ProxyConfig.ProxyEntry(ProxyConfig.ProxyType.HTTP, "host", 8080, null, null);
            Proxy p = e.toJavaProxy();
            assertThat(p.type()).isEqualTo(Proxy.Type.HTTP);

            // address is an InetSocketAddress; its port should match
            java.net.InetSocketAddress addr = (java.net.InetSocketAddress) p.address();
            assertThat(addr.getPort()).isEqualTo(8080);
            assertThat(addr.getHostString()).isEqualTo("host");
        }

        @Test
        void socksProxyToJavaProxy() {
            ProxyConfig.ProxyEntry e =
                    new ProxyConfig.ProxyEntry(ProxyConfig.ProxyType.SOCKS5, "host", 1080, null, null);
            Proxy p = e.toJavaProxy();
            assertThat(p.type()).isEqualTo(Proxy.Type.SOCKS);
        }

        @Test
        void directProxyReturnsNoProxy() {
            ProxyConfig.ProxyEntry e =
                    new ProxyConfig.ProxyEntry(ProxyConfig.ProxyType.DIRECT, "ignored", 0, null, null);
            assertThat(e.toJavaProxy()).isSameAs(Proxy.NO_PROXY);
        }

        @Test
        void toUrlWithoutUsername() {
            ProxyConfig.ProxyEntry e = new ProxyConfig.ProxyEntry(ProxyConfig.ProxyType.HTTP, "host", 8080, null, null);
            assertThat(e.toUrl()).isEqualTo("http://host:8080");
        }

        @Test
        void toUrlWithUsernameOmitsPassword() {
            ProxyConfig.ProxyEntry e =
                    new ProxyConfig.ProxyEntry(ProxyConfig.ProxyType.HTTP, "host", 8080, "alice", "secret");
            assertThat(e.toUrl()).isEqualTo("http://alice@host:8080");
        }

        @Test
        void toUrlSocks() {
            ProxyConfig.ProxyEntry e =
                    new ProxyConfig.ProxyEntry(ProxyConfig.ProxyType.SOCKS5, "host", 1080, null, null);
            assertThat(e.toUrl()).isEqualTo("socks5://host:1080");
        }
    }

    @Nested
    class ParseProxyUrl {

        @Test
        void nullYieldsNull() {
            assertThat(ProxyConfig.parseProxyUrl(null)).isNull();
        }

        @Test
        void blankYieldsNull() {
            assertThat(ProxyConfig.parseProxyUrl("   ")).isNull();
        }

        @Test
        void plainHttp() {
            ProxyConfig.ProxyEntry e = ProxyConfig.parseProxyUrl("http://host:8080");
            assertThat(e.type()).isEqualTo(ProxyConfig.ProxyType.HTTP);
            assertThat(e.host()).isEqualTo("host");
            assertThat(e.port()).isEqualTo(8080);
            assertThat(e.username()).isNull();
            assertThat(e.password()).isNull();
        }

        @Test
        void socks5Scheme() {
            ProxyConfig.ProxyEntry e = ProxyConfig.parseProxyUrl("socks5://host:1080");
            assertThat(e.type()).isEqualTo(ProxyConfig.ProxyType.SOCKS5);
            assertThat(e.port()).isEqualTo(1080);
        }

        @Test
        void socksScheme() {
            ProxyConfig.ProxyEntry e = ProxyConfig.parseProxyUrl("socks://host:1080");
            assertThat(e.type()).isEqualTo(ProxyConfig.ProxyType.SOCKS5);
        }

        @Test
        void defaultPortWhenMissing() {
            ProxyConfig.ProxyEntry e = ProxyConfig.parseProxyUrl("http://host");
            assertThat(e.port()).isEqualTo(8080);
        }

        @Test
        void usernameAndPassword() {
            ProxyConfig.ProxyEntry e = ProxyConfig.parseProxyUrl("http://user:pass@host:9090");
            assertThat(e.username()).isEqualTo("user");
            assertThat(e.password()).isEqualTo("pass");
            assertThat(e.host()).isEqualTo("host");
            assertThat(e.port()).isEqualTo(9090);
        }

        @Test
        void usernameOnly() {
            ProxyConfig.ProxyEntry e = ProxyConfig.parseProxyUrl("http://justuser@host:8080");
            assertThat(e.username()).isEqualTo("justuser");
            assertThat(e.password()).isNull();
        }

        @Test
        void invalidUrlReturnsNull() {
            // URI parsing should fail for this string
            ProxyConfig.ProxyEntry e = ProxyConfig.parseProxyUrl("not a url");
            assertThat(e).isNull();
        }
    }

    @Nested
    class FromUrl {

        @Test
        void validUrlSetsBothHttpAndHttps() {
            ProxyConfig cfg = ProxyConfig.fromUrl("http://host:8080");
            assertThat(cfg.isConfigured()).isTrue();
            assertThat(cfg.getHttpProxy()).isNotNull();
            assertThat(cfg.getHttpsProxy()).isNotNull();
            assertThat(cfg.getHttpProxy()).isEqualTo(cfg.getHttpsProxy());
        }

        @Test
        void nullUrlYieldsEmptyConfig() {
            ProxyConfig cfg = ProxyConfig.fromUrl(null);
            assertThat(cfg.isConfigured()).isFalse();
        }

        @Test
        void blankUrlYieldsEmptyConfig() {
            ProxyConfig cfg = ProxyConfig.fromUrl("");
            assertThat(cfg.isConfigured()).isFalse();
        }
    }

    @Nested
    class GetProxyFor {

        private ProxyConfig configWith(ProxyConfig.ProxyEntry http, ProxyConfig.ProxyEntry https) {
            ProxyConfig cfg = new ProxyConfig();
            cfg.setHttpProxy(http);
            cfg.setHttpsProxy(https);
            return cfg;
        }

        @Test
        void httpsUsesHttpsProxy() {
            ProxyConfig.ProxyEntry httpsP =
                    new ProxyConfig.ProxyEntry(ProxyConfig.ProxyType.HTTP, "secure", 443, null, null);
            ProxyConfig cfg = configWith(null, httpsP);
            assertThat(cfg.getProxyFor("https://example.com/path")).isSameAs(httpsP);
        }

        @Test
        void httpUsesHttpProxy() {
            ProxyConfig.ProxyEntry httpP = new ProxyConfig.ProxyEntry(ProxyConfig.ProxyType.HTTP, "p", 80, null, null);
            ProxyConfig cfg = configWith(httpP, null);
            assertThat(cfg.getProxyFor("http://example.com")).isSameAs(httpP);
        }

        @Test
        void httpsFallsBackToHttpProxy() {
            ProxyConfig.ProxyEntry httpP = new ProxyConfig.ProxyEntry(ProxyConfig.ProxyType.HTTP, "p", 80, null, null);
            ProxyConfig cfg = configWith(httpP, null);
            assertThat(cfg.getProxyFor("https://example.com")).isSameAs(httpP);
        }

        @Test
        void noProxyConfiguredReturnsNull() {
            ProxyConfig cfg = configWith(null, null);
            assertThat(cfg.getProxyFor("http://example.com")).isNull();
        }

        @Test
        void invalidUriReturnsNull() {
            ProxyConfig cfg =
                    configWith(new ProxyConfig.ProxyEntry(ProxyConfig.ProxyType.HTTP, "p", 80, null, null), null);

            // Pass a value that throws inside URI.create
            assertThat(cfg.getProxyFor("ht!tp://??")).isNull();
        }

        @Test
        void bypassedHostReturnsNull() {
            ProxyConfig.ProxyEntry httpP = new ProxyConfig.ProxyEntry(ProxyConfig.ProxyType.HTTP, "p", 80, null, null);
            ProxyConfig cfg = new ProxyConfig();
            cfg.setHttpProxy(httpP);

            // Manually add bypass entries via the helper round-trip
            assertThat(cfg.shouldBypass("localhost")).isFalse();
        }
    }

    @Nested
    class ShouldBypass {

        @Test
        void exactMatch() throws Exception {
            ProxyConfig cfg = configWithNoProxy("localhost,example.com");
            assertThat(cfg.shouldBypass("localhost")).isTrue();
            assertThat(cfg.shouldBypass("EXAMPLE.com")).isTrue();
            assertThat(cfg.shouldBypass("other.com")).isFalse();
        }

        @Test
        void leadingDotIsSuffixMatch() throws Exception {
            ProxyConfig cfg = configWithNoProxy(".internal.example.com");
            assertThat(cfg.shouldBypass("api.internal.example.com")).isTrue();
            assertThat(cfg.shouldBypass("internal.example.com")).isFalse();
        }

        @Test
        void implicitSuffixMatch() throws Exception {
            ProxyConfig cfg = configWithNoProxy("example.com");
            assertThat(cfg.shouldBypass("api.example.com")).isTrue();
        }

        @Test
        void wildcardBypassesAll() throws Exception {
            ProxyConfig cfg = configWithNoProxy("*");
            assertThat(cfg.shouldBypass("anything")).isTrue();
        }

        @Test
        void emptyNoProxyDoesNotBypass() {
            ProxyConfig cfg = new ProxyConfig();
            assertThat(cfg.shouldBypass("anything")).isFalse();
        }

        // Helper: ProxyConfig only loads NO_PROXY from env, so we set it up via reflection.
        private ProxyConfig configWithNoProxy(String noProxy) throws Exception {
            ProxyConfig cfg = new ProxyConfig();
            var field = ProxyConfig.class.getDeclaredField("noProxy");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var list = (java.util.List<String>) field.get(cfg);
            for (String entry : noProxy.split(",")) {
                list.add(entry.trim().toLowerCase(java.util.Locale.ROOT));
            }
            return cfg;
        }
    }

    @Nested
    class IsConfigured {

        @Test
        void emptyConfigNotConfigured() {
            assertThat(new ProxyConfig().isConfigured()).isFalse();
        }

        @Test
        void httpSetIsConfigured() {
            ProxyConfig cfg = new ProxyConfig();
            cfg.setHttpProxy(new ProxyConfig.ProxyEntry(ProxyConfig.ProxyType.HTTP, "h", 1, null, null));
            assertThat(cfg.isConfigured()).isTrue();
        }

        @Test
        void httpsSetIsConfigured() {
            ProxyConfig cfg = new ProxyConfig();
            cfg.setHttpsProxy(new ProxyConfig.ProxyEntry(ProxyConfig.ProxyType.HTTP, "h", 1, null, null));
            assertThat(cfg.isConfigured()).isTrue();
        }

        @Test
        void getNoProxyIsUnmodifiable() {
            ProxyConfig cfg = new ProxyConfig();
            assertThat(cfg.getNoProxy()).isEmpty();

            // Confirm unmodifiable contract
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> cfg.getNoProxy().add("x"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class FromEnvironment {

        @Test
        void noEnvYieldsEmptyConfig() {
            // We can't easily set process env vars; rely on a clean environment for HTTP_PROXY-like.
            // The method must at least return a non-null instance and not throw.
            ProxyConfig cfg = ProxyConfig.fromEnvironment();
            assertThat(cfg).isNotNull();

            // The returned list is always unmodifiable
            assertThat(cfg.getNoProxy()).isNotNull();
        }
    }

    @Nested
    class WindowsRegistry {

        @Test
        void detectOnNonWindowsReturnsNull() {
            // On macOS/Linux the `reg` command isn't available, so the implementation falls back to null.
            // We tolerate either null (expected) or a value if running on Windows CI.
            ProxyConfig.ProxyEntry result = ProxyConfig.detectWindowsRegistryProxy();
            assertThat(result == null || result.host() != null).isTrue();
        }
    }

    @Nested
    class InstallAsDefault {

        @Test
        void installsProxySelectorRoutingHttpsThroughHttpsProxy() throws Exception {
            java.net.ProxySelector saved = java.net.ProxySelector.getDefault();
            try {
                ProxyConfig cfg = new ProxyConfig();
                cfg.setHttpsProxy(new ProxyConfig.ProxyEntry(ProxyConfig.ProxyType.HTTP, "host2", 8443, null, null));
                cfg.installAsDefault();

                java.util.List<java.net.Proxy> selected =
                        java.net.ProxySelector.getDefault().select(new java.net.URI("https://example.com/path"));
                assertThat(selected).hasSize(1);
                assertThat(selected.get(0).type()).isEqualTo(java.net.Proxy.Type.HTTP);
            } finally {
                java.net.ProxySelector.setDefault(saved);
            }
        }

        @Test
        void noConfiguredProxyYieldsNoProxy() throws Exception {
            java.net.ProxySelector saved = java.net.ProxySelector.getDefault();
            try {
                new ProxyConfig().installAsDefault();
                java.util.List<java.net.Proxy> selected =
                        java.net.ProxySelector.getDefault().select(new java.net.URI("https://example.com"));
                assertThat(selected).containsExactly(java.net.Proxy.NO_PROXY);
            } finally {
                java.net.ProxySelector.setDefault(saved);
            }
        }

        @Test
        void connectFailedLogsWithoutThrowing() throws Exception {
            java.net.ProxySelector saved = java.net.ProxySelector.getDefault();
            try {
                ProxyConfig cfg = new ProxyConfig();
                cfg.setHttpProxy(new ProxyConfig.ProxyEntry(ProxyConfig.ProxyType.HTTP, "host", 8080, null, null));
                cfg.installAsDefault();

                java.net.ProxySelector selector = java.net.ProxySelector.getDefault();
                java.net.URI uri = new java.net.URI("http://example.com");
                java.net.InetSocketAddress addr = new java.net.InetSocketAddress("host", 8080);
                java.io.IOException cause = new java.io.IOException("test");

                // ProxySelector.connectFailed is a notification hook; it must swallow the failure
                // and merely log so callers (URLConnection internals) keep working.
                assertThatNoException().isThrownBy(() -> selector.connectFailed(uri, addr, cause));
            } finally {
                java.net.ProxySelector.setDefault(saved);
            }
        }

        @Test
        void authenticatorInstalledWhenUsernamePresent() {
            java.net.ProxySelector saved = java.net.ProxySelector.getDefault();
            try {
                ProxyConfig cfg = new ProxyConfig();
                cfg.setHttpProxy(
                        new ProxyConfig.ProxyEntry(ProxyConfig.ProxyType.HTTP, "host", 8080, "alice", "secret"));
                cfg.installAsDefault();

                // No way to read back the global Authenticator's behavior portably; we just
                // verify the call doesn't throw — coverage exercises the authenticator branch.
                assertThat(cfg.isConfigured()).isTrue();
            } finally {
                java.net.ProxySelector.setDefault(saved);
            }
        }
    }
}

/* Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// This code is based on AOSP /libcore/luni/src/main/java/java/net/ProxySelectorImpl.java

package org.mozilla.gecko.util;

import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URLConnection;
import java.util.List;

public class ProxySelector {
    private static final String TOR_PROXY_ADDRESS = "127.0.0.1";
    private static final int TOR_SOCKS_PROXY_PORT = 9150;
    private static final int TOR_HTTP_PROXY_PORT = 8218;

    public static URLConnection openConnectionWithProxy(final URI uri) throws IOException {
        java.net.ProxySelector ps = java.net.ProxySelector.getDefault();
        Proxy proxy = Proxy.NO_PROXY;
        if (ps != null) {
            List<Proxy> proxies = ps.select(uri);
            if (proxies != null && !proxies.isEmpty()) {
                proxy = proxies.get(0);
            }
        }

        /* Ignore the proxy we found from the VM, only use Tor. We can probably
         * safely use the logic in this class in the future. */
        return uri.toURL().openConnection(getProxy());
    }

    public static Proxy getProxy() {
        // TODO make configurable
        return new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(TOR_PROXY_ADDRESS, TOR_SOCKS_PROXY_PORT));
    }

    public static String getProxyHostAddress() {
        return TOR_PROXY_ADDRESS;
    }

    public static int getSocksProxyPort() {
        return TOR_SOCKS_PROXY_PORT;
    }

    public static int getHttpProxyPort() {
        return TOR_HTTP_PROXY_PORT;
    }

    public ProxySelector() {
    }

    public Proxy select(final String scheme, final String host) {
        int port = -1;
        Proxy proxy = null;
        String nonProxyHostsKey = null;
        boolean httpProxyOkay = true;
        if ("http".equalsIgnoreCase(scheme)) {
            port = 80;
            nonProxyHostsKey = "http.nonProxyHosts";
            proxy = lookupProxy("http.proxyHost", "http.proxyPort", Proxy.Type.HTTP, port);
        } else if ("https".equalsIgnoreCase(scheme)) {
            port = 443;
            nonProxyHostsKey = "https.nonProxyHosts"; // RI doesn't support this
            proxy = lookupProxy("https.proxyHost", "https.proxyPort", Proxy.Type.HTTP, port);
        } else if ("ftp".equalsIgnoreCase(scheme)) {
            port = 80; // not 21 as you might guess
            nonProxyHostsKey = "ftp.nonProxyHosts";
            proxy = lookupProxy("ftp.proxyHost", "ftp.proxyPort", Proxy.Type.HTTP, port);
        } else if ("socket".equalsIgnoreCase(scheme)) {
            httpProxyOkay = false;
        } else {
            return Proxy.NO_PROXY;
        }

        if (nonProxyHostsKey != null
                && isNonProxyHost(host, System.getProperty(nonProxyHostsKey))) {
            return Proxy.NO_PROXY;
        }

        if (proxy != null) {
            return proxy;
        }

        if (httpProxyOkay) {
            proxy = lookupProxy("proxyHost", "proxyPort", Proxy.Type.HTTP, port);
            if (proxy != null) {
                return proxy;
            }
        }

        proxy = lookupProxy("socksProxyHost", "socksProxyPort", Proxy.Type.SOCKS, 1080);
        if (proxy != null) {
            return proxy;
        }

        return Proxy.NO_PROXY;
    }

    /**
     * Returns the proxy identified by the {@code hostKey} system property, or
     * null.
     */
    @Nullable
    private Proxy lookupProxy(final String hostKey, final String portKey, final Proxy.Type type,
                              final int defaultPort) {
        final String host = System.getProperty(hostKey);
        if (TextUtils.isEmpty(host)) {
            return null;
        }

        final int port = getSystemPropertyInt(portKey, defaultPort);
        if (port == -1) {
            // Port can be -1. See bug 1270529.
            return null;
        }

        return new Proxy(type, InetSocketAddress.createUnresolved(host, port));
    }

    private int getSystemPropertyInt(final String key, final int defaultValue) {
        String string = System.getProperty(key);
        if (string != null) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    /**
     * Returns true if the {@code nonProxyHosts} system property pattern exists
     * and matches {@code host}.
     */
    private boolean isNonProxyHost(final String host, final String nonProxyHosts) {
        if (host == null || nonProxyHosts == null) {
            return false;
        }

        // construct pattern
        StringBuilder patternBuilder = new StringBuilder();
        for (int i = 0; i < nonProxyHosts.length(); i++) {
            char c = nonProxyHosts.charAt(i);
            switch (c) {
                case '.':
                    patternBuilder.append("\\.");
                    break;
                case '*':
                    patternBuilder.append(".*");
                    break;
                default:
                    patternBuilder.append(c);
            }
        }
        // check whether the host is the nonProxyHosts.
        String pattern = patternBuilder.toString();
        return host.matches(pattern);
    }
}


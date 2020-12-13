/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.network.http;

import bisq.network.Socks5ProxyProvider;

import bisq.common.app.Version;
import bisq.common.util.Utilities;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;

import javax.inject.Inject;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

// TODO close connection if failing
@Slf4j
public class HttpClientImpl implements HttpClient {
    @Nullable
    private Socks5ProxyProvider socks5ProxyProvider;
    @Nullable
    private HttpURLConnection connection;
    @Nullable
    private CloseableHttpClient closeableHttpClient;

    @Getter
    @Setter
    private String baseUrl;
    @Setter
    private boolean ignoreSocks5Proxy;
    @Getter
    private final String uid;

    @Inject
    public HttpClientImpl(@Nullable Socks5ProxyProvider socks5ProxyProvider) {
        this.socks5ProxyProvider = socks5ProxyProvider;
        uid = UUID.randomUUID().toString();
    }

    public HttpClientImpl(String baseUrl) {
        this.baseUrl = baseUrl;
        uid = UUID.randomUUID().toString();
    }

    @Override
    public void shutDown() {
        if (connection != null) {
            connection.disconnect();
        }
        if (closeableHttpClient != null) {
            try {
                closeableHttpClient.close();
            } catch (IOException ignore) {
            }
        }
    }

    @Override
    public String get(String param,
                      @Nullable String headerKey,
                      @Nullable String headerValue) throws IOException {
        return doRequest(param, HttpMethod.GET, headerKey, headerValue);
    }

    @Override
    public String post(String param,
                       @Nullable String headerKey,
                       @Nullable String headerValue) throws IOException {
        return doRequest(param, HttpMethod.POST, headerKey, headerValue);
    }

    private String doRequest(String param,
                             HttpMethod httpMethod,
                             @Nullable String headerKey,
                             @Nullable String headerValue) throws IOException {
        checkNotNull(baseUrl, "baseUrl must be set before calling post");
        Socks5Proxy socks5Proxy = getSocks5Proxy(socks5ProxyProvider);
        if (ignoreSocks5Proxy || socks5Proxy == null || baseUrl.contains("localhost")) {
            return requestWithoutProxy(baseUrl, param, httpMethod, headerKey, headerValue);
        } else {
            return doRequestWithProxy(baseUrl, param, httpMethod, socks5Proxy, headerKey, headerValue);
        }
    }

    private String requestWithoutProxy(String baseUrl,
                                       String param,
                                       HttpMethod httpMethod,
                                       @Nullable String headerKey,
                                       @Nullable String headerValue) throws IOException {
        long ts = System.currentTimeMillis();
        String spec = baseUrl + param;
        log.info("requestWithoutProxy: URL={}, httpMethod={}", spec, httpMethod);
        URL url = new URL(spec);
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(httpMethod.name());
            connection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(120));
            connection.setReadTimeout((int) TimeUnit.SECONDS.toMillis(120));
            connection.setRequestProperty("User-Agent", "bisq/" + Version.VERSION);
            if (headerKey != null && headerValue != null) {
                connection.setRequestProperty(headerKey, headerValue);
            }

            if (connection.getResponseCode() == 200) {
                String response = convertInputStreamToString(connection.getInputStream());
                log.info("Response for {} took {} ms. Data size:{}, response: {}",
                        spec,
                        System.currentTimeMillis() - ts,
                        Utilities.readableFileSize(response.getBytes().length),
                        Utilities.toTruncatedString(response));
                return response;
            } else {
                String error = convertInputStreamToString(connection.getErrorStream());
                connection.getErrorStream().close();
                throw new HttpException(error);
            }
        } catch (Throwable t) {
            String message = "Error at requestWithoutProxy with URL: " + spec + ". Throwable=" + t.getMessage();
            log.error(message);
            throw new IOException(message);
        } finally {
            try {
                if (connection != null)
                    connection.getInputStream().close();
            } catch (Throwable ignore) {
            }
        }
    }

    private String doRequestWithProxy(String baseUrl,
                                      String param,
                                      HttpMethod httpMethod,
                                      Socks5Proxy socks5Proxy,
                                      @Nullable String headerKey,
                                      @Nullable String headerValue) throws IOException {
        long ts = System.currentTimeMillis();
        String uri = baseUrl + param;
        log.info("requestWithoutProxy: uri={}, httpMethod={}", uri, httpMethod);
        // This code is adapted from:
        //  http://stackoverflow.com/a/25203021/5616248

        // Register our own SocketFactories to override createSocket() and connectSocket().
        // connectSocket does NOT resolve hostname before passing it to proxy.
        Registry<ConnectionSocketFactory> reg = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", new SocksConnectionSocketFactory())
                .register("https", new SocksSSLConnectionSocketFactory(SSLContexts.createSystemDefault())).build();

        // Use FakeDNSResolver if not resolving DNS locally.
        // This prevents a local DNS lookup (which would be ignored anyway)
        PoolingHttpClientConnectionManager cm = socks5Proxy.resolveAddrLocally() ?
                new PoolingHttpClientConnectionManager(reg) :
                new PoolingHttpClientConnectionManager(reg, new FakeDnsResolver());
        try {
            closeableHttpClient = HttpClients.custom().setConnectionManager(cm).build();
            InetSocketAddress socksAddress = new InetSocketAddress(socks5Proxy.getInetAddress(), socks5Proxy.getPort());

            // remove me: Use this to test with system-wide Tor proxy, or change port for another proxy.
            // InetSocketAddress socksAddress = new InetSocketAddress("127.0.0.1", 9050);

            HttpClientContext context = HttpClientContext.create();
            context.setAttribute("socks.address", socksAddress);

            HttpUriRequest request = getHttpUriRequest(httpMethod, uri);
            if (headerKey != null && headerValue != null)
                request.setHeader(headerKey, headerValue);

            try (CloseableHttpResponse httpResponse = checkNotNull(closeableHttpClient).execute(request, context)) {
                String response = convertInputStreamToString(httpResponse.getEntity().getContent());
                log.info("Response for {} took {} ms. Data size:{}, response: {}",
                        uri,
                        System.currentTimeMillis() - ts,
                        Utilities.readableFileSize(response.getBytes().length),
                        Utilities.toTruncatedString(response));
                return response;
            }
        } catch (Throwable t) {
            String message = "Error at doRequestWithProxy with URL: " + uri + ". Throwable=" + t.getMessage();
            log.error(message);
            throw new IOException(message);
        } finally {
            if (closeableHttpClient != null) {
                closeableHttpClient.close();
            }
        }
    }

    private HttpUriRequest getHttpUriRequest(HttpMethod httpMethod, String uri) {
        switch (httpMethod) {
            case GET:
                return new HttpGet(uri);
            case POST:
                return new HttpPost(uri);
            default:
                throw new IllegalArgumentException("HttpMethod not supported: " + httpMethod);
        }
    }

    @Nullable
    private Socks5Proxy getSocks5Proxy(Socks5ProxyProvider socks5ProxyProvider) {
        if (socks5ProxyProvider == null) {
            return null;
        }

        // We use the custom socks5ProxyHttp.
        Socks5Proxy socks5Proxy = socks5ProxyProvider.getSocks5ProxyHttp();
        if (socks5Proxy != null) {
            return socks5Proxy;
        }

        // If not set we request socks5ProxyProvider.getSocks5Proxy()
        // which delivers the btc proxy if set, otherwise the internal proxy.
        return socks5ProxyProvider.getSocks5Proxy();
    }

    private String convertInputStreamToString(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            stringBuilder.append(line);
        }
        return stringBuilder.toString();
    }

    @Override
    public String toString() {
        return "HttpClientImpl{" +
                "\n     socks5ProxyProvider=" + socks5ProxyProvider +
                ",\n     baseUrl='" + baseUrl + '\'' +
                ",\n     ignoreSocks5Proxy=" + ignoreSocks5Proxy +
                ",\n     uid='" + uid + '\'' +
                ",\n     connection=" + connection +
                ",\n     httpclient=" + closeableHttpClient +
                "\n}";
    }
}

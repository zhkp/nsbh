package com.kp.nsbh.tools;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kp.nsbh.config.NsbhProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.ProxySelector;
import java.net.CookieHandler;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.Certificate;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class HttpGetToolTest {

    private HttpGetTool tool() {
        return new HttpGetTool(new ObjectMapper(), new NsbhProperties());
    }

    @Test
    void shouldRejectNonHttpScheme() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool().execute("{\"url\":\"ftp://example.com/file\"}").block());
        assertTrue(ex.getMessage().contains("http/https"));
    }

    @Test
    void shouldRejectLocalhost() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool().execute("{\"url\":\"http://localhost:8080\"}").block());
        assertTrue(ex.getMessage().contains("Private"));
    }

    @Test
    void shouldRejectLoopbackIpv4() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool().execute("{\"url\":\"http://127.0.0.1:8080\"}").block());
        assertTrue(ex.getMessage().contains("Private IP"));
    }

    @Test
    void shouldRejectLoopbackIpv6() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool().execute("{\"url\":\"http://[::1]:8080\"}").block());
        assertTrue(ex.getMessage().contains("Private IP"));
    }

    @Test
    void shouldRejectMissingUrl() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool().execute("{}").block());
        assertTrue(ex.getMessage().contains("url is required"));
    }

    @Test
    void shouldRejectInvalidJsonInput() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool().execute("{").block());
        assertTrue(ex.getMessage().contains("Invalid JSON input"));
    }

    @Test
    void shouldRejectMalformedUrl() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool().execute("{\"url\":\"http://exa mple.com\"}").block());
        assertTrue(ex.getMessage().contains("Invalid URL"));
    }

    @Test
    void shouldRejectPrivateIpv4Range() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool().execute("{\"url\":\"http://10.0.0.1\"}").block());
        assertTrue(ex.getMessage().contains("Private IP"));
    }

    @Test
    void shouldRejectCgnatIpv4Range() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool().execute("{\"url\":\"http://100.64.0.1\"}").block());
        assertTrue(ex.getMessage().contains("Private IP"));
    }

    @Test
    void isPrivateAddressShouldCoverIpv4AndIpv6Branches() throws Exception {
        HttpGetTool tool = tool();
        Method method = HttpGetTool.class.getDeclaredMethod("isPrivateAddress", InetAddress.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(tool, InetAddress.getByName("0.0.0.0")));
        assertTrue((Boolean) method.invoke(tool, InetAddress.getByName("10.0.0.1")));
        assertTrue((Boolean) method.invoke(tool, InetAddress.getByName("100.64.0.1")));
        assertTrue((Boolean) method.invoke(tool, InetAddress.getByName("198.18.0.1")));
        assertTrue((Boolean) method.invoke(tool, InetAddress.getByName("192.0.2.1")));
        assertTrue((Boolean) method.invoke(tool, InetAddress.getByName("224.0.0.1")));
        assertTrue((Boolean) method.invoke(tool, InetAddress.getByName("fc00::1")));
        assertTrue((Boolean) method.invoke(tool, InetAddress.getByName("::1")));
        assertTrue((Boolean) method.invoke(tool, InetAddress.getByName("127.0.0.1")));
    }

    @Test
    void readLimitedUtf8ShouldRejectOversizedResponse() throws Exception {
        HttpGetTool tool = tool();
        Method method = HttpGetTool.class.getDeclaredMethod("readLimitedUtf8", java.io.InputStream.class, int.class);
        method.setAccessible(true);

        byte[] bytes = "0123456789".getBytes(StandardCharsets.UTF_8);
        Exception ex = assertThrows(Exception.class, () -> method.invoke(tool, new ByteArrayInputStream(bytes), 5));
        assertTrue(ex.getCause() instanceof IllegalArgumentException);
        assertTrue(ex.getCause().getMessage().contains("HTTP response too large"));
    }

    @Test
    void validateUriShouldRejectMissingHost() throws Exception {
        HttpGetTool tool = tool();
        Method method = HttpGetTool.class.getDeclaredMethod("validateUri", URI.class);
        method.setAccessible(true);

        Exception ex = assertThrows(Exception.class, () -> method.invoke(tool, new URI("https:///path-only")));
        assertTrue(ex.getCause() instanceof IllegalArgumentException);
        assertTrue(ex.getCause().getMessage().contains("URL host is required"));
    }

    @Test
    void privateHelpersShouldCoverRemainingBranches() throws Exception {
        HttpGetTool tool = tool();

        Method isPrivateAddress = HttpGetTool.class.getDeclaredMethod("isPrivateAddress", InetAddress.class);
        isPrivateAddress.setAccessible(true);
        assertTrue(!(Boolean) isPrivateAddress.invoke(tool, InetAddress.getByName("8.8.8.8")));
        assertTrue(!(Boolean) isPrivateAddress.invoke(tool, InetAddress.getByName("2001:4860:4860::8888")));

        Method isRedirect = HttpGetTool.class.getDeclaredMethod("isRedirect", int.class);
        isRedirect.setAccessible(true);
        assertTrue((Boolean) isRedirect.invoke(tool, 302));
        assertTrue(!(Boolean) isRedirect.invoke(tool, 200));

        Method readLimitedUtf8 = HttpGetTool.class.getDeclaredMethod("readLimitedUtf8", java.io.InputStream.class, int.class);
        readLimitedUtf8.setAccessible(true);
        String body = (String) readLimitedUtf8.invoke(tool,
                new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8)), 10);
        assertTrue(body.contains("ok"));

        Method validateResolvedAddresses = HttpGetTool.class.getDeclaredMethod("validateResolvedAddresses", String.class);
        validateResolvedAddresses.setAccessible(true);
        Exception ex = assertThrows(Exception.class, () -> validateResolvedAddresses.invoke(tool, "invalid.invalid.invalid"));
        assertTrue(ex.getCause() instanceof IllegalStateException);
    }

    @Test
    void executeShouldReturnJsonOnSuccess() throws Exception {
        HttpGetTool tool = tool();
        injectHttpClient(tool, new StubHttpClient(req -> new StubHttpResponse(
                200,
                req.uri(),
                new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8))
        )));

        String out = tool.execute("{\"url\":\"http://1.1.1.1\"}").block();
        assertTrue(out.contains("\"status\":200"));
        assertTrue(out.contains("\"url\":\"http://1.1.1.1\""));
        assertTrue(out.contains("\"body\":\"ok\""));
    }

    @Test
    void executeShouldRejectRedirectStatus() throws Exception {
        HttpGetTool tool = tool();
        injectHttpClient(tool, new StubHttpClient(req -> new StubHttpResponse(
                302,
                req.uri(),
                new ByteArrayInputStream("moved".getBytes(StandardCharsets.UTF_8))
        )));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute("{\"url\":\"http://1.1.1.1\"}").block());
        assertTrue(ex.getMessage().contains("Redirect response"));
    }

    @Test
    void executeShouldWrapIoException() throws Exception {
        HttpGetTool tool = tool();
        injectHttpClient(tool, new StubHttpClient(req -> {
            throw new IOException("io-fail");
        }));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> tool.execute("{\"url\":\"http://1.1.1.1\"}").block());
        assertTrue(ex.getMessage().contains("HTTP request failed"));
    }

    @Test
    void executeShouldWrapInterruptedException() throws Exception {
        HttpGetTool tool = tool();
        injectHttpClient(tool, new StubHttpClient(req -> {
            throw new InterruptedException("stop");
        }));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> tool.execute("{\"url\":\"http://1.1.1.1\"}").block());
        assertTrue(ex.getMessage().contains("HTTP request interrupted"));
        Thread.interrupted();
    }

    @Test
    void executeShouldRejectLargeBody() throws Exception {
        NsbhProperties properties = new NsbhProperties();
        properties.getTools().setMaxOutputBytes(3);
        HttpGetTool tool = new HttpGetTool(new ObjectMapper(), properties);
        injectHttpClient(tool, new StubHttpClient(req -> new StubHttpResponse(
                200,
                req.uri(),
                new ByteArrayInputStream("tool-body-large".getBytes(StandardCharsets.UTF_8))
        )));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute("{\"url\":\"http://1.1.1.1\"}").block());
        assertTrue(ex.getMessage().contains("too large"));
    }

    private void injectHttpClient(HttpGetTool tool, HttpClient client) throws Exception {
        Field field = HttpGetTool.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        field.set(tool, client);
    }

    @FunctionalInterface
    interface SendHandler {
        HttpResponse<InputStream> send(HttpRequest request) throws IOException, InterruptedException;
    }

    static class StubHttpClient extends HttpClient {
        private final SendHandler handler;

        StubHttpClient(SendHandler handler) {
            this.handler = handler;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(1));
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            @SuppressWarnings("unchecked")
            HttpResponse<T> response = (HttpResponse<T>) handler.send(request);
            return response;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }

    static class StubHttpResponse implements HttpResponse<InputStream> {
        private final int status;
        private final URI uri;
        private final InputStream body;

        StubHttpResponse(int status, URI uri, InputStream body) {
            this.status = status;
            this.uri = uri;
            this.body = body;
        }

        @Override
        public int statusCode() {
            return status;
        }

        @Override
        public HttpRequest request() {
            return HttpRequest.newBuilder(uri).build();
        }

        @Override
        public Optional<HttpResponse<InputStream>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (a, b) -> true);
        }

        @Override
        public InputStream body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return uri;
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}

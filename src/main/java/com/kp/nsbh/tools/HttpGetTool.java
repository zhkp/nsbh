package com.kp.nsbh.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kp.nsbh.config.NsbhProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@NsbhTool(
        name = "http_get",
        description = "Fetches an HTTP/HTTPS URL with SSRF checks and response size limit",
        schema = "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}},\"required\":[\"url\"]}",
        requiredPermissions = {"NET_HTTP"}
)
public class HttpGetTool implements Tool {
    private final ObjectMapper objectMapper;
    private final NsbhProperties properties;
    private final HttpClient httpClient;

    public HttpGetTool(ObjectMapper objectMapper, NsbhProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1, properties.getTools().getTimeoutMs())))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public Mono<String> execute(String inputJson) {
        return Mono.fromCallable(() -> executeBlocking(inputJson))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String executeBlocking(String inputJson) {
        String url = extractUrl(inputJson);
        URI uri = parseUri(url);
        validateUri(uri);
        validateResolvedAddresses(uri.getHost());

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(Math.max(1, properties.getTools().getTimeoutMs())))
                .header("Accept", "text/plain, application/json, */*")
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (isRedirect(response.statusCode())) {
                throw new IllegalArgumentException("Redirect response is not allowed");
            }
            String body = readLimitedUtf8(response.body(), properties.getTools().getMaxOutputBytes());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", response.statusCode());
            result.put("url", uri.toString());
            result.put("body", body);
            return objectMapper.writeValueAsString(result);
        } catch (IOException e) {
            throw new IllegalStateException("HTTP request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP request interrupted", e);
        }
    }

    private String extractUrl(String inputJson) {
        try {
            JsonNode root = objectMapper.readTree(inputJson == null ? "{}" : inputJson);
            JsonNode node = root.get("url");
            if (node == null || node.asText().isBlank()) {
                throw new IllegalArgumentException("url is required");
            }
            return node.asText().trim();
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid JSON input", e);
        }
    }

    private URI parseUri(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL", e);
        }
    }

    private void validateUri(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Only http/https URLs are allowed");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("URL host is required");
        }
    }

    private void validateResolvedAddresses(String host) {
        if ("localhost".equalsIgnoreCase(host)) {
            throw new IllegalArgumentException("Private host is not allowed");
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (isPrivateAddress(address)) {
                    throw new IllegalArgumentException("Private IP is not allowed: " + address.getHostAddress());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to resolve host", e);
        }
    }

    private boolean isPrivateAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet4Address ipv4) {
            byte[] b = ipv4.getAddress();
            int first = b[0] & 0xFF;
            int second = b[1] & 0xFF;
            if (first == 100 && second >= 64 && second <= 127) {
                return true;
            }
            if (first == 198 && (second == 18 || second == 19)) {
                return true;
            }
            if (first == 192 && second == 0) {
                return true;
            }
            if (first >= 224) {
                return true;
            }
            return first == 0;
        }
        if (address instanceof Inet6Address ipv6) {
            byte[] b = ipv6.getAddress();
            return (b[0] & 0xFE) == 0xFC;
        }
        return false;
    }

    private String readLimitedUtf8(InputStream inputStream, int maxBytes) throws IOException {
        int limit = Math.max(1, maxBytes);
        try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    throw new IllegalArgumentException("HTTP response too large");
                }
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private boolean isRedirect(int statusCode) {
        return statusCode >= 300 && statusCode < 400;
    }
}

package com.kp.nsbh.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kp.nsbh.config.NsbhProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class TavilySearchToolTest {

    private TavilySearchTool tool(ExchangeFunction exchange, String apiKey) {
        NsbhProperties props = new NsbhProperties();
        props.getTools().getWebSearch().setApiKey(apiKey);
        props.getTools().getWebSearch().setMaxResults(3);
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchange);
        return new TavilySearchTool(builder, props, new ObjectMapper());
    }

    private ExchangeFunction jsonResponse(String body) {
        return req -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    @Test
    void returnsSearchResults() {
        String tavilyResponse = """
                {"results":[
                  {"title":"Example","url":"https://example.com","content":"some snippet"}
                ]}
                """;
        String result = tool(jsonResponse(tavilyResponse), "sk-test")
                .execute("{\"query\":\"spring boot\"}").block();
        assertTrue(result.contains("Example"));
        assertTrue(result.contains("example.com"));
    }

    @Test
    void blankApiKeyFailsAtExecute() {
        TavilySearchTool t = tool(req -> Mono.empty(), "");
        assertThrows(IllegalArgumentException.class,
                () -> t.execute("{\"query\":\"test\"}").block());
    }

    @Test
    void missingQueryFailsAtExecute() {
        TavilySearchTool t = tool(req -> Mono.empty(), "sk-test");
        assertThrows(IllegalArgumentException.class,
                () -> t.execute("{}").block());
    }

    @Test
    void explicitMaxResultsOverridesDefault() {
        String tavilyResponse = """
                {"results":[
                  {"title":"T","url":"https://t.com","content":"s"}
                ]}
                """;
        String result = tool(jsonResponse(tavilyResponse), "sk-test")
                .execute("{\"query\":\"test\",\"max_results\":1}").block();
        assertTrue(result.contains("T"));
    }

    @Test
    void apiErrorResponseThrowsIllegalState() {
        ExchangeFunction errorResponse = req -> Mono.just(
                org.springframework.web.reactive.function.client.ClientResponse
                        .create(org.springframework.http.HttpStatus.UNAUTHORIZED)
                        .header(org.springframework.http.HttpHeaders.CONTENT_TYPE,
                                org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"error\":\"Unauthorized\"}")
                        .build());
        assertThrows(Exception.class,
                () -> tool(errorResponse, "sk-test").execute("{\"query\":\"test\"}").block());
    }

    @Test
    void emptyResultsListReturnsEmptyArray() {
        String tavilyResponse = "{\"results\":[]}";
        String result = tool(jsonResponse(tavilyResponse), "sk-test")
                .execute("{\"query\":\"test\"}").block();
        assertEquals("[]", result);
    }

    @Test
    void nonListResultsReturnsEmptyArray() {
        String tavilyResponse = "{\"results\":\"not-a-list\"}";
        String result = tool(jsonResponse(tavilyResponse), "sk-test")
                .execute("{\"query\":\"test\"}").block();
        assertEquals("[]", result);
    }

    @Test
    void nullApiKeyFailsAtExecute() {
        TavilySearchTool t = tool(req -> Mono.empty(), null);
        assertThrows(IllegalArgumentException.class,
                () -> t.execute("{\"query\":\"test\"}").block());
    }

    @Test
    void nonMapItemInResultsIsSkipped() {
        String tavilyResponse = "{\"results\":[\"string-item\",{\"title\":\"T\",\"url\":\"https://t.com\",\"content\":\"c\"}]}";
        String result = tool(jsonResponse(tavilyResponse), "sk-test")
                .execute("{\"query\":\"test\"}").block();
        assertTrue(result.contains("T"));
        assertEquals(1, result.split("\\{").length - 1);
    }

    @Test
    void nullFieldInResultBecomesEmptyString() {
        String tavilyResponse = "{\"results\":[{\"url\":\"https://t.com\",\"content\":\"c\"}]}";
        String result = tool(jsonResponse(tavilyResponse), "sk-test")
                .execute("{\"query\":\"test\"}").block();
        assertTrue(result.contains(""));
        assertTrue(result.contains("https://t.com"));
    }

    @Test
    void invalidJsonResponseBodyReturnsEmptyArray() {
        String result = tool(jsonResponse("not-valid-json"), "sk-test")
                .execute("{\"query\":\"test\"}").block();
        assertEquals("[]", result);
    }
}

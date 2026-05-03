package com.kp.nsbh.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.memory.entity.MessageEntity;
import com.kp.nsbh.memory.entity.MessageRole;
import com.kp.nsbh.memory.entity.MessageType;
import com.kp.nsbh.tools.ToolRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class AnthropicLlmClientTest {

    @Test
    void firstReplyReturnsTextWhenNoToolUse() {
        String body = """
                {"content":[{"type":"text","text":"hello"}],
                 "stop_reason":"end_turn","model":"claude-3-haiku-20240307"}
                """;
        AnthropicLlmClient client = clientFor(jsonResponse(HttpStatus.OK, body));

        LlmReply reply = client.firstReply("hi", "claude-3-haiku-20240307", List.of()).block();

        assertFalse(reply.hasToolCalls());
        assertEquals("hello", reply.assistantMessage());
    }

    @Test
    void firstReplyReturnsToolCallWhenToolUsePresent() {
        String body = """
                {"content":[{"type":"tool_use","id":"call1",
                  "name":"time","input":{}}],
                 "stop_reason":"tool_use","model":"claude-3-haiku-20240307"}
                """;
        AnthropicLlmClient client = clientFor(jsonResponse(HttpStatus.OK, body));

        LlmReply reply = client.firstReply("what time", "claude-3-haiku-20240307", List.of()).block();

        assertTrue(reply.hasToolCalls());
        assertEquals("time", reply.toolCalls().get(0).toolName());
        assertEquals("call1", reply.toolCalls().get(0).id());
    }

    @Test
    void requestIncludesApiKeyHeaderAndAnthropicVersion() {
        String body = """
                {"content":[{"type":"text","text":"ok"}],
                 "stop_reason":"end_turn","model":"claude-3-haiku-20240307"}
                """;
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        ExchangeFunction capturing = req -> {
            captured.set(req);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build());
        };
        AnthropicLlmClient client = clientFor(capturing);
        client.firstReply("hi", "claude-3-haiku-20240307", List.of()).block();

        assertEquals("test-key", captured.get().headers().getFirst("x-api-key"));
        assertEquals("2023-06-01", captured.get().headers().getFirst("anthropic-version"));
    }

    @Test
    void systemMessagesExtractedToTopLevelField() {
        // Verify that providing a system message does not cause an error and returns normally.
        // The system field is extracted to the top-level Anthropic request (not in the messages array).
        String body = """
                {"content":[{"type":"text","text":"ok"}],
                 "stop_reason":"end_turn","model":"m"}
                """;
        MessageEntity sys = new MessageEntity();
        sys.setRole(MessageRole.SYSTEM);
        sys.setType(MessageType.NORMAL);
        sys.setContent("You are helpful");

        AnthropicLlmClient client = clientFor(jsonResponse(HttpStatus.OK, body));
        LlmReply reply = client.firstReply("hi", "m", List.of(sys)).block();

        assertFalse(reply.hasToolCalls());
        assertEquals("ok", reply.assistantMessage());
    }

    private AnthropicLlmClient clientFor(ExchangeFunction exchange) {
        NsbhProperties props = new NsbhProperties();
        props.getLlm().setProvider("anthropic");
        props.getLlm().setApiKey("test-key");
        props.getLlm().setBaseUrl("https://api.anthropic.com");
        props.getLlm().setTimeoutMs(5000);
        ToolRegistry toolRegistry = new ToolRegistry(List.of());
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchange);
        return new AnthropicLlmClient(builder, props, toolRegistry);
    }

    private ExchangeFunction jsonResponse(HttpStatus status, String body) {
        return request -> Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }
}

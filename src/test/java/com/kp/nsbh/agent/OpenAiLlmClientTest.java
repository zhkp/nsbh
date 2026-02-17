package com.kp.nsbh.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.memory.entity.MessageEntity;
import com.kp.nsbh.memory.entity.MessageRole;
import com.kp.nsbh.memory.entity.MessageType;
import com.kp.nsbh.tools.TimeTool;
import com.kp.nsbh.tools.ToolRegistry;
import java.util.List;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class OpenAiLlmClientTest {

    @Test
    void firstReplyShouldReturnToolCallWhenToolCallsPresent() {
        String body = """
                {"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call-1","type":"function","function":{"name":"time","arguments":"{}"}}]}}]}
                """;
        OpenAiLlmClient client = clientFor(response(HttpStatus.OK, body), 1000);

        LlmReply reply = client.firstReply("hello", "gpt-4.1-mini", fullPromptWindow()).block();

        assertNotNull(reply);
        assertNull(reply.assistantMessage());
        assertNotNull(reply.toolCall());
        assertEquals("call-1", reply.toolCall().id());
        assertEquals("time", reply.toolCall().toolName());
        assertEquals("{}", reply.toolCall().inputJson());
    }

    @Test
    void firstReplyShouldReturnAssistantMessageWhenNoToolCalls() {
        String body = """
                {"choices":[{"message":{"role":"assistant","content":"hi","tool_calls":[]}}]}
                """;
        OpenAiLlmClient client = clientFor(response(HttpStatus.OK, body), 1000);

        LlmReply reply = client.firstReply("hello", "gpt-4.1-mini", List.of()).block();

        assertNotNull(reply);
        assertEquals("hi", reply.assistantMessage());
        assertNull(reply.toolCall());
    }

    @Test
    void firstReplyShouldFailWhenToolCallFunctionNameMissing() {
        String body = """
                {"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call-1","type":"function","function":{"arguments":"{}"}}]}}]}
                """;
        OpenAiLlmClient client = clientFor(response(HttpStatus.OK, body), 1000);

        LlmClientException ex = assertThrows(LlmClientException.class,
                () -> client.firstReply("hello", "gpt-4.1-mini", List.of()).block());
        assertTrue(ex.getMessage().contains("invalid tool call"));
    }

    @Test
    void finalReplyShouldReturnEmptyWhenContentIsNull() {
        String body = """
                {"choices":[{"message":{"role":"assistant","content":null}}]}
                """;
        OpenAiLlmClient client = clientFor(response(HttpStatus.OK, body), 1000);

        String result = client.finalReply("hello", "gpt-4.1-mini", "tool", List.of()).block();

        assertEquals("", result);
    }

    @Test
    void summarizeShouldFailWhenChoicesMissing() {
        String body = "{\"choices\":[]}";
        OpenAiLlmClient client = clientFor(response(HttpStatus.OK, body), 1000);

        LlmClientException ex = assertThrows(LlmClientException.class,
                () -> client.summarize(List.of(), "gpt-4.1-mini").block());
        assertTrue(ex.getMessage().contains("no choices"));
    }

    @Test
    void shouldMap401Error() {
        String body = """
                {"error":{"message":"bad key"}}
                """;
        OpenAiLlmClient client = clientFor(response(HttpStatus.UNAUTHORIZED, body), 1000);

        LlmClientException ex = assertThrows(LlmClientException.class,
                () -> client.finalReply("hello", "gpt-4.1-mini", "tool", List.of()).block());
        assertTrue(ex.getMessage().contains("authentication failed"));
    }

    @Test
    void shouldMap429Error() {
        String body = """
                {"error":{"message":"limit"}}
                """;
        OpenAiLlmClient client = clientFor(response(HttpStatus.TOO_MANY_REQUESTS, body), 1000);

        LlmClientException ex = assertThrows(LlmClientException.class,
                () -> client.finalReply("hello", "gpt-4.1-mini", "tool", List.of()).block());
        assertTrue(ex.getMessage().contains("rate limit"));
    }

    @Test
    void shouldMap5xxError() {
        OpenAiLlmClient client = clientFor(response(HttpStatus.BAD_GATEWAY, "{\"error\":{\"message\":\"oops\"}}"), 1000);

        LlmClientException ex = assertThrows(LlmClientException.class,
                () -> client.finalReply("hello", "gpt-4.1-mini", "tool", List.of()).block());
        assertTrue(ex.getMessage().contains("service unavailable"));
    }

    @Test
    void shouldMap400ErrorWithOpenAiMessage() {
        String body = """
                {"error":{"message":"bad input"}}
                """;
        OpenAiLlmClient client = clientFor(response(HttpStatus.BAD_REQUEST, body), 1000);

        LlmClientException ex = assertThrows(LlmClientException.class,
                () -> client.finalReply("hello", "gpt-4.1-mini", "tool", List.of()).block());
        assertTrue(ex.getMessage().contains("400"));
        assertTrue(ex.getMessage().contains("bad input"));
    }

    @Test
    void shouldMap400ErrorWithoutOpenAiMessage() {
        OpenAiLlmClient client = clientFor(response(HttpStatus.BAD_REQUEST, "{\"error\":{}}"), 1000);

        LlmClientException ex = assertThrows(LlmClientException.class,
                () -> client.finalReply("hello", "gpt-4.1-mini", "tool", List.of()).block());
        assertTrue(ex.getMessage().contains("OpenAI request failed"));
    }

    @Test
    void shouldFailWhenResponseHasNoMessage() {
        OpenAiLlmClient client = clientFor(response(HttpStatus.OK, "{\"choices\":[{}]}"), 1000);

        LlmClientException ex = assertThrows(LlmClientException.class,
                () -> client.finalReply("hello", "gpt-4.1-mini", "tool", List.of()).block());
        assertTrue(ex.getMessage().contains("no message"));
    }

    @Test
    void shouldHandleInvalidToolSchemaJson() {
        OpenAiLlmClient client = clientFor(
                response(HttpStatus.OK, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}]}"),
                1000,
                List.of(new BrokenSchemaTool())
        );

        LlmReply reply = client.firstReply("hello", "gpt-4.1-mini", List.of()).block();
        assertNotNull(reply);
        assertEquals("ok", reply.assistantMessage());
    }

    @Test
    void privateHelpersShouldCoverRemainingBranches() throws Exception {
        OpenAiLlmClient client = clientFor(response(HttpStatus.OK, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}]}"), 1000);

        Method extract = OpenAiLlmClient.class.getDeclaredMethod("extractOpenAiErrorMessage", String.class);
        extract.setAccessible(true);
        assertNull(extract.invoke(client, (String) null));
        assertNull(extract.invoke(client, "no-message-here"));
        assertNull(extract.invoke(client, "{\"message\":}"));

        Method parse = OpenAiLlmClient.class.getDeclaredMethod("parseSchema", String.class);
        parse.setAccessible(true);
        Object blankSchema = parse.invoke(client, "");
        Object invalidSchema = parse.invoke(client, "{");
        assertNotNull(blankSchema);
        assertNotNull(invalidSchema);

        Method rootCause = OpenAiLlmClient.class.getDeclaredMethod("rootCauseMessage", Throwable.class);
        rootCause.setAccessible(true);
        String msg = (String) rootCause.invoke(client, new RuntimeException(""));
        assertTrue(msg.contains("RuntimeException"));

        Method firstMessage = OpenAiLlmClient.class.getDeclaredMethod(
                "firstMessage",
                Class.forName("com.kp.nsbh.agent.OpenAiLlmClient$ChatCompletionsResponse")
        );
        firstMessage.setAccessible(true);

        Exception noResponse = assertThrows(Exception.class, () -> firstMessage.invoke(client, new Object[] {null}));
        assertTrue(noResponse.getCause() instanceof LlmClientException);
        assertTrue(noResponse.getCause().getMessage().contains("no choices"));

        Class<?> responseType = Class.forName("com.kp.nsbh.agent.OpenAiLlmClient$ChatCompletionsResponse");
        Constructor<?> responseCtor = responseType.getDeclaredConstructor(List.class);
        responseCtor.setAccessible(true);
        Object nullChoices = responseCtor.newInstance(new Object[] {null});
        Exception noChoicesEx = assertThrows(Exception.class, () -> firstMessage.invoke(client, nullChoices));
        assertTrue(noChoicesEx.getCause() instanceof LlmClientException);
        assertTrue(noChoicesEx.getCause().getMessage().contains("no choices"));
    }

    @Test
    void shouldMapWebClientResponseExceptionBranch() {
        ExchangeFunction error = request -> Mono.error(org.springframework.web.reactive.function.client.WebClientResponseException.create(
                429, "Too Many Requests", HttpHeaders.EMPTY, "{\"error\":{\"message\":\"quota\"}}".getBytes(), StandardCharsets.UTF_8
        ));
        OpenAiLlmClient client = clientFor(error, 1000);

        LlmClientException ex = assertThrows(LlmClientException.class,
                () -> client.finalReply("hello", "gpt-4.1-mini", "tool", List.of()).block());
        assertTrue(ex.getMessage().contains("rate limit"));
    }

    @Test
    void shouldFailOnTimeout() {
        ExchangeFunction neverReturn = request -> Mono.never();
        OpenAiLlmClient client = clientFor(neverReturn, 1);

        LlmClientException ex = assertThrows(LlmClientException.class,
                () -> client.finalReply("hello", "gpt-4.1-mini", "tool", List.of()).block());
        assertTrue(ex.getMessage().contains("OpenAI request failed"));
    }

    private OpenAiLlmClient clientFor(ExchangeFunction exchangeFunction, long timeoutMs) {
        return clientFor(exchangeFunction, timeoutMs, List.of(new TimeTool()));
    }

    private OpenAiLlmClient clientFor(ExchangeFunction exchangeFunction, long timeoutMs, List<com.kp.nsbh.tools.Tool> tools) {
        NsbhProperties properties = new NsbhProperties();
        properties.getLlm().setProvider("openai");
        properties.getLlm().setApiKey("test-key");
        properties.getLlm().setTimeoutMs(timeoutMs);
        properties.getLlm().setBaseUrl("https://example.test");
        ToolRegistry toolRegistry = new ToolRegistry(tools);
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);
        return new OpenAiLlmClient(builder, properties, new ObjectMapper(), toolRegistry);
    }

    private ExchangeFunction response(HttpStatus status, String body) {
        return request -> Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    private List<MessageEntity> fullPromptWindow() {
        MessageEntity system = msg(MessageRole.SYSTEM, "system", null, null);
        MessageEntity user = msg(MessageRole.USER, "user", null, null);
        MessageEntity assistant = msg(MessageRole.ASSISTANT, "assistant", null, null);
        MessageEntity tool = msg(MessageRole.TOOL, "tool", "time", "call-1");
        return List.of(system, user, assistant, tool);
    }

    private MessageEntity msg(MessageRole role, String content, String toolName, String toolCallId) {
        MessageEntity message = new MessageEntity();
        message.setRole(role);
        message.setType(MessageType.NORMAL);
        message.setContent(content);
        message.setToolName(toolName);
        message.setToolCallId(toolCallId);
        return message;
    }

    @com.kp.nsbh.tools.NsbhTool(name = "broken", description = "broken", schema = "{")
    static class BrokenSchemaTool implements com.kp.nsbh.tools.Tool {
        @Override
        public Mono<String> execute(String inputJson) {
            return Mono.just("ok");
        }
    }
}

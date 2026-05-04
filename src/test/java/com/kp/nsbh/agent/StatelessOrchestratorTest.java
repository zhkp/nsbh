package com.kp.nsbh.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.openai.OpenAiMessage;
import com.kp.nsbh.tools.ToolCallReason;
import com.kp.nsbh.tools.ToolCallStatus;
import com.kp.nsbh.tools.ToolExecutionResult;
import com.kp.nsbh.tools.ToolService;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class StatelessOrchestratorTest {

    private final MockLlmClient llm = new MockLlmClient();
    private final ToolService toolService = mock(ToolService.class);
    private final NsbhProperties properties = new NsbhProperties();
    private final StatelessOrchestrator orchestrator =
            new StatelessOrchestrator(llm, toolService, properties);

    @Test
    void chatNoToolCallsReturnsAssistantMessage() {
        llm.script(LlmReply.text("hello"));
        String result = orchestrator.chat(List.of(new OpenAiMessage("user", "hi")), "mock").block();
        assertEquals("hello", result);
    }

    @Test
    void chatSingleToolRoundExecutesAndReturnsResult() {
        llm.script(
                LlmReply.withTool(new ToolCallRequest("c1", "time", "{}")),
                LlmReply.text("The time is 12:00")
        );
        when(toolService.execute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(new ToolExecutionResult(
                        "time", ToolCallStatus.SUCCESS, ToolCallReason.NONE, "12:00", "c1")));

        String result = orchestrator.chat(
                List.of(new OpenAiMessage("user", "what time?")), "mock").block();
        assertEquals("The time is 12:00", result);
    }

    @Test
    void chatExceedsMaxToolRoundsWithNoAssistantReturnsEmpty() {
        properties.getAgent().setMaxToolRounds(0);
        llm.script(LlmReply.withTool(new ToolCallRequest("c1", "time", "{}")));

        String result = orchestrator.chat(
                List.of(new OpenAiMessage("user", "hi")), "mock").block();
        assertEquals("", result);
    }

    @Test
    void chatExceedsMaxToolRoundsReturnsLastAssistantFromHistory() {
        properties.getAgent().setMaxToolRounds(0);

        String result = orchestrator.chat(List.of(
                new OpenAiMessage("user", "hi"),
                new OpenAiMessage("assistant", "previous reply"),
                new OpenAiMessage("user", "follow-up")
        ), "mock").block();
        assertEquals("previous reply", result);
    }

    @Test
    void chatHandlesNullMessageContent() {
        llm.script(LlmReply.text("ok"));
        String result = orchestrator.chat(
                List.of(new OpenAiMessage("user", null)), "mock").block();
        assertEquals("ok", result);
    }

    @Test
    void chatMapsDifferentRoles() {
        llm.script(LlmReply.text("ok"));
        String result = orchestrator.chat(List.of(
                new OpenAiMessage("system", "be helpful"),
                new OpenAiMessage("user", "hi"),
                new OpenAiMessage("assistant", "hello"),
                new OpenAiMessage("tool", "result")
        ), "mock").block();
        assertEquals("ok", result);
    }

    @Test
    void streamNoToolCallsReturnsTokens() {
        llm.script(LlmReply.text("hello world"));
        List<String> tokens = orchestrator.stream(
                List.of(new OpenAiMessage("user", "hi")), "mock").collectList().block();
        assertEquals(List.of("hello world"), tokens);
    }

    @Test
    void streamWithToolCallContinuesAfterExecution() {
        llm.script(
                LlmReply.withTool(new ToolCallRequest("c1", "time", "{}")),
                LlmReply.text("It is 12:00")
        );
        when(toolService.execute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(new ToolExecutionResult(
                        "time", ToolCallStatus.SUCCESS, ToolCallReason.NONE, "12:00", "c1")));

        List<String> tokens = orchestrator.stream(
                List.of(new OpenAiMessage("user", "time?")), "mock").collectList().block();
        assertFalse(tokens.isEmpty());
    }

    @Test
    void streamExceedsMaxToolRoundsNoAssistantProducesEmptyFlux() {
        properties.getAgent().setMaxToolRounds(0);
        llm.script(LlmReply.withTool(new ToolCallRequest("c1", "time", "{}")));

        List<String> tokens = orchestrator.stream(
                List.of(new OpenAiMessage("user", "hi")), "mock").collectList().block();
        assertTrue(tokens.isEmpty());
    }

    @Test
    void streamBlankAssistantMessageProducesEmptyFlux() {
        llm.script(LlmReply.text(""));

        List<String> tokens = orchestrator.stream(
                List.of(new OpenAiMessage("user", "hi")), "mock").collectList().block();
        assertTrue(tokens.isEmpty());
    }

    @Test
    void streamExceedsMaxWithAssistantInHistoryReturnsLastAssistant() {
        properties.getAgent().setMaxToolRounds(0);

        List<String> tokens = orchestrator.stream(List.of(
                new OpenAiMessage("assistant", "hello from before")
        ), "mock").collectList().block();
        assertEquals(List.of("hello from before"), tokens);
    }
}

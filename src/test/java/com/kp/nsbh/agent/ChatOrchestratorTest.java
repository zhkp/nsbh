package com.kp.nsbh.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.memory.entity.MessageEntity;
import com.kp.nsbh.memory.entity.MessageRole;
import com.kp.nsbh.memory.entity.MessageType;
import com.kp.nsbh.memory.repo.MessageRepository;
import com.kp.nsbh.tools.ToolCallReason;
import com.kp.nsbh.tools.ToolCallStatus;
import com.kp.nsbh.tools.ToolExecutionResult;
import com.kp.nsbh.tools.ToolService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ChatOrchestratorTest {

    @Test
    void noToolCallsEmitsTextDeltaThenDone() {
        MockLlmClient llm = new MockLlmClient();
        llm.script(LlmReply.text("hello"));

        ChatOrchestrator orchestrator = buildOrchestrator(llm, mock(ToolService.class));

        List<AgentEvent> events = orchestrator
                .orchestrate(UUID.randomUUID(), "hi", "model")
                .collectList().block();

        assertEquals(2, events.size());
        assertInstanceOf(AgentEvent.TextDelta.class, events.get(0));
        assertInstanceOf(AgentEvent.Done.class, events.get(1));
        assertEquals("hello", ((AgentEvent.Done) events.get(1)).fullText());
    }

    @Test
    void oneToolCallEmitsToolStartToolEndTextDeltaDone() {
        MockLlmClient llm = new MockLlmClient();
        llm.script(
                LlmReply.withTool(new ToolCallRequest("call-1", "time", "{}")),
                LlmReply.text("result is 12:00")
        );

        ToolService toolService = mock(ToolService.class);
        when(toolService.execute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(new ToolExecutionResult(
                        "time", ToolCallStatus.SUCCESS, ToolCallReason.NONE, "12:00", "call-1")));

        ChatOrchestrator orchestrator = buildOrchestrator(llm, toolService);

        List<AgentEvent> events = orchestrator
                .orchestrate(UUID.randomUUID(), "what time", "model")
                .collectList().block();

        long toolStarts = events.stream().filter(e -> e instanceof AgentEvent.ToolStart).count();
        long toolEnds = events.stream().filter(e -> e instanceof AgentEvent.ToolEnd).count();
        long textDeltas = events.stream().filter(e -> e instanceof AgentEvent.TextDelta).count();
        long dones = events.stream().filter(e -> e instanceof AgentEvent.Done).count();

        assertEquals(1, toolStarts);
        assertEquals(1, toolEnds);
        assertEquals(1, textDeltas);
        assertEquals(1, dones);
    }

    @Test
    void doneEventContainsAllAccumulatedToolResults() {
        MockLlmClient llm = new MockLlmClient();
        llm.script(
                LlmReply.withTool(new ToolCallRequest("c1", "time", "{}")),
                LlmReply.withTool(new ToolCallRequest("c2", "time", "{}")),
                LlmReply.text("done")
        );

        ToolService toolService = mock(ToolService.class);
        when(toolService.execute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(new ToolExecutionResult(
                        "time", ToolCallStatus.SUCCESS, ToolCallReason.NONE, "ok", "c1")));

        ChatOrchestrator orchestrator = buildOrchestrator(llm, toolService);

        List<AgentEvent> events = orchestrator
                .orchestrate(UUID.randomUUID(), "msg", "model")
                .collectList().block();

        AgentEvent.Done done = events.stream()
                .filter(e -> e instanceof AgentEvent.Done)
                .map(e -> (AgentEvent.Done) e)
                .findFirst().orElseThrow();

        assertEquals(2, done.toolResults().size());
    }

    @Test
    void maxToolRoundsStopsLoop() {
        MockLlmClient llm = new MockLlmClient();
        // Always returns a tool call — should be stopped by maxToolRounds
        llm.script(LlmReply.withTool(new ToolCallRequest("c", "time", "{}")));

        ToolService toolService = mock(ToolService.class);
        when(toolService.execute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(new ToolExecutionResult(
                        "time", ToolCallStatus.SUCCESS, ToolCallReason.NONE, "ok", "c")));

        NsbhProperties props = new NsbhProperties();
        props.getAgent().setMaxToolRounds(2);

        ChatOrchestrator orchestrator = buildOrchestrator(llm, toolService, props);

        List<AgentEvent> events = orchestrator
                .orchestrate(UUID.randomUUID(), "msg", "model")
                .collectList().block();

        long toolStarts = events.stream().filter(e -> e instanceof AgentEvent.ToolStart).count();
        assertEquals(2, toolStarts); // stopped after 2 rounds
        AgentEvent last = events.get(events.size() - 1);
        assertInstanceOf(AgentEvent.Done.class, last);
    }

    @Test
    void parallelToolCallsBothExecuted() {
        MockLlmClient llm = new MockLlmClient();
        llm.script(
                LlmReply.withTools(List.of(
                        new ToolCallRequest("c1", "time", "{}"),
                        new ToolCallRequest("c2", "time", "{}")
                )),
                LlmReply.text("done")
        );

        ToolService toolService = mock(ToolService.class);
        when(toolService.execute(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> Mono.just(new ToolExecutionResult(
                        "time", ToolCallStatus.SUCCESS, ToolCallReason.NONE, "ok",
                        inv.getArgument(3))));

        ChatOrchestrator orchestrator = buildOrchestrator(llm, toolService);

        List<AgentEvent> events = orchestrator
                .orchestrate(UUID.randomUUID(), "msg", "model")
                .collectList().block();

        long toolStarts = events.stream().filter(e -> e instanceof AgentEvent.ToolStart).count();
        long toolEnds = events.stream().filter(e -> e instanceof AgentEvent.ToolEnd).count();
        assertEquals(2, toolStarts);
        assertEquals(2, toolEnds);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ChatOrchestrator buildOrchestrator(MockLlmClient llm, ToolService toolService) {
        return buildOrchestrator(llm, toolService, new NsbhProperties());
    }

    private ChatOrchestrator buildOrchestrator(MockLlmClient llm, ToolService toolService,
                                                NsbhProperties props) {
        MessageRepository repo = mock(MessageRepository.class);
        when(repo.save(any(MessageEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(repo.findByConversationIdOrderByCreatedAtAsc(any(UUID.class)))
                .thenReturn(Flux.empty());

        PromptBuilder promptBuilder = new PromptBuilder(repo, props);
        MemoryService memoryService = new MemoryService(repo, llm, props);

        return new ChatOrchestrator(llm, toolService, promptBuilder, memoryService, repo, props);
    }
}

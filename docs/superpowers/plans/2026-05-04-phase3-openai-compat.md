# Phase 3: OpenAI Compatible API — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose an OpenAI-compatible `/v1/chat/completions` endpoint with Bearer token auth so standard clients can use NSBH as a drop-in OpenAI backend.

**Architecture:** Three new components — `ApiKeyAuthFilter` (WebFilter for `/v1/**`), `StatelessOrchestrator` (in-memory multi-round agent loop, no DB), `OpenAiCompatController` (GET `/v1/models` + POST `/v1/chat/completions`). Two new `LlmClient` overloads take `List<MessageEntity>` directly. Content negotiation (Accept header) routes streaming vs. non-streaming at the same `/v1/chat/completions` URL.

**Tech Stack:** Spring WebFlux, Jackson, JUnit 5 + Mockito, `WebTestClient`.

**Coverage gate:** `mvn verify` requires ≥ 80% branch coverage (JaCoCo). Every branch in new code must be tested.

---

## File Map

**Create:**
- `src/main/java/com/kp/nsbh/openai/OpenAiMessage.java`
- `src/main/java/com/kp/nsbh/openai/OpenAiChatRequest.java`
- `src/main/java/com/kp/nsbh/openai/OpenAiChatResponse.java`
- `src/main/java/com/kp/nsbh/openai/OpenAiChatChunk.java`
- `src/main/java/com/kp/nsbh/agent/StatelessOrchestrator.java`
- `src/main/java/com/kp/nsbh/api/ApiKeyAuthFilter.java`
- `src/main/java/com/kp/nsbh/openai/OpenAiCompatController.java`
- `src/test/java/com/kp/nsbh/agent/StatelessOrchestratorTest.java`
- `src/test/java/com/kp/nsbh/api/ApiKeyAuthFilterTest.java`
- `src/test/java/com/kp/nsbh/openai/OpenAiCompatControllerTest.java`
- `src/test/java/com/kp/nsbh/openai/OpenAiCompatControllerSliceTest.java`

**Modify:**
- `src/main/java/com/kp/nsbh/config/NsbhProperties.java` — add `Api` inner class + `api` field
- `src/main/java/com/kp/nsbh/agent/LlmClient.java` — add two stateless overloads
- `src/main/java/com/kp/nsbh/agent/MockLlmClient.java` — implement new overloads
- `src/main/java/com/kp/nsbh/agent/OpenAiLlmClient.java` — implement new overloads
- `src/main/java/com/kp/nsbh/agent/AnthropicLlmClient.java` — implement new overloads
- `src/main/resources/application.yml` — add `nsbh.api.key`

---

## Task 1: NsbhProperties — Add api.key config

**Files:**
- Modify: `src/main/java/com/kp/nsbh/config/NsbhProperties.java`
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: Add `Api` inner class and field to `NsbhProperties`**

  Inside `NsbhProperties.java`, add the `api` field after the `mcp` field, add its getter, and add the inner class. The class body currently ends at `}` for `McpServerConfig`. Add before the closing `}` of `NsbhProperties`:

  ```java
  // In NsbhProperties class fields (after `private final Mcp mcp = new Mcp();`):
  private final Api api = new Api();

  // In NsbhProperties getters (after `public Mcp getMcp() { return mcp; }`):
  public Api getApi() { return api; }

  // New inner class (add after McpServerConfig):
  public static class Api {
      private String key = "";

      public String getKey() { return key; }
      public void setKey(String key) { this.key = key; }
  }
  ```

- [ ] **Step 2: Add `nsbh.api.key` to `application.yml`**

  In `src/main/resources/application.yml`, inside the `nsbh:` block (after `mcp:` section or at the end of the `nsbh:` block):

  ```yaml
  nsbh:
    # ... existing config ...
    api:
      key: ${NSBH_API_KEY:}   # blank = no auth (local dev convenience)
  ```

- [ ] **Step 3: Verify compilation**

  ```bash
  mvn compile -q
  ```

  Expected: BUILD SUCCESS with no errors.

- [ ] **Step 4: Commit**

  ```bash
  git add src/main/java/com/kp/nsbh/config/NsbhProperties.java src/main/resources/application.yml
  git commit -m "feat: NsbhProperties — add api.key config field"
  ```

---

## Task 2: OpenAI DTO records

**Files:**
- Create: `src/main/java/com/kp/nsbh/openai/OpenAiMessage.java`
- Create: `src/main/java/com/kp/nsbh/openai/OpenAiChatRequest.java`
- Create: `src/main/java/com/kp/nsbh/openai/OpenAiChatResponse.java`
- Create: `src/main/java/com/kp/nsbh/openai/OpenAiChatChunk.java`

- [ ] **Step 1: Create `OpenAiMessage`**

  ```java
  // src/main/java/com/kp/nsbh/openai/OpenAiMessage.java
  package com.kp.nsbh.openai;

  public record OpenAiMessage(String role, String content) {}
  ```

- [ ] **Step 2: Create `OpenAiChatRequest`**

  ```java
  // src/main/java/com/kp/nsbh/openai/OpenAiChatRequest.java
  package com.kp.nsbh.openai;

  import java.util.List;

  public record OpenAiChatRequest(String model, List<OpenAiMessage> messages, Boolean stream) {}
  ```

- [ ] **Step 3: Create `OpenAiChatResponse`**

  ```java
  // src/main/java/com/kp/nsbh/openai/OpenAiChatResponse.java
  package com.kp.nsbh.openai;

  import com.fasterxml.jackson.annotation.JsonProperty;
  import java.util.List;

  public record OpenAiChatResponse(
          String id,
          String object,
          long created,
          String model,
          List<Choice> choices,
          Usage usage
  ) {
      public record Choice(
              int index,
              Message message,
              @JsonProperty("finish_reason") String finishReason
      ) {}

      public record Message(String role, String content) {}

      public record Usage(
              @JsonProperty("prompt_tokens") int promptTokens,
              @JsonProperty("completion_tokens") int completionTokens,
              @JsonProperty("total_tokens") int totalTokens
      ) {}
  }
  ```

- [ ] **Step 4: Create `OpenAiChatChunk`**

  ```java
  // src/main/java/com/kp/nsbh/openai/OpenAiChatChunk.java
  package com.kp.nsbh.openai;

  import com.fasterxml.jackson.annotation.JsonInclude;
  import com.fasterxml.jackson.annotation.JsonProperty;
  import java.util.List;

  public record OpenAiChatChunk(
          String id,
          String object,
          long created,
          String model,
          List<ChunkChoice> choices
  ) {
      public record ChunkChoice(
              int index,
              Delta delta,
              @JsonProperty("finish_reason") String finishReason
      ) {}

      @JsonInclude(JsonInclude.Include.NON_NULL)
      public record Delta(String role, String content) {}
  }
  ```

- [ ] **Step 5: Verify compilation**

  ```bash
  mvn compile -q
  ```

  Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

  ```bash
  git add src/main/java/com/kp/nsbh/openai/
  git commit -m "feat: OpenAI DTO records — OpenAiMessage, Request, Response, Chunk"
  ```

---

## Task 3: LlmClient stateless overloads

**Files:**
- Modify: `src/main/java/com/kp/nsbh/agent/LlmClient.java`
- Modify: `src/main/java/com/kp/nsbh/agent/MockLlmClient.java`
- Modify: `src/main/java/com/kp/nsbh/agent/OpenAiLlmClient.java`
- Modify: `src/main/java/com/kp/nsbh/agent/AnthropicLlmClient.java`

These overloads take the full `List<MessageEntity>` directly (no `userMessage` parameter).

- [ ] **Step 1: Write the failing test**

  Create `src/test/java/com/kp/nsbh/agent/MockLlmClientStatelessOverloadTest.java`:

  ```java
  package com.kp.nsbh.agent;

  import static org.junit.jupiter.api.Assertions.assertEquals;
  import static org.junit.jupiter.api.Assertions.assertTrue;

  import com.kp.nsbh.memory.entity.MessageEntity;
  import com.kp.nsbh.memory.entity.MessageRole;
  import com.kp.nsbh.memory.entity.MessageType;
  import java.util.List;
  import org.junit.jupiter.api.Test;

  class MockLlmClientStatelessOverloadTest {

      private final MockLlmClient llm = new MockLlmClient();

      private MessageEntity userEntity(String content) {
          MessageEntity e = new MessageEntity();
          e.setRole(MessageRole.USER);
          e.setType(MessageType.NORMAL);
          e.setContent(content);
          return e;
      }

      @Test
      void noScriptFirstReplyReturnsLastUserContent() {
          List<MessageEntity> messages = List.of(userEntity("hello"));
          LlmReply reply = llm.firstReply(messages, "mock").block();
          assertEquals("Mock: hello", reply.assistantMessage());
      }

      @Test
      void noScriptNoUserMessageReturnsEmptyPrefix() {
          List<MessageEntity> messages = List.of();
          LlmReply reply = llm.firstReply(messages, "mock").block();
          assertEquals("Mock: ", reply.assistantMessage());
      }

      @Test
      void scriptModeFirstReplyUsesScript() {
          llm.script(LlmReply.text("scripted reply"));
          List<MessageEntity> messages = List.of(userEntity("hi"));
          LlmReply reply = llm.firstReply(messages, "mock").block();
          assertEquals("scripted reply", reply.assistantMessage());
      }

      @Test
      void streamFirstReplyNonBlankReturnsOneToken() {
          llm.script(LlmReply.text("streamed"));
          List<MessageEntity> messages = List.of(userEntity("hi"));
          List<String> tokens = llm.streamFirstReply(messages, "mock").collectList().block();
          assertEquals(List.of("streamed"), tokens);
      }

      @Test
      void streamFirstReplyBlankReplyProducesEmptyFlux() {
          llm.script(LlmReply.text(""));
          List<MessageEntity> messages = List.of(userEntity("hi"));
          List<String> tokens = llm.streamFirstReply(messages, "mock").collectList().block();
          assertTrue(tokens.isEmpty());
      }

      @Test
      void streamFirstReplyNullReplyProducesEmptyFlux() {
          llm.script(LlmReply.text(null));
          List<MessageEntity> messages = List.of(userEntity("hi"));
          List<String> tokens = llm.streamFirstReply(messages, "mock").collectList().block();
          assertTrue(tokens.isEmpty());
      }
  }
  ```

- [ ] **Step 2: Run test to verify it fails**

  ```bash
  mvn test -pl . -Dtest=MockLlmClientStatelessOverloadTest -q 2>&1 | tail -20
  ```

  Expected: FAIL — `LlmClient` does not yet have the new overload methods.

- [ ] **Step 3: Add overloads to `LlmClient` interface**

  In `src/main/java/com/kp/nsbh/agent/LlmClient.java`, add after the existing `streamFirstReply` overload (before `summarize`):

  ```java
  // Stateless overloads: full message list used as context directly, no userMessage separation
  Mono<LlmReply> firstReply(List<MessageEntity> messages, String model);

  Flux<String> streamFirstReply(List<MessageEntity> messages, String model);
  ```

- [ ] **Step 4: Implement in `MockLlmClient`**

  In `MockLlmClient.java`, add after the existing `streamFirstReply` overload:

  ```java
  @Override
  public Mono<LlmReply> firstReply(List<MessageEntity> messages, String model) {
      if (script != null) {
          int i = Math.min(scriptIndex.getAndIncrement(), script.size() - 1);
          return Mono.just(script.get(i));
      }
      String lastUserContent = messages.stream()
              .filter(m -> m.getRole() == MessageRole.USER)
              .reduce((a, b) -> b)
              .map(MessageEntity::getContent)
              .orElse("");
      return Mono.just(LlmReply.text("Mock: " + lastUserContent));
  }

  @Override
  public Flux<String> streamFirstReply(List<MessageEntity> messages, String model) {
      return firstReply(messages, model)
              .flatMapMany(reply -> {
                  String msg = reply.assistantMessage();
                  if (msg == null || msg.isBlank()) return Flux.empty();
                  return Flux.just(msg);
              });
  }
  ```

- [ ] **Step 5: Implement in `OpenAiLlmClient`**

  In `OpenAiLlmClient.java`, add after the existing `streamFirstReply` overload (before `summarize`). The existing `firstReply(String, String, List<MessageEntity>)` ignores `userMessage` in its request, so delegation is safe:

  ```java
  @Override
  public Mono<LlmReply> firstReply(List<MessageEntity> messages, String model) {
      return firstReply("", model, messages);
  }

  @Override
  public Flux<String> streamFirstReply(List<MessageEntity> messages, String model) {
      return streamFirstReply("", model, messages);
  }
  ```

- [ ] **Step 6: Implement in `AnthropicLlmClient`**

  In `AnthropicLlmClient.java`, add after the existing `streamFirstReply` overload:

  ```java
  @Override
  public Mono<LlmReply> firstReply(List<MessageEntity> messages, String model) {
      return firstReply("", model, messages);
  }

  @Override
  public Flux<String> streamFirstReply(List<MessageEntity> messages, String model) {
      return streamFirstReply("", model, messages);
  }
  ```

  `OllamaLlmClient` extends `OpenAiLlmClient` and inherits the new overloads — no changes needed.

- [ ] **Step 7: Run test to verify it passes**

  ```bash
  mvn test -pl . -Dtest=MockLlmClientStatelessOverloadTest -q 2>&1 | tail -10
  ```

  Expected: BUILD SUCCESS, 6 tests passed.

- [ ] **Step 8: Commit**

  ```bash
  git add src/main/java/com/kp/nsbh/agent/ src/test/java/com/kp/nsbh/agent/MockLlmClientStatelessOverloadTest.java
  git commit -m "feat: LlmClient stateless overloads — firstReply/streamFirstReply(List<MessageEntity>, String)"
  ```

---

## Task 4: StatelessOrchestrator (TDD)

**Files:**
- Create: `src/main/java/com/kp/nsbh/agent/StatelessOrchestrator.java`
- Create: `src/test/java/com/kp/nsbh/agent/StatelessOrchestratorTest.java`

- [ ] **Step 1: Write the failing test**

  Create `src/test/java/com/kp/nsbh/agent/StatelessOrchestratorTest.java`:

  ```java
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
  ```

- [ ] **Step 2: Run test to verify it fails**

  ```bash
  mvn test -pl . -Dtest=StatelessOrchestratorTest -q 2>&1 | tail -10
  ```

  Expected: FAIL — `StatelessOrchestrator` does not exist yet.

- [ ] **Step 3: Implement `StatelessOrchestrator`**

  Create `src/main/java/com/kp/nsbh/agent/StatelessOrchestrator.java`:

  ```java
  package com.kp.nsbh.agent;

  import com.kp.nsbh.config.NsbhProperties;
  import com.kp.nsbh.memory.entity.MessageEntity;
  import com.kp.nsbh.memory.entity.MessageRole;
  import com.kp.nsbh.memory.entity.MessageType;
  import com.kp.nsbh.openai.OpenAiMessage;
  import com.kp.nsbh.tools.ToolExecutionResult;
  import com.kp.nsbh.tools.ToolService;
  import java.util.ArrayList;
  import java.util.List;
  import java.util.UUID;
  import java.util.stream.Collectors;
  import org.springframework.stereotype.Service;
  import reactor.core.publisher.Flux;
  import reactor.core.publisher.Mono;

  @Service
  public class StatelessOrchestrator {

      private final LlmClient llmClient;
      private final ToolService toolService;
      private final NsbhProperties properties;

      public StatelessOrchestrator(LlmClient llmClient,
                                   ToolService toolService,
                                   NsbhProperties properties) {
          this.llmClient = llmClient;
          this.toolService = toolService;
          this.properties = properties;
      }

      public Mono<String> chat(List<OpenAiMessage> messages, String model) {
          return loop(toEntities(messages), model, 0);
      }

      public Flux<String> stream(List<OpenAiMessage> messages, String model) {
          return streamLoop(toEntities(messages), model, 0);
      }

      private Mono<String> loop(List<MessageEntity> messages, String model, int round) {
          if (round >= properties.getAgent().getMaxToolRounds()) {
              return Mono.just(lastAssistantContent(messages));
          }
          return llmClient.firstReply(messages, model)
                  .flatMap(reply -> {
                      if (!reply.hasToolCalls()) {
                          return Mono.just(reply.assistantMessage() == null ? "" : reply.assistantMessage());
                      }
                      return executeToolsAndContinue(reply.toolCalls(), messages, model, round);
                  });
      }

      private Mono<String> executeToolsAndContinue(List<ToolCallRequest> toolCalls,
                                                    List<MessageEntity> messages,
                                                    String model,
                                                    int round) {
          String sessionId = UUID.randomUUID().toString();
          return Flux.fromIterable(toolCalls)
                  .flatMap(tc -> toolService.execute(sessionId, tc.toolName(), tc.inputJson(), tc.id()))
                  .collectList()
                  .flatMap(results -> {
                      List<MessageEntity> next = new ArrayList<>(messages);
                      for (ToolExecutionResult r : results) {
                          next.add(toolEntity(r));
                      }
                      return loop(next, model, round + 1);
                  });
      }

      private Flux<String> streamLoop(List<MessageEntity> messages, String model, int round) {
          if (round >= properties.getAgent().getMaxToolRounds()) {
              String last = lastAssistantContent(messages);
              return last.isBlank() ? Flux.empty() : Flux.just(last);
          }
          return llmClient.firstReply(messages, model)
                  .flatMapMany(reply -> {
                      if (!reply.hasToolCalls()) {
                          return llmClient.streamFirstReply(messages, model);
                      }
                      String sessionId = UUID.randomUUID().toString();
                      return Flux.fromIterable(reply.toolCalls())
                              .flatMap(tc -> toolService.execute(
                                      sessionId, tc.toolName(), tc.inputJson(), tc.id()))
                              .collectList()
                              .flatMapMany(results -> {
                                  List<MessageEntity> next = new ArrayList<>(messages);
                                  for (ToolExecutionResult r : results) {
                                      next.add(toolEntity(r));
                                  }
                                  return streamLoop(next, model, round + 1);
                              });
                  });
      }

      private String lastAssistantContent(List<MessageEntity> messages) {
          return messages.stream()
                  .filter(m -> m.getRole() == MessageRole.ASSISTANT)
                  .reduce((a, b) -> b)
                  .map(MessageEntity::getContent)
                  .orElse("");
      }

      private List<MessageEntity> toEntities(List<OpenAiMessage> messages) {
          return messages.stream().map(m -> {
              MessageEntity e = new MessageEntity();
              e.setRole(mapRole(m.role()));
              e.setType(MessageType.NORMAL);
              e.setContent(m.content() == null ? "" : m.content());
              return e;
          }).collect(Collectors.toList());
      }

      private MessageRole mapRole(String role) {
          return switch (role) {
              case "assistant" -> MessageRole.ASSISTANT;
              case "system" -> MessageRole.SYSTEM;
              case "tool" -> MessageRole.TOOL;
              default -> MessageRole.USER;
          };
      }

      private MessageEntity toolEntity(ToolExecutionResult r) {
          MessageEntity e = new MessageEntity();
          e.setRole(MessageRole.TOOL);
          e.setType(MessageType.NORMAL);
          e.setContent(r.result());
          e.setToolName(r.toolName());
          e.setToolCallId(r.toolCallId());
          return e;
      }
  }
  ```

- [ ] **Step 4: Run test to verify it passes**

  ```bash
  mvn test -pl . -Dtest=StatelessOrchestratorTest -q 2>&1 | tail -10
  ```

  Expected: BUILD SUCCESS, 11 tests passed.

- [ ] **Step 5: Commit**

  ```bash
  git add src/main/java/com/kp/nsbh/agent/StatelessOrchestrator.java \
          src/test/java/com/kp/nsbh/agent/StatelessOrchestratorTest.java
  git commit -m "feat: StatelessOrchestrator — in-memory multi-round agent loop"
  ```

---

## Task 5: ApiKeyAuthFilter (TDD)

**Files:**
- Create: `src/main/java/com/kp/nsbh/api/ApiKeyAuthFilter.java`
- Create: `src/test/java/com/kp/nsbh/api/ApiKeyAuthFilterTest.java`

- [ ] **Step 1: Write the failing test**

  Create `src/test/java/com/kp/nsbh/api/ApiKeyAuthFilterTest.java`:

  ```java
  package com.kp.nsbh.api;

  import com.kp.nsbh.config.NsbhProperties;
  import org.junit.jupiter.api.Test;
  import org.springframework.test.web.reactive.server.WebTestClient;

  class ApiKeyAuthFilterTest {

      private WebTestClient client(String apiKey) {
          NsbhProperties properties = new NsbhProperties();
          properties.getApi().setKey(apiKey);
          ApiKeyAuthFilter filter = new ApiKeyAuthFilter(properties);
          return WebTestClient
                  .bindToWebHandler(exchange -> exchange.getResponse().setComplete())
                  .webFilter(filter)
                  .build();
      }

      @Test
      void validKeyAllowsV1Request() {
          client("secret").get().uri("/v1/models")
                  .header("Authorization", "Bearer secret")
                  .exchange()
                  .expectStatus().isOk();
      }

      @Test
      void invalidKeyReturns401WithBody() {
          client("secret").get().uri("/v1/models")
                  .header("Authorization", "Bearer wrong")
                  .exchange()
                  .expectStatus().isUnauthorized()
                  .expectBody()
                  .jsonPath("$.error").isEqualTo("invalid_api_key");
      }

      @Test
      void missingAuthHeaderReturns401() {
          client("secret").get().uri("/v1/models")
                  .exchange()
                  .expectStatus().isUnauthorized();
      }

      @Test
      void authHeaderWithoutBearerPrefixReturns401() {
          client("secret").get().uri("/v1/models")
                  .header("Authorization", "Token secret")
                  .exchange()
                  .expectStatus().isUnauthorized();
      }

      @Test
      void blankConfiguredKeySkipsAuthForV1() {
          client("").get().uri("/v1/models")
                  .exchange()
                  .expectStatus().isOk();
      }

      @Test
      void nonV1PathPassesThroughWithoutAuth() {
          client("secret").get().uri("/api/v1/conversations")
                  .exchange()
                  .expectStatus().isOk();
      }

      @Test
      void nullConfiguredKeySkipsAuth() {
          NsbhProperties properties = new NsbhProperties();
          properties.getApi().setKey(null);
          ApiKeyAuthFilter filter = new ApiKeyAuthFilter(properties);
          WebTestClient wc = WebTestClient
                  .bindToWebHandler(exchange -> exchange.getResponse().setComplete())
                  .webFilter(filter)
                  .build();
          wc.get().uri("/v1/models").exchange().expectStatus().isOk();
      }
  }
  ```

- [ ] **Step 2: Run test to verify it fails**

  ```bash
  mvn test -pl . -Dtest=ApiKeyAuthFilterTest -q 2>&1 | tail -10
  ```

  Expected: FAIL — `ApiKeyAuthFilter` does not exist yet.

- [ ] **Step 3: Implement `ApiKeyAuthFilter`**

  Create `src/main/java/com/kp/nsbh/api/ApiKeyAuthFilter.java`:

  ```java
  package com.kp.nsbh.api;

  import com.kp.nsbh.config.NsbhProperties;
  import java.nio.charset.StandardCharsets;
  import org.springframework.core.io.buffer.DataBuffer;
  import org.springframework.http.HttpStatus;
  import org.springframework.http.MediaType;
  import org.springframework.stereotype.Component;
  import org.springframework.web.server.ServerWebExchange;
  import org.springframework.web.server.WebFilter;
  import org.springframework.web.server.WebFilterChain;
  import reactor.core.publisher.Mono;

  @Component
  public class ApiKeyAuthFilter implements WebFilter {

      private final NsbhProperties properties;

      public ApiKeyAuthFilter(NsbhProperties properties) {
          this.properties = properties;
      }

      @Override
      public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
          String path = exchange.getRequest().getPath().value();
          if (!path.startsWith("/v1/")) {
              return chain.filter(exchange);
          }
          String configuredKey = properties.getApi().getKey();
          if (configuredKey == null || configuredKey.isBlank()) {
              return chain.filter(exchange);
          }
          String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
          if (authHeader != null && authHeader.startsWith("Bearer ")) {
              String key = authHeader.substring(7);
              if (key.equals(configuredKey)) {
                  return chain.filter(exchange);
              }
          }
          return writeUnauthorized(exchange);
      }

      private Mono<Void> writeUnauthorized(ServerWebExchange exchange) {
          exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
          exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
          byte[] body = "{\"error\":\"invalid_api_key\"}".getBytes(StandardCharsets.UTF_8);
          DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
          return exchange.getResponse().writeWith(Mono.just(buffer));
      }
  }
  ```

- [ ] **Step 4: Run test to verify it passes**

  ```bash
  mvn test -pl . -Dtest=ApiKeyAuthFilterTest -q 2>&1 | tail -10
  ```

  Expected: BUILD SUCCESS, 7 tests passed.

- [ ] **Step 5: Commit**

  ```bash
  git add src/main/java/com/kp/nsbh/api/ApiKeyAuthFilter.java \
          src/test/java/com/kp/nsbh/api/ApiKeyAuthFilterTest.java
  git commit -m "feat: ApiKeyAuthFilter — Bearer token auth for /v1/** paths"
  ```

---

## Task 6: OpenAiCompatController (TDD)

**Files:**
- Create: `src/main/java/com/kp/nsbh/openai/OpenAiCompatController.java`
- Create: `src/test/java/com/kp/nsbh/openai/OpenAiCompatControllerTest.java`
- Create: `src/test/java/com/kp/nsbh/openai/OpenAiCompatControllerSliceTest.java`

- [ ] **Step 1: Write the integration test**

  Create `src/test/java/com/kp/nsbh/openai/OpenAiCompatControllerTest.java`:

  ```java
  package com.kp.nsbh.openai;

  import static org.junit.jupiter.api.Assertions.assertFalse;
  import static org.junit.jupiter.api.Assertions.assertNotNull;
  import static org.junit.jupiter.api.Assertions.assertTrue;

  import java.util.List;
  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
  import org.springframework.boot.test.context.SpringBootTest;
  import org.springframework.http.MediaType;
  import org.springframework.test.context.TestPropertySource;
  import org.springframework.test.web.reactive.server.WebTestClient;

  @SpringBootTest
  @AutoConfigureWebTestClient
  @TestPropertySource(properties = {"nsbh.api.key="})
  class OpenAiCompatControllerTest {

      @Autowired
      private WebTestClient webTestClient;

      @Test
      void modelsEndpointReturnsModelList() {
          webTestClient.get().uri("/v1/models")
                  .exchange()
                  .expectStatus().isOk()
                  .expectBody()
                  .jsonPath("$.object").isEqualTo("list")
                  .jsonPath("$.data[0].id").isNotEmpty()
                  .jsonPath("$.data[0].object").isEqualTo("model")
                  .jsonPath("$.data[0].owned_by").isEqualTo("nsbh");
      }

      @Test
      void chatCompletionsNonStreamReturnsValidStructure() {
          webTestClient.post().uri("/v1/chat/completions")
                  .contentType(MediaType.APPLICATION_JSON)
                  .bodyValue("{\"model\":\"mock\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"stream\":false}")
                  .exchange()
                  .expectStatus().isOk()
                  .expectBody()
                  .jsonPath("$.object").isEqualTo("chat.completion")
                  .jsonPath("$.choices[0].message.role").isEqualTo("assistant")
                  .jsonPath("$.choices[0].message.content").isNotEmpty()
                  .jsonPath("$.choices[0].finish_reason").isEqualTo("stop")
                  .jsonPath("$.usage.prompt_tokens").isEqualTo(0);
      }

      @Test
      void chatCompletionsNonStreamWithNullModelUsesDefault() {
          webTestClient.post().uri("/v1/chat/completions")
                  .contentType(MediaType.APPLICATION_JSON)
                  .bodyValue("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")
                  .exchange()
                  .expectStatus().isOk()
                  .expectBody()
                  .jsonPath("$.model").isNotEmpty();
      }

      @Test
      void chatCompletionsEmptyMessagesReturns400() {
          webTestClient.post().uri("/v1/chat/completions")
                  .contentType(MediaType.APPLICATION_JSON)
                  .bodyValue("{\"model\":\"mock\",\"messages\":[]}")
                  .exchange()
                  .expectStatus().isBadRequest()
                  .expectBody()
                  .jsonPath("$.error.type").isEqualTo("invalid_request_error");
      }

      @Test
      void chatCompletionsStreamReturnsSseWithDone() {
          List<String> events = webTestClient.post().uri("/v1/chat/completions")
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.TEXT_EVENT_STREAM)
                  .bodyValue("{\"model\":\"mock\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"stream\":true}")
                  .exchange()
                  .expectStatus().isOk()
                  .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                  .returnResult(String.class)
                  .getResponseBody()
                  .collectList()
                  .block();

          assertNotNull(events);
          assertFalse(events.isEmpty());
          assertTrue(events.stream().anyMatch(e -> e.contains("[DONE]")));
          assertTrue(events.stream().anyMatch(e -> e.contains("chat.completion.chunk")));
      }

      @Test
      void chatCompletionsStreamEmptyMessagesReturnsErrorEvent() {
          List<String> events = webTestClient.post().uri("/v1/chat/completions")
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.TEXT_EVENT_STREAM)
                  .bodyValue("{\"model\":\"mock\",\"messages\":[]}")
                  .exchange()
                  .expectStatus().isOk()
                  .returnResult(String.class)
                  .getResponseBody()
                  .collectList()
                  .block();

          assertNotNull(events);
          assertTrue(events.stream().anyMatch(e -> e.contains("invalid_request_error")));
      }

      @Test
      void missingAuthReturns401WhenKeyConfigured() {
          webTestClient.get().uri("/v1/models")
                  .exchange()
                  .expectStatus().isOk(); // key is blank in this test context — no auth required
      }
  }
  ```

- [ ] **Step 2: Write the error-branch slice test**

  Create `src/test/java/com/kp/nsbh/openai/OpenAiCompatControllerSliceTest.java`:

  ```java
  package com.kp.nsbh.openai;

  import static org.junit.jupiter.api.Assertions.assertNotNull;
  import static org.junit.jupiter.api.Assertions.assertTrue;
  import static org.mockito.ArgumentMatchers.anyList;
  import static org.mockito.ArgumentMatchers.anyString;
  import static org.mockito.Mockito.when;

  import com.kp.nsbh.agent.StatelessOrchestrator;
  import com.kp.nsbh.api.ApiKeyAuthFilter;
  import com.kp.nsbh.config.NsbhProperties;
  import java.util.List;
  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
  import org.springframework.boot.test.mock.mockito.MockBean;
  import org.springframework.context.annotation.Import;
  import org.springframework.http.MediaType;
  import org.springframework.test.web.reactive.server.WebTestClient;
  import reactor.core.publisher.Flux;
  import reactor.core.publisher.Mono;

  @WebFluxTest(OpenAiCompatController.class)
  @Import({OpenAiCompatControllerSliceTest.TestConfig.class, ApiKeyAuthFilter.class})
  class OpenAiCompatControllerSliceTest {

      @org.springframework.boot.test.context.TestConfiguration
      static class TestConfig {
          @org.springframework.context.annotation.Bean
          NsbhProperties nsbhProperties() {
              return new NsbhProperties(); // blank api.key = no auth, mock provider
          }
      }

      @MockBean
      StatelessOrchestrator orchestrator;

      @Autowired
      WebTestClient webTestClient;

      @Test
      void nonStreamLlmErrorReturns502() {
          when(orchestrator.chat(anyList(), anyString()))
                  .thenReturn(Mono.error(new RuntimeException("LLM failed")));

          webTestClient.post().uri("/v1/chat/completions")
                  .contentType(MediaType.APPLICATION_JSON)
                  .bodyValue("{\"model\":\"mock\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")
                  .exchange()
                  .expectStatus().isEqualTo(502)
                  .expectBody()
                  .jsonPath("$.error.type").isEqualTo("server_error");
      }

      @Test
      void streamLlmErrorReturnsErrorEventInSse() {
          when(orchestrator.stream(anyList(), anyString()))
                  .thenReturn(Flux.error(new RuntimeException("stream failed")));

          List<String> events = webTestClient.post().uri("/v1/chat/completions")
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.TEXT_EVENT_STREAM)
                  .bodyValue("{\"model\":\"mock\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"stream\":true}")
                  .exchange()
                  .expectStatus().isOk()
                  .returnResult(String.class)
                  .getResponseBody()
                  .collectList()
                  .block();

          assertNotNull(events);
          assertTrue(events.stream().anyMatch(e -> e.contains("server_error")));
      }

      @Test
      void nonStreamWithNullExceptionMessageUsesDefaultMessage() {
          when(orchestrator.chat(anyList(), anyString()))
                  .thenReturn(Mono.error(new RuntimeException((String) null)));

          webTestClient.post().uri("/v1/chat/completions")
                  .contentType(MediaType.APPLICATION_JSON)
                  .bodyValue("{\"model\":\"mock\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")
                  .exchange()
                  .expectStatus().isEqualTo(502)
                  .expectBody()
                  .jsonPath("$.error.type").isEqualTo("server_error");
      }
  }
  ```

- [ ] **Step 3: Run tests to verify they fail**

  ```bash
  mvn test -pl . -Dtest="OpenAiCompatControllerTest,OpenAiCompatControllerSliceTest" -q 2>&1 | tail -15
  ```

  Expected: FAIL — `OpenAiCompatController` does not exist yet.

- [ ] **Step 4: Implement `OpenAiCompatController`**

  Create `src/main/java/com/kp/nsbh/openai/OpenAiCompatController.java`:

  ```java
  package com.kp.nsbh.openai;

  import com.fasterxml.jackson.databind.ObjectMapper;
  import com.kp.nsbh.agent.StatelessOrchestrator;
  import com.kp.nsbh.config.NsbhProperties;
  import java.time.Instant;
  import java.util.LinkedHashMap;
  import java.util.List;
  import java.util.Map;
  import java.util.UUID;
  import org.springframework.http.HttpStatus;
  import org.springframework.http.MediaType;
  import org.springframework.http.ResponseEntity;
  import org.springframework.http.codec.ServerSentEvent;
  import org.springframework.web.bind.annotation.GetMapping;
  import org.springframework.web.bind.annotation.PostMapping;
  import org.springframework.web.bind.annotation.RequestBody;
  import org.springframework.web.bind.annotation.RequestMapping;
  import org.springframework.web.bind.annotation.RestController;
  import reactor.core.publisher.Flux;
  import reactor.core.publisher.Mono;

  @RestController
  @RequestMapping("/v1")
  public class OpenAiCompatController {

      private final StatelessOrchestrator orchestrator;
      private final NsbhProperties properties;
      private final ObjectMapper objectMapper;

      public OpenAiCompatController(StatelessOrchestrator orchestrator,
                                    NsbhProperties properties,
                                    ObjectMapper objectMapper) {
          this.orchestrator = orchestrator;
          this.properties = properties;
          this.objectMapper = objectMapper;
      }

      @GetMapping("/models")
      public Mono<Map<String, Object>> models() {
          String modelId = properties.getLlm().getModelDefault();
          Map<String, Object> model = Map.of("id", modelId, "object", "model", "owned_by", "nsbh");
          return Mono.just(Map.of("object", "list", "data", List.of(model)));
      }

      @PostMapping(value = "/chat/completions",
                   consumes = MediaType.APPLICATION_JSON_VALUE,
                   produces = MediaType.APPLICATION_JSON_VALUE)
      public Mono<ResponseEntity<Object>> chatCompletions(@RequestBody OpenAiChatRequest request) {
          if (request.messages() == null || request.messages().isEmpty()) {
              return Mono.just(ResponseEntity.badRequest()
                      .<Object>body(openAiError("messages cannot be empty", "invalid_request_error")));
          }
          String model = request.model() != null
                  ? request.model()
                  : properties.getLlm().getModelDefault();
          return orchestrator.chat(request.messages(), model)
                  .map(text -> ResponseEntity.ok().<Object>body(buildChatResponse(text, model)))
                  .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                          .<Object>body(openAiError(
                                  e.getMessage() != null ? e.getMessage() : "LLM error",
                                  "server_error"))));
      }

      @PostMapping(value = "/chat/completions",
                   consumes = MediaType.APPLICATION_JSON_VALUE,
                   produces = MediaType.TEXT_EVENT_STREAM_VALUE)
      public Flux<ServerSentEvent<String>> chatCompletionsStream(@RequestBody OpenAiChatRequest request) {
          if (request.messages() == null || request.messages().isEmpty()) {
              return Flux.just(ServerSentEvent.<String>builder()
                      .data(toJson(openAiError("messages cannot be empty", "invalid_request_error")))
                      .build());
          }
          String id = "chatcmpl-" + UUID.randomUUID();
          long created = Instant.now().getEpochSecond();
          String model = request.model() != null
                  ? request.model()
                  : properties.getLlm().getModelDefault();

          Flux<ServerSentEvent<String>> firstChunk = Flux.just(
                  sseChunk(id, created, model, new OpenAiChatChunk.Delta("assistant", null), null));

          Flux<ServerSentEvent<String>> contentChunks = orchestrator.stream(request.messages(), model)
                  .map(token -> sseChunk(id, created, model,
                          new OpenAiChatChunk.Delta(null, token), null));

          Flux<ServerSentEvent<String>> stopChunk = Flux.just(
                  sseChunk(id, created, model, new OpenAiChatChunk.Delta(null, null), "stop"));

          Flux<ServerSentEvent<String>> done = Flux.just(
                  ServerSentEvent.<String>builder().data("[DONE]").build());

          return firstChunk.concatWith(contentChunks).concatWith(stopChunk).concatWith(done)
                  .onErrorResume(e -> Flux.just(ServerSentEvent.<String>builder()
                          .data(toJson(openAiError(
                                  e.getMessage() != null ? e.getMessage() : "stream error",
                                  "server_error")))
                          .build()));
      }

      private ServerSentEvent<String> sseChunk(String id, long created, String model,
                                                OpenAiChatChunk.Delta delta, String finishReason) {
          OpenAiChatChunk chunk = new OpenAiChatChunk(
                  id, "chat.completion.chunk", created, model,
                  List.of(new OpenAiChatChunk.ChunkChoice(0, delta, finishReason)));
          return ServerSentEvent.<String>builder().data(toJson(chunk)).build();
      }

      private OpenAiChatResponse buildChatResponse(String text, String model) {
          return new OpenAiChatResponse(
                  "chatcmpl-" + UUID.randomUUID(),
                  "chat.completion",
                  Instant.now().getEpochSecond(),
                  model,
                  List.of(new OpenAiChatResponse.Choice(
                          0,
                          new OpenAiChatResponse.Message("assistant", text),
                          "stop")),
                  new OpenAiChatResponse.Usage(0, 0, 0));
      }

      private Map<String, Object> openAiError(String message, String type) {
          Map<String, Object> detail = new LinkedHashMap<>();
          detail.put("message", message);
          detail.put("type", type);
          detail.put("code", null);
          return Map.of("error", detail);
      }

      private String toJson(Object obj) {
          try {
              return objectMapper.writeValueAsString(obj);
          } catch (Exception e) {
              return "{}";
          }
      }
  }
  ```

- [ ] **Step 5: Run tests to verify they pass**

  ```bash
  mvn test -pl . -Dtest="OpenAiCompatControllerTest,OpenAiCompatControllerSliceTest" -q 2>&1 | tail -15
  ```

  Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 6: Run the full test suite with coverage check**

  ```bash
  mvn verify -q 2>&1 | tail -20
  ```

  Expected: BUILD SUCCESS. JaCoCo must report ≥ 80% branch coverage. If coverage is below 80%, read `target/site/jacoco/index.html` to find uncovered branches and add targeted tests before proceeding.

- [ ] **Step 7: Commit**

  ```bash
  git add src/main/java/com/kp/nsbh/openai/ \
          src/test/java/com/kp/nsbh/openai/
  git commit -m "feat: OpenAiCompatController — GET /v1/models + POST /v1/chat/completions"
  ```

---

## Task 7: Auth integration test (API key configured scenario)

**Files:**
- No new files — one more `@SpringBootTest` context with `nsbh.api.key=test-secret`

This task verifies the auth filter works end-to-end when a key is configured.

- [ ] **Step 1: Add the auth scenario test**

  Create `src/test/java/com/kp/nsbh/openai/OpenAiCompatControllerAuthTest.java`:

  ```java
  package com.kp.nsbh.openai;

  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
  import org.springframework.boot.test.context.SpringBootTest;
  import org.springframework.http.MediaType;
  import org.springframework.test.context.TestPropertySource;
  import org.springframework.test.web.reactive.server.WebTestClient;

  @SpringBootTest
  @AutoConfigureWebTestClient
  @TestPropertySource(properties = {"nsbh.api.key=test-secret"})
  class OpenAiCompatControllerAuthTest {

      @Autowired
      private WebTestClient webTestClient;

      @Test
      void missingAuthHeaderReturns401() {
          webTestClient.get().uri("/v1/models")
                  .exchange()
                  .expectStatus().isUnauthorized()
                  .expectBody()
                  .jsonPath("$.error").isEqualTo("invalid_api_key");
      }

      @Test
      void invalidKeyReturns401() {
          webTestClient.get().uri("/v1/models")
                  .header("Authorization", "Bearer wrong-key")
                  .exchange()
                  .expectStatus().isUnauthorized();
      }

      @Test
      void validKeyAllowsAccess() {
          webTestClient.get().uri("/v1/models")
                  .header("Authorization", "Bearer test-secret")
                  .exchange()
                  .expectStatus().isOk()
                  .expectBody()
                  .jsonPath("$.object").isEqualTo("list");
      }

      @Test
      void validKeyAllowsChatCompletions() {
          webTestClient.post().uri("/v1/chat/completions")
                  .header("Authorization", "Bearer test-secret")
                  .contentType(MediaType.APPLICATION_JSON)
                  .bodyValue("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")
                  .exchange()
                  .expectStatus().isOk();
      }
  }
  ```

- [ ] **Step 2: Run auth test**

  ```bash
  mvn test -pl . -Dtest=OpenAiCompatControllerAuthTest -q 2>&1 | tail -10
  ```

  Expected: BUILD SUCCESS, 4 tests passed.

- [ ] **Step 3: Run full verification**

  ```bash
  mvn verify 2>&1 | tail -30
  ```

  Expected: BUILD SUCCESS. Confirm JaCoCo branch coverage ≥ 80%.

  If coverage is below 80%, open `target/site/jacoco/index.html` or read `target/site/jacoco/jacoco.xml` to find uncovered branches. Common gaps:
  - `StatelessOrchestrator.mapRole` — ensure all 4 role values are tested
  - `ApiKeyAuthFilter` — all path branches tested
  - `OpenAiCompatController.toJson` exception path — hard to trigger; acceptable if overall coverage remains ≥ 80%

- [ ] **Step 4: Commit and finish**

  ```bash
  git add src/test/java/com/kp/nsbh/openai/OpenAiCompatControllerAuthTest.java
  git commit -m "test: OpenAI compat API key auth integration test"
  ```

---

## Spec Coverage Self-Review

| Spec requirement | Task that covers it |
|---|---|
| `ApiKeyAuthFilter` — Bearer token, `/v1/**` only | Task 5 |
| Blank `api.key` → skip validation | Task 5 (test: blankConfiguredKeySkipsAuth) |
| `nsbh.api.key` config field | Task 1 |
| `StatelessOrchestrator.chat()` + loop logic | Task 4 |
| `StatelessOrchestrator.stream()` | Task 4 |
| `LlmClient` new stateless overloads | Task 3 |
| `GET /v1/models` → single-item list | Task 6 |
| `POST /v1/chat/completions` non-stream JSON | Task 6 |
| `POST /v1/chat/completions` stream SSE + `[DONE]` | Task 6 |
| Error format `{"error":{"message":"...","type":"...","code":null}}` | Task 6 |
| 400 on empty messages | Task 6 |
| 502 on LLM error | Task 6 (slice test) |
| ≥ 80% branch coverage | Task 6 step 6 + Task 7 step 3 |
| Auth integration (key configured) | Task 7 |

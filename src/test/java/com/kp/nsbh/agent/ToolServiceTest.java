package com.kp.nsbh.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.tools.NsbhTool;
import com.kp.nsbh.tools.Tool;
import com.kp.nsbh.tools.ToolCallReason;
import com.kp.nsbh.tools.ToolCallStatus;
import com.kp.nsbh.tools.ToolExecutionResult;
import com.kp.nsbh.tools.ToolRegistry;
import com.kp.nsbh.tools.ToolService;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class ToolServiceTest {

    @Test
    void shouldRejectToolNotInAllowlist() {
        NsbhProperties properties = new NsbhProperties();
        properties.getTools().setAllowed(List.of());

        ToolService service = new ToolService(new ToolRegistry(List.of(new FastTimeTool())), properties);
        ToolExecutionResult result = service.execute("conv-1", "time", "{}", "c1").block();

        assertEquals(ToolCallStatus.REJECTED, result.status());
        assertEquals(ToolCallReason.NOT_ALLOWED, result.reason());
    }

    @Test
    void shouldRejectWhenPermissionMissing() {
        NsbhProperties properties = new NsbhProperties();
        properties.getTools().setAllowed(List.of("time"));
        properties.getPermissions().setGranted(List.of());

        ToolService service = new ToolService(new ToolRegistry(List.of(new PermissionedTimeTool())), properties);
        ToolExecutionResult result = service.execute("conv-1", "time", "{}", "c-missing-perm").block();

        assertEquals(ToolCallStatus.REJECTED, result.status());
        assertEquals(ToolCallReason.PERMISSION_MISSING, result.reason());
    }

    @Test
    void shouldFailWhenToolTimesOut() {
        NsbhProperties properties = new NsbhProperties();
        properties.getTools().setAllowed(List.of("time"));
        properties.getTools().setTimeoutMs(20);

        ToolService service = new ToolService(new ToolRegistry(List.of(new SlowTimeTool())), properties);
        ToolExecutionResult result = service.execute("conv-1", "time", "{}", "c2").block();

        assertEquals(ToolCallStatus.FAILED, result.status());
        assertEquals(ToolCallReason.TIMEOUT, result.reason());
    }

    @Test
    void shouldRejectWhenToolNotRegistered() {
        NsbhProperties properties = new NsbhProperties();
        properties.getTools().setAllowed(List.of("missing"));

        ToolService service = new ToolService(new ToolRegistry(List.of(new FastTimeTool())), properties);
        ToolExecutionResult result = service.execute("conv-1", "missing", "{}", "c3").block();

        assertEquals(ToolCallStatus.REJECTED, result.status());
        assertEquals(ToolCallReason.NOT_REGISTERED, result.reason());
    }

    @Test
    void shouldRejectWhenInputTooLarge() {
        NsbhProperties properties = new NsbhProperties();
        properties.getTools().setAllowed(List.of("time"));
        properties.getTools().setMaxInputBytes(2);

        ToolService service = new ToolService(new ToolRegistry(List.of(new FastTimeTool())), properties);
        ToolExecutionResult result = service.execute("conv-1", "time", "{\"x\":1}", "c4").block();

        assertEquals(ToolCallStatus.REJECTED, result.status());
        assertEquals(ToolCallReason.INPUT_TOO_LARGE, result.reason());
    }

    @Test
    void shouldFailWhenOutputTooLarge() {
        NsbhProperties properties = new NsbhProperties();
        properties.getTools().setAllowed(List.of("time"));
        properties.getTools().setMaxOutputBytes(2);

        ToolService service = new ToolService(new ToolRegistry(List.of(new LargeOutputTool())), properties);
        ToolExecutionResult result = service.execute("conv-1", "time", "{}", "c5").block();

        assertEquals(ToolCallStatus.FAILED, result.status());
        assertEquals(ToolCallReason.OUTPUT_TOO_LARGE, result.reason());
    }

    @Test
    void shouldFailWhenInterrupted() {
        NsbhProperties properties = new NsbhProperties();
        properties.getTools().setAllowed(List.of("time"));

        ToolService service = new ToolService(new ToolRegistry(List.of(new InterruptedTool())), properties);
        ToolExecutionResult result = service.execute("conv-1", "time", "{}", "c6").block();

        assertEquals(ToolCallStatus.FAILED, result.status());
        assertEquals(ToolCallReason.INTERRUPTED, result.reason());
    }

    @Test
    void shouldFailWhenToolThrows() {
        NsbhProperties properties = new NsbhProperties();
        properties.getTools().setAllowed(List.of("time"));

        ToolService service = new ToolService(new ToolRegistry(List.of(new ErrorTool())), properties);
        ToolExecutionResult result = service.execute("conv-1", "time", "{}", "c7").block();

        assertEquals(ToolCallStatus.FAILED, result.status());
        assertEquals(ToolCallReason.EXECUTION_ERROR, result.reason());
    }

    @NsbhTool(name = "time", description = "fast", schema = "{}")
    static class FastTimeTool implements Tool {
        @Override
        public Mono<String> execute(String inputJson) {
            return Mono.just("2026-01-01T00:00:00Z");
        }
    }

    @NsbhTool(name = "time", description = "slow", schema = "{}")
    static class SlowTimeTool implements Tool {
        @Override
        public Mono<String> execute(String inputJson) {
            return Mono.delay(java.time.Duration.ofMillis(200)).thenReturn("late");
        }
    }

    @NsbhTool(name = "time", description = "perm", schema = "{}", requiredPermissions = {"NET_HTTP"})
    static class PermissionedTimeTool implements Tool {
        @Override
        public Mono<String> execute(String inputJson) {
            return Mono.just("ok");
        }
    }

    @NsbhTool(name = "time", description = "large", schema = "{}")
    static class LargeOutputTool implements Tool {
        @Override
        public Mono<String> execute(String inputJson) {
            return Mono.just("tool-output");
        }
    }

    @NsbhTool(name = "time", description = "interrupted", schema = "{}")
    static class InterruptedTool implements Tool {
        @Override
        public Mono<String> execute(String inputJson) {
            return Mono.error(new InterruptedException("stop"));
        }
    }

    @NsbhTool(name = "time", description = "error", schema = "{}")
    static class ErrorTool implements Tool {
        @Override
        public Mono<String> execute(String inputJson) {
            return Mono.error(new IllegalStateException("boom"));
        }
    }
}

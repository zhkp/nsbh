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

class ToolServiceTest {

    @Test
    void shouldRejectToolNotInAllowlist() {
        NsbhProperties properties = new NsbhProperties();
        properties.getTools().setAllowed(List.of());

        ToolService service = new ToolService(new ToolRegistry(List.of(new FastTimeTool())), properties);
        ToolExecutionResult result = service.execute("conv-1", "time", "{}", "c1");

        assertEquals(ToolCallStatus.REJECTED, result.status());
        assertEquals(ToolCallReason.NOT_ALLOWED, result.reason());
    }

    @Test
    void shouldRejectWhenPermissionMissing() {
        NsbhProperties properties = new NsbhProperties();
        properties.getTools().setAllowed(List.of("time"));
        properties.getPermissions().setGranted(List.of());

        ToolService service = new ToolService(new ToolRegistry(List.of(new PermissionedTimeTool())), properties);
        ToolExecutionResult result = service.execute("conv-1", "time", "{}", "c-missing-perm");

        assertEquals(ToolCallStatus.REJECTED, result.status());
        assertEquals(ToolCallReason.PERMISSION_MISSING, result.reason());
    }

    @Test
    void shouldFailWhenToolTimesOut() {
        NsbhProperties properties = new NsbhProperties();
        properties.getTools().setAllowed(List.of("time"));
        properties.getTools().setTimeoutMs(20);

        ToolService service = new ToolService(new ToolRegistry(List.of(new SlowTimeTool())), properties);
        ToolExecutionResult result = service.execute("conv-1", "time", "{}", "c2");

        assertEquals(ToolCallStatus.FAILED, result.status());
        assertEquals(ToolCallReason.TIMEOUT, result.reason());
    }

    @NsbhTool(name = "time", description = "fast", schema = "{}")
    static class FastTimeTool implements Tool {
        @Override
        public String execute(String inputJson) {
            return "2026-01-01T00:00:00Z";
        }
    }

    @NsbhTool(name = "time", description = "slow", schema = "{}")
    static class SlowTimeTool implements Tool {
        @Override
        public String execute(String inputJson) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "late";
        }
    }

    @NsbhTool(name = "time", description = "perm", schema = "{}", requiredPermissions = {"NET_HTTP"})
    static class PermissionedTimeTool implements Tool {
        @Override
        public String execute(String inputJson) {
            return "ok";
        }
    }
}

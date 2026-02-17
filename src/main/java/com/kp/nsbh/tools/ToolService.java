package com.kp.nsbh.tools;

import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.logging.JsonLogFormatter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class ToolService {
    private static final Logger TOOL_AUDIT = LoggerFactory.getLogger("TOOL_AUDIT");

    private final ToolRegistry toolRegistry;
    private final NsbhProperties properties;

    public ToolService(ToolRegistry toolRegistry, NsbhProperties properties) {
        this.toolRegistry = toolRegistry;
        this.properties = properties;
    }

    public ToolExecutionResult execute(String conversationId, String toolName, String inputJson, String toolCallId) {
        Instant started = Instant.now();

        ToolMetadata metadata = toolRegistry.findMetadata(toolName);
        if (metadata == null) {
            return audited(conversationId, toolName, toolCallId, started, ToolCallStatus.REJECTED,
                    ToolCallReason.NOT_REGISTERED, "Tool is not registered");
        }

        if (!properties.getTools().getAllowed().contains(toolName)) {
            return audited(conversationId, toolName, toolCallId, started, ToolCallStatus.REJECTED,
                    ToolCallReason.NOT_ALLOWED, "Tool is not in tools.allowed");
        }

        String missingPermissions = metadata.requiredPermissions().stream()
                .filter(permission -> !properties.getPermissions().getGranted().contains(permission))
                .collect(Collectors.joining(","));
        if (!missingPermissions.isEmpty()) {
            return audited(conversationId, toolName, toolCallId, started, ToolCallStatus.REJECTED,
                    ToolCallReason.PERMISSION_MISSING, "Missing permissions: " + missingPermissions);
        }

        Tool tool = toolRegistry.findTool(toolName);
        if (tool == null) {
            return audited(conversationId, toolName, toolCallId, started, ToolCallStatus.REJECTED,
                    ToolCallReason.NOT_REGISTERED, "Tool is not registered");
        }

        if (sizeOf(inputJson) > properties.getTools().getMaxInputBytes()) {
            return audited(conversationId, toolName, toolCallId, started, ToolCallStatus.REJECTED,
                    ToolCallReason.INPUT_TOO_LARGE, "Tool input too large");
        }

        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> tool.execute(inputJson));
            String output = future.get(properties.getTools().getTimeoutMs(), TimeUnit.MILLISECONDS);
            if (sizeOf(output) > properties.getTools().getMaxOutputBytes()) {
                return audited(conversationId, toolName, toolCallId, started, ToolCallStatus.FAILED,
                        ToolCallReason.OUTPUT_TOO_LARGE, "Tool output too large");
            }
            return audited(conversationId, toolName, toolCallId, started, ToolCallStatus.SUCCESS, ToolCallReason.NONE, output);
        } catch (TimeoutException e) {
            return audited(conversationId, toolName, toolCallId, started, ToolCallStatus.FAILED, ToolCallReason.TIMEOUT,
                    "Tool timed out");
        } catch (ExecutionException e) {
            return audited(conversationId, toolName, toolCallId, started, ToolCallStatus.FAILED,
                    ToolCallReason.EXECUTION_ERROR, "Tool failed: " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return audited(conversationId, toolName, toolCallId, started, ToolCallStatus.FAILED,
                    ToolCallReason.INTERRUPTED, "Tool interrupted");
        }
    }

    private ToolExecutionResult audited(String conversationId, String toolName, String toolCallId,
                                        Instant started, ToolCallStatus status, ToolCallReason reason, String result) {
        long duration = Duration.between(started, Instant.now()).toMillis();
        String requestId = MDC.get("requestId");
        TOOL_AUDIT.info(JsonLogFormatter.json(JsonLogFormatter.fields(
                "requestId", requestId,
                "conversationId", conversationId,
                "toolName", toolName,
                "status", status.name(),
                "reason", reason.name(),
                "durationMs", duration
        )));
        return new ToolExecutionResult(toolName, status, reason, result, toolCallId);
    }

    private static int sizeOf(String value) {
        if (value == null) {
            return 0;
        }
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}

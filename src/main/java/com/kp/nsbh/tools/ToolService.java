package com.kp.nsbh.tools;

import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.logging.JsonLogFormatter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ToolService {
    private static final Logger TOOL_AUDIT = LoggerFactory.getLogger("TOOL_AUDIT");

    private final ToolRegistry toolRegistry;
    private final NsbhProperties properties;

    public ToolService(ToolRegistry toolRegistry, NsbhProperties properties) {
        this.toolRegistry = toolRegistry;
        this.properties = properties;
    }

    public Mono<ToolExecutionResult> execute(String conversationId, String toolName, String inputJson, String toolCallId) {
        Instant started = Instant.now();
        return Mono.deferContextual(ctx -> {
            String requestId = ctx.getOrDefault("requestId", MDC.get("requestId"));
            return executeInternal(conversationId, toolName, inputJson, toolCallId, started, requestId);
        });
    }

    private Mono<ToolExecutionResult> executeInternal(String conversationId,
                                                      String toolName,
                                                      String inputJson,
                                                      String toolCallId,
                                                      Instant started,
                                                      String requestId) {
        ToolMetadata metadata = toolRegistry.findMetadata(toolName);
        if (metadata == null) {
            return Mono.just(audited(conversationId, toolName, toolCallId, started, ToolCallStatus.REJECTED,
                    ToolCallReason.NOT_REGISTERED, "Tool is not registered", requestId));
        }

        if (!properties.getTools().getAllowed().contains(toolName)) {
            return Mono.just(audited(conversationId, toolName, toolCallId, started, ToolCallStatus.REJECTED,
                    ToolCallReason.NOT_ALLOWED, "Tool is not in tools.allowed", requestId));
        }

        String missingPermissions = metadata.requiredPermissions().stream()
                .filter(permission -> !properties.getPermissions().getGranted().contains(permission))
                .collect(Collectors.joining(","));
        if (!missingPermissions.isEmpty()) {
            return Mono.just(audited(conversationId, toolName, toolCallId, started, ToolCallStatus.REJECTED,
                    ToolCallReason.PERMISSION_MISSING, "Missing permissions: " + missingPermissions, requestId));
        }

        Tool tool = toolRegistry.findTool(toolName);
        if (tool == null) {
            return Mono.just(audited(conversationId, toolName, toolCallId, started, ToolCallStatus.REJECTED,
                    ToolCallReason.NOT_REGISTERED, "Tool is not registered", requestId));
        }

        if (sizeOf(inputJson) > properties.getTools().getMaxInputBytes()) {
            return Mono.just(audited(conversationId, toolName, toolCallId, started, ToolCallStatus.REJECTED,
                    ToolCallReason.INPUT_TOO_LARGE, "Tool input too large", requestId));
        }

        return tool.execute(inputJson)
                .timeout(Duration.ofMillis(properties.getTools().getTimeoutMs()))
                .map(output -> {
                    if (sizeOf(output) > properties.getTools().getMaxOutputBytes()) {
                        return audited(conversationId, toolName, toolCallId, started, ToolCallStatus.FAILED,
                                ToolCallReason.OUTPUT_TOO_LARGE, "Tool output too large", requestId);
                    }
                    return audited(conversationId, toolName, toolCallId, started, ToolCallStatus.SUCCESS,
                            ToolCallReason.NONE, output, requestId);
                })
                .onErrorResume(TimeoutException.class, e -> Mono.just(audited(
                        conversationId,
                        toolName,
                        toolCallId,
                        started,
                        ToolCallStatus.FAILED,
                        ToolCallReason.TIMEOUT,
                        "Tool timed out",
                        requestId
                )))
                .onErrorResume(InterruptedException.class, e -> {
                    Thread.currentThread().interrupt();
                    return Mono.just(audited(
                            conversationId,
                            toolName,
                            toolCallId,
                            started,
                            ToolCallStatus.FAILED,
                            ToolCallReason.INTERRUPTED,
                            "Tool interrupted",
                            requestId
                    ));
                })
                .onErrorResume(e -> Mono.just(audited(
                        conversationId,
                        toolName,
                        toolCallId,
                        started,
                        ToolCallStatus.FAILED,
                        ToolCallReason.EXECUTION_ERROR,
                        "Tool failed: " + safeMessage(e),
                        requestId
                )));
    }

    private ToolExecutionResult audited(String conversationId, String toolName, String toolCallId,
                                        Instant started, ToolCallStatus status, ToolCallReason reason, String result,
                                        String requestId) {
        long duration = Duration.between(started, Instant.now()).toMillis();
        withRequestId(requestId, () -> TOOL_AUDIT.info(JsonLogFormatter.json(JsonLogFormatter.fields(
                "requestId", requestId,
                "conversationId", conversationId,
                "toolName", toolName,
                "status", status.name(),
                "reason", reason.name(),
                "durationMs", duration
        ))));
        return new ToolExecutionResult(toolName, status, reason, result, toolCallId);
    }

    private void withRequestId(String requestId, Runnable runnable) {
        String old = MDC.get("requestId");
        if (requestId != null && !requestId.isBlank()) {
            MDC.put("requestId", requestId);
        }
        try {
            runnable.run();
        } finally {
            if (old == null || old.isBlank()) {
                MDC.remove("requestId");
            } else {
                MDC.put("requestId", old);
            }
        }
    }

    private String safeMessage(Throwable throwable) {
        String msg = throwable.getMessage();
        if (msg == null || msg.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return msg;
    }

    private static int sizeOf(String value) {
        if (value == null) {
            return 0;
        }
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}

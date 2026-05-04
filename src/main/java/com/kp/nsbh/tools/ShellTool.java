package com.kp.nsbh.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kp.nsbh.config.NsbhProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@NsbhTool(
        name = "shell",
        description = "Execute an allowlisted shell command in the workspace",
        schema = "{\"type\":\"object\",\"properties\":{\"command\":{\"type\":\"string\"}},\"required\":[\"command\"]}",
        requiredPermissions = {"SHELL_EXEC"}
)
public class ShellTool implements Tool {

    private static final List<String> BLOCKLIST = List.of(
            "sudo", "rm -rf /", "chmod 777 /", ">/dev/", "mkfs");

    private final ObjectMapper objectMapper;
    private final WorkspaceService workspaceService;
    private final NsbhProperties properties;

    public ShellTool(ObjectMapper objectMapper,
                     WorkspaceService workspaceService,
                     NsbhProperties properties) {
        this.objectMapper = objectMapper;
        this.workspaceService = workspaceService;
        this.properties = properties;
    }

    @Override
    public Mono<String> execute(String inputJson) {
        return Mono.fromCallable(() -> executeBlocking(inputJson))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String executeBlocking(String inputJson) throws IOException, InterruptedException {
        JsonNode root = objectMapper.readTree(inputJson == null ? "{}" : inputJson);
        String command = root.path("command").asText("").trim();

        if (command.isBlank()) {
            throw new IllegalArgumentException("command is required");
        }

        String firstToken = command.split("\\s+")[0];
        List<String> allowed = properties.getTools().getShell().getAllowedPrefixes();
        if (!allowed.contains(firstToken)) {
            throw new IllegalArgumentException("command not in allowlist: " + firstToken);
        }

        for (String blocked : BLOCKLIST) {
            if (command.contains(blocked)) {
                throw new IllegalArgumentException("blocked command pattern");
            }
        }

        Path workspaceRoot = workspaceService.getRoot();
        int maxOutputBytes = properties.getTools().getShell().getMaxOutputBytes();
        long timeoutMs = properties.getTools().getTimeoutMs();

        ProcessBuilder pb = new ProcessBuilder("sh", "-c", command)
                .directory(workspaceRoot.toFile())
                .redirectErrorStream(true);

        Process process = pb.start();

        CompletableFuture<byte[]> outputFuture = CompletableFuture.supplyAsync(() -> {
            try (InputStream in = process.getInputStream()) {
                return in.readAllBytes();
            } catch (IOException e) {
                return new byte[0];
            }
        });

        boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("command timed out");
        }

        byte[] rawOutput;
        try {
            rawOutput = outputFuture.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            rawOutput = new byte[0];
        }

        if (rawOutput.length > maxOutputBytes) {
            rawOutput = Arrays.copyOf(rawOutput, maxOutputBytes);
        }
        return new String(rawOutput, StandardCharsets.UTF_8);
    }
}

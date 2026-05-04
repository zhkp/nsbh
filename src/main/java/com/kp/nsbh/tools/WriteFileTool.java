package com.kp.nsbh.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@NsbhTool(
        name = "write_file",
        description = "Write a file into the workspace (creates parent directories)",
        schema = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},\"content\":{\"type\":\"string\"}},\"required\":[\"path\",\"content\"]}",
        requiredPermissions = {"WORKSPACE_WRITE"}
)
public class WriteFileTool implements Tool {

    private final ObjectMapper objectMapper;
    private final WorkspaceService workspaceService;

    public WriteFileTool(WorkspaceService workspaceService, ObjectMapper objectMapper) {
        this.workspaceService = workspaceService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<String> execute(String inputJson) {
        return Mono.fromCallable(() -> {
            JsonNode root = objectMapper.readTree(inputJson == null ? "{}" : inputJson);
            String userPath = root.path("path").asText("").trim();
            String content = root.path("content").asText("");
            if (userPath.isBlank()) {
                throw new IllegalArgumentException("path is required");
            }
            Path resolved = workspaceService.resolve(userPath);
            workspaceService.assertSafe(resolved);

            Files.createDirectories(resolved.getParent());
            Files.writeString(resolved, content);

            int byteCount = content.getBytes(StandardCharsets.UTF_8).length;
            return objectMapper.writeValueAsString(Map.of("path", userPath, "bytes", byteCount));
        }).subscribeOn(Schedulers.boundedElastic());
    }
}

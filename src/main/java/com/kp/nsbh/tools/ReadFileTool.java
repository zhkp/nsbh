package com.kp.nsbh.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kp.nsbh.config.NsbhProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@NsbhTool(
        name = "read_file",
        description = "Read a file from the workspace",
        schema = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}",
        requiredPermissions = {"WORKSPACE_READ"}
)
public class ReadFileTool implements Tool {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkspaceService workspaceService;
    private final NsbhProperties properties;

    public ReadFileTool(WorkspaceService workspaceService, NsbhProperties properties) {
        this.workspaceService = workspaceService;
        this.properties = properties;
    }

    @Override
    public Mono<String> execute(String inputJson) {
        return Mono.fromCallable(() -> {
            JsonNode root = objectMapper.readTree(inputJson == null ? "{}" : inputJson);
            String userPath = root.path("path").asText("").trim();
            if (userPath.isBlank()) {
                throw new IllegalArgumentException("path is required");
            }
            Path resolved = workspaceService.resolve(userPath);
            workspaceService.assertSafe(resolved);

            String content = Files.readString(resolved);
            int maxBytes = properties.getTools().getMaxOutputBytes();
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > maxBytes) {
                content = new String(bytes, 0, maxBytes, StandardCharsets.UTF_8) + "\n[truncated]";
            }
            return content;
        }).subscribeOn(Schedulers.boundedElastic());
    }
}

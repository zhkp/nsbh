package com.kp.nsbh.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@NsbhTool(
        name = "list_files",
        description = "List files in a workspace directory",
        schema = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},\"recursive\":{\"type\":\"boolean\"}},\"required\":[\"path\"]}",
        requiredPermissions = {"WORKSPACE_READ"}
)
public class ListFilesTool implements Tool {

    private static final int MAX_ENTRIES = 500;

    private final ObjectMapper objectMapper;
    private final WorkspaceService workspaceService;

    public ListFilesTool(ObjectMapper objectMapper, WorkspaceService workspaceService) {
        this.objectMapper = objectMapper;
        this.workspaceService = workspaceService;
    }

    @Override
    public Mono<String> execute(String inputJson) {
        return Mono.fromCallable(() -> {
            JsonNode root = objectMapper.readTree(inputJson == null ? "{}" : inputJson);
            String userPath = root.path("path").asText(".");
            boolean recursive = root.path("recursive").asBoolean(false);

            Path resolved = workspaceService.resolve(userPath);
            workspaceService.assertSafe(resolved);

            int maxDepth = recursive ? Integer.MAX_VALUE : 1;
            Path wsRoot = workspaceService.getRoot();
            List<String> entries;
            try (Stream<Path> stream = Files.walk(resolved, maxDepth)) {
                entries = stream
                        .filter(p -> !p.equals(resolved))
                        .map(p -> wsRoot.relativize(p).toString())
                        .limit(MAX_ENTRIES)
                        .toList();
            }
            return objectMapper.writeValueAsString(entries);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}

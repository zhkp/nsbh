package com.kp.nsbh.tools;

import com.kp.nsbh.config.NsbhProperties;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceService {

    private final Path workspaceRoot;

    public WorkspaceService(NsbhProperties properties) {
        this.workspaceRoot = Path.of(properties.getWorkspace().getRoot())
                .toAbsolutePath().normalize();
    }

    public Path resolve(String userPath) {
        return workspaceRoot.resolve(userPath).normalize();
    }

    public void assertSafe(Path resolved) {
        if (!resolved.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("Path escapes workspace: " + resolved);
        }
    }

    public Path getRoot() {
        return workspaceRoot;
    }
}

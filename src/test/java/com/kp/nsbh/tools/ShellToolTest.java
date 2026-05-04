package com.kp.nsbh.tools;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kp.nsbh.config.NsbhProperties;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShellToolTest {

    private ShellTool tool(Path root, List<String> allowedPrefixes) {
        NsbhProperties props = new NsbhProperties();
        props.getWorkspace().setRoot(root.toString());
        props.getTools().getShell().setAllowedPrefixes(allowedPrefixes);
        props.getTools().getShell().setMaxOutputBytes(65536);
        props.getTools().setTimeoutMs(5000);
        WorkspaceService ws = new WorkspaceService(props);
        return new ShellTool(new ObjectMapper(), ws, props);
    }

    @Test
    void allowlistedCommandRuns(@TempDir Path root) {
        String result = tool(root, List.of("echo"))
                .execute("{\"command\":\"echo hello\"}").block();
        assertTrue(result.contains("hello"));
    }

    @Test
    void commandNotInAllowlistThrows(@TempDir Path root) {
        assertThrows(IllegalArgumentException.class,
                () -> tool(root, List.of("ls"))
                        .execute("{\"command\":\"cat /etc/passwd\"}").block());
    }

    @Test
    void blockedPatternThrows(@TempDir Path root) {
        assertThrows(IllegalArgumentException.class,
                () -> tool(root, List.of("sudo"))
                        .execute("{\"command\":\"sudo rm -rf /\"}").block());
    }

    @Test
    void emptyCommandThrows(@TempDir Path root) {
        assertThrows(IllegalArgumentException.class,
                () -> tool(root, List.of("echo"))
                        .execute("{\"command\":\"\"}").block());
    }

    @Test
    void outputTruncatedAtMaxBytes(@TempDir Path root) {
        NsbhProperties props = new NsbhProperties();
        props.getWorkspace().setRoot(root.toString());
        props.getTools().getShell().setAllowedPrefixes(List.of("echo"));
        props.getTools().getShell().setMaxOutputBytes(5);
        props.getTools().setTimeoutMs(5000);
        WorkspaceService ws = new WorkspaceService(props);
        ShellTool t = new ShellTool(new ObjectMapper(), ws, props);

        String result = t.execute("{\"command\":\"echo 0123456789\"}").block();
        assertTrue(result.length() <= 5);
    }
}

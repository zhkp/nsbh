package com.kp.nsbh.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kp.nsbh.config.NsbhProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReadFileToolTest {

    private ReadFileTool tool(Path root) {
        NsbhProperties props = new NsbhProperties();
        props.getWorkspace().setRoot(root.toString());
        return new ReadFileTool(new WorkspaceService(props), props);
    }

    @Test
    void readsExistingFile(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("hello.txt"), "hello world");
        String result = tool(root).execute("{\"path\":\"hello.txt\"}").block();
        assertEquals("hello world", result);
    }

    @Test
    void rejectsPathEscape(@TempDir Path root) {
        assertThrows(IllegalArgumentException.class,
                () -> tool(root).execute("{\"path\":\"../secret.txt\"}").block());
    }

    @Test
    void throwsWhenFileNotFound(@TempDir Path root) {
        assertThrows(Exception.class,
                () -> tool(root).execute("{\"path\":\"missing.txt\"}").block());
    }

    @Test
    void rejectsBlankPath(@TempDir Path root) {
        assertThrows(IllegalArgumentException.class,
                () -> tool(root).execute("{\"path\":\"\"}").block());
    }

    @Test
    void truncatesOversizedFile(@TempDir Path root) throws Exception {
        NsbhProperties props = new NsbhProperties();
        props.getWorkspace().setRoot(root.toString());
        props.getTools().setMaxOutputBytes(5);
        ReadFileTool t = new ReadFileTool(new WorkspaceService(props), props);
        Files.writeString(root.resolve("big.txt"), "0123456789");
        String result = t.execute("{\"path\":\"big.txt\"}").block();
        assertTrue(result.contains("[truncated]"));
    }

    @Test
    void nullInputJsonIsRejectedAsBlankPath(@TempDir Path root) {
        assertThrows(IllegalArgumentException.class,
                () -> tool(root).execute(null).block());
    }
}

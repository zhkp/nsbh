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
}

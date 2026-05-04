package com.kp.nsbh.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kp.nsbh.config.NsbhProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ListFilesToolTest {

    private ListFilesTool tool(Path root) {
        NsbhProperties props = new NsbhProperties();
        props.getWorkspace().setRoot(root.toString());
        return new ListFilesTool(new ObjectMapper(), new WorkspaceService(props));
    }

    @Test
    void nonRecursiveListShowsOnlyTopLevel(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("a.txt"), "a");
        Files.createDirectory(root.resolve("sub"));
        Files.writeString(root.resolve("sub/b.txt"), "b");

        String result = tool(root).execute("{\"path\":\".\"}").block();
        assertTrue(result.contains("a.txt"));
        assertFalse(result.contains("b.txt"));
    }

    @Test
    void recursiveListShowsNestedFiles(@TempDir Path root) throws Exception {
        Files.createDirectory(root.resolve("sub"));
        Files.writeString(root.resolve("sub/b.txt"), "b");

        String result = tool(root).execute("{\"path\":\".\",\"recursive\":true}").block();
        assertTrue(result.contains("b.txt"));
    }

    @Test
    void rejectsPathEscape(@TempDir Path root) {
        assertThrows(IllegalArgumentException.class,
                () -> tool(root).execute("{\"path\":\"../\"}").block());
    }

    @Test
    void nullInputJsonDefaultsToRoot(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("a.txt"), "a");
        String result = tool(root).execute(null).block();
        assertTrue(result.contains("a.txt"));
    }
}

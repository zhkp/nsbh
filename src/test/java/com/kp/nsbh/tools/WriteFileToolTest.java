package com.kp.nsbh.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kp.nsbh.config.NsbhProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WriteFileToolTest {

    private WriteFileTool tool(Path root) {
        NsbhProperties props = new NsbhProperties();
        props.getWorkspace().setRoot(root.toString());
        return new WriteFileTool(new WorkspaceService(props), new ObjectMapper());
    }

    @Test
    void writesFileAndReturnsBytes(@TempDir Path root) throws Exception {
        String result = tool(root).execute("{\"path\":\"out.txt\",\"content\":\"hello\"}").block();
        assertTrue(result.contains("bytes"));
        assertEquals("hello", Files.readString(root.resolve("out.txt")));
    }

    @Test
    void createsParentDirectories(@TempDir Path root) throws Exception {
        tool(root).execute("{\"path\":\"sub/dir/file.txt\",\"content\":\"x\"}").block();
        assertTrue(Files.exists(root.resolve("sub/dir/file.txt")));
    }

    @Test
    void rejectsPathEscape(@TempDir Path root) {
        assertThrows(IllegalArgumentException.class,
                () -> tool(root).execute("{\"path\":\"../evil.txt\",\"content\":\"x\"}").block());
    }

    @Test
    void rejectsBlankPath(@TempDir Path root) {
        assertThrows(IllegalArgumentException.class,
                () -> tool(root).execute("{\"path\":\"\",\"content\":\"x\"}").block());
    }

    @Test
    void returnsCorrectByteCount(@TempDir Path root) throws Exception {
        String result = tool(root).execute("{\"path\":\"f.txt\",\"content\":\"ab\"}").block();
        assertTrue(result.contains("\"bytes\":2"));
    }

    @Test
    void nullInputJsonIsRejectedAsBlankPath(@TempDir Path root) {
        assertThrows(IllegalArgumentException.class,
                () -> tool(root).execute(null).block());
    }
}

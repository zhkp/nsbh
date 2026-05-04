package com.kp.nsbh.tools;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kp.nsbh.config.NsbhProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceServiceTest {

    private WorkspaceService service(Path root) {
        NsbhProperties props = new NsbhProperties();
        props.getWorkspace().setRoot(root.toString());
        return new WorkspaceService(props);
    }

    @Test
    void safePathResolvesCorrectly(@TempDir Path root) {
        WorkspaceService ws = service(root);
        Path resolved = ws.resolve("foo/bar.txt");
        assertDoesNotThrow(() -> ws.assertSafe(resolved));
        assertEquals(root.resolve("foo/bar.txt").normalize(), resolved);
    }

    @Test
    void dotDotEscapeIsRejected(@TempDir Path root) {
        WorkspaceService ws = service(root);
        Path resolved = ws.resolve("../etc/passwd");
        assertThrows(IllegalArgumentException.class, () -> ws.assertSafe(resolved));
    }

    @Test
    void absolutePathOutsideWorkspaceIsRejected(@TempDir Path root) {
        WorkspaceService ws = service(root);
        Path resolved = ws.resolve("/etc/passwd");
        assertThrows(IllegalArgumentException.class, () -> ws.assertSafe(resolved));
    }

    @Test
    void getRootReturnsNormalizedRoot(@TempDir Path root) {
        WorkspaceService ws = service(root);
        assertEquals(root.normalize().toAbsolutePath(), ws.getRoot());
    }
}

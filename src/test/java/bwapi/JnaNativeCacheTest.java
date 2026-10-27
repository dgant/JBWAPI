package bwapi;

import com.sun.jna.Native;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardOpenOption.WRITE;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class JnaNativeCacheTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void reusesMatchingBytesAndSeparatesDifferentBinaries() throws Exception {
        Path root = temporary.newFolder().toPath().resolve("cache");
        byte[] binary = {1, 2, 3};
        Path library = JnaNativeCache.cache(root, "library", binary);
        Files.setLastModifiedTime(library, FileTime.fromMillis(100000));
        assertEquals(library, JnaNativeCache.cache(root, "library", binary));
        assertEquals(100000, Files.getLastModifiedTime(library).toMillis());
        Path updated = JnaNativeCache.cache(root, "library", new byte[] {4, 5, 6});
        assertNotEquals(library, updated);
        assertArrayEquals(binary, Files.readAllBytes(library));
    }

    @Test
    public void skipsBusyWriterThenRepairsCorruptionAndStaleStagingFile() throws Exception {
        Path root = temporary.newFolder().toPath().resolve("cache");
        byte[] binary = {1, 2, 3};
        Path library = JnaNativeCache.cache(root, "library", binary);
        Files.write(library, new byte[] {0, 0, 0});
        Path staging = library.getParent().resolve(".partial");
        Files.write(staging, new byte[] {9});
        try (FileChannel channel = FileChannel.open(library.getParent().resolve(".lock"), WRITE);
             FileLock lock = channel.lock()) {
            assertNull(JnaNativeCache.cache(root, "library", binary));
        }
        assertEquals(library, JnaNativeCache.cache(root, "library", binary));
        assertArrayEquals(binary, Files.readAllBytes(library));
        assertFalse(Files.exists(staging));
    }

    @Test
    public void rejectsSharedPosixCacheAndSymlinkedEntries() throws Exception {
        Path base = temporary.newFolder().toPath();
        assumeTrue(Files.getFileStore(base).supportsFileAttributeView("posix"));
        Path root = base.resolve("cache");
        Path library = JnaNativeCache.cache(root, "library", new byte[] {1});
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwxrwxrwx"));
        assertThrows(java.io.IOException.class, () -> JnaNativeCache.cache(root, "library", new byte[] {1}));
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        Path other = base.resolve("other");
        Files.write(other, new byte[] {2});
        Files.delete(library);
        Files.createSymbolicLink(library, other);
        assertThrows(java.io.IOException.class, () -> JnaNativeCache.cache(root, "library", new byte[] {1}));
        assertArrayEquals(new byte[] {2}, Files.readAllBytes(other));
    }

    // Fresh JVMs exercise native initialization, which cannot be reset between tests in one JVM.
    @Test
    public void clientLoadsAndReusesCacheAcrossProcessesAndRepairsBadBytes() throws Exception {
        Path base = temporary.newFolder().toPath();
        String first = finish(start(base));
        Path library = Paths.get(value(first, "native"));
        assertTrue(first, library.startsWith(base.resolve(JnaNativeCache.directoryName())));
        assertEquals("unset", value(first, "boot"));
        byte[] expected = Files.readAllBytes(library);
        FileTime timestamp = Files.getLastModifiedTime(library);
        assertEquals(library.toString(), value(finish(start(base)), "native"));
        assertEquals(timestamp, Files.getLastModifiedTime(library));
        Files.write(library, new byte[] {0});
        assertEquals(library.toString(), value(finish(start(base)), "native"));
        assertArrayEquals(expected, Files.readAllBytes(library));
    }

    @Test
    public void cacheFailureFallsBackToOrdinaryJnaLoading() throws Exception {
        Path base = temporary.newFolder().toPath();
        Files.write(base.resolve(JnaNativeCache.directoryName()), new byte[] {0});
        String result = finish(start(base));
        assertFalse(result, value(result, "native").contains(JnaNativeCache.directoryName()));
        assertEquals("unset", value(result, "boot"));
    }

    @Test
    public void respectsExplicitBootPathOptOutAndAlreadyLoadedJna() throws Exception {
        Path base = temporary.newFolder().toPath();
        String explicit = base.resolve("explicit").toString();
        assertEquals(explicit, value(finish(start(base, "-Djna.boot.library.path=" + explicit)), "boot"));
        assertFalse(Files.exists(base.resolve(JnaNativeCache.directoryName())));
        finish(start(base, "-Djbwapi.nativeCache=false"));
        assertFalse(Files.exists(base.resolve(JnaNativeCache.directoryName())));
        finish(start(base, "-Dprobe.preload=true"));
        assertFalse(Files.exists(base.resolve(JnaNativeCache.directoryName())));
    }

    @Test
    public void honorsNoUnpackRatherThanBypassingItWithCache() throws Exception {
        Path base = temporary.newFolder().toPath();
        // Populate a usable cache first, then request JNA's explicit no-extraction behavior.
        finish(start(base));
        Process child = start(base, "-Djna.nounpack=true");
        String output = output(child);
        assertNotEquals(output, 0, child.exitValue());
        assertTrue(output, output.contains("UnsatisfiedLinkError"));
    }

    @Test
    public void concurrentFirstLaunchesCanLoadCacheOrFallBack() throws Exception {
        Path base = temporary.newFolder().toPath();
        List<Process> children = new ArrayList<>();
        try {
            for (int i = 0; i < 3; ++i) children.add(start(base));
            for (Process child : children) finish(child);
            String result = finish(start(base));
            assertTrue(result, value(result, "native").contains(JnaNativeCache.directoryName()));
        } finally {
            for (Process child : children) child.destroyForcibly();
        }
    }

    private Process start(Path base, String... options) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        List<String> command = new ArrayList<>(Arrays.asList(
                Paths.get(System.getProperty("java.home"), "bin", executable).toString(),
                "-Xmx128m", "-XX:-UsePerfData", "-Djava.io.tmpdir=" + base, "-Djna.tmpdir=" + base));
        command.addAll(Arrays.asList(options));
        command.addAll(Arrays.asList("-cp", System.getProperty("surefire.test.class.path",
                System.getProperty("java.class.path")), Probe.class.getName()));
        ProcessBuilder builder = new ProcessBuilder(command).directory(base.toFile()).redirectErrorStream(true);
        for (String name : new String[] {"TEMP", "TMP", "TMPDIR"}) builder.environment().put(name, base.toString());
        return builder.start();
    }

    private String finish(Process child) throws Exception {
        String output = output(child);
        assertEquals(output, 0, child.exitValue());
        assertTrue(output, output.contains("pointer=4") || output.contains("pointer=8"));
        return output;
    }

    private String output(Process child) throws Exception {
        try {
            assertTrue("Native initialization timed out", child.waitFor(30, TimeUnit.SECONDS));
            try (InputStream input = child.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] bytes = new byte[4096];
                int count;
                while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
                return output.toString("UTF-8");
            }
        } finally {
            child.destroyForcibly();
        }
    }

    private String value(String output, String key) {
        for (String line : output.split("\\R")) {
            if (line.startsWith(key + "=")) return line.substring(key.length() + 1);
        }
        throw new AssertionError(output);
    }

    public static class Probe {
        public static void main(String[] args) {
            if (Boolean.getBoolean("probe.preload")) System.out.println("preload=" + Native.POINTER_SIZE);
            new Client(new BWClient(new DefaultBWListener()));
            System.out.println("pointer=" + Native.POINTER_SIZE);
            System.out.println("native=" + System.getProperty("jnidispatch.path"));
            System.out.println("boot=" + System.getProperty("jna.boot.library.path", "unset"));
        }
    }
}

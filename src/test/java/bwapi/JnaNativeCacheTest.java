package bwapi;

import com.sun.jna.Native;
import com.sun.jna.Platform;
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
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static java.nio.file.StandardOpenOption.WRITE;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Verifies native-library selection, cache reuse, and fallback without a BWAPI server.
 * Small byte arrays exercise cache publication and permission checks independently
 * of native loading. Subprocess tests load the real JNA library in a fresh JVM for
 * each scenario because native initialization cannot be reset within one JVM.
 * Bundled-DLL cases require Windows; POSIX permission cases require a POSIX file store.
 */
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
        assertEquals("unset", value(result, "bootName"));
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

    @Test
    public void bundledWindowsLibraryIsSelectedWithoutFlagsFromAnotherWorkingDirectory() throws Exception {
        assumeTrue(Platform.isWindows());
        Path distribution = temporary.newFolder("bot with spaces #").toPath();
        String classpath = bundledClasspath(distribution);
        Path base = temporary.newFolder("unrelated working directory").toPath();
        String result = finish(startOnClasspath(base, classpath));
        Path expected = distribution.resolve("jnidispatch-" + Platform.RESOURCE_PREFIX + ".dll");
        assertTrue(result, Files.isSameFile(expected, Paths.get(value(result, "native"))));
        assertEquals("unset", value(result, "boot"));
        assertEquals("unset", value(result, "bootName"));
        assertFalse(Files.exists(base.resolve(JnaNativeCache.directoryName())));
        // Both flags forbid cache extraction, but neither forbids loading an existing bundled DLL.
        result = finish(startOnClasspath(base, classpath, "-Djna.nounpack=true", "-Djbwapi.nativeCache=false"));
        assertTrue(result, Files.isSameFile(expected, Paths.get(value(result, "native"))));
        String explicit = base.resolve("explicit").toString();
        result = finish(startOnClasspath(base, classpath, "-Djna.boot.library.path=" + explicit));
        assertEquals(explicit, value(result, "boot"));
        assertNotEquals(expected.toString(), value(result, "native"));
    }

    @Test
    public void staleOrMissingBundledWindowsLibraryFallsBackToCache() throws Exception {
        assumeTrue(Platform.isWindows());
        Path distribution = temporary.newFolder().toPath();
        String classpath = bundledClasspath(distribution);
        Path base = temporary.newFolder().toPath();
        Path library = distribution.resolve("jnidispatch-" + Platform.RESOURCE_PREFIX + ".dll");
        Files.write(library, new byte[] {0});
        String result = finish(startOnClasspath(base, classpath));
        assertTrue(result, value(result, "native").contains(JnaNativeCache.directoryName()));
        assertArrayEquals(new byte[] {0}, Files.readAllBytes(library));
        Files.delete(library);
        assertEquals(value(result, "native"), value(finish(startOnClasspath(base, classpath)), "native"));
    }

    @Test
    public void bundledWindowsLibrariesAreIgnoredOnOtherPlatforms() throws Exception {
        assumeTrue(!Platform.isWindows());
        Path distribution = temporary.newFolder().toPath();
        String classpath = bundledClasspath(distribution);
        Path base = temporary.newFolder().toPath();
        String result = finish(startOnClasspath(base, classpath));
        assertTrue(result, value(result, "native").contains(JnaNativeCache.directoryName()));
        assertFalse(result, value(result, "native").contains(distribution.toString()));
    }

    /**
     * Builds a minimal distribution fixture. Loading the helper from bot.jar gives
     * it a real artifact location for finding adjacent DLLs; the rest of the classes
     * remain on the test classpath. All Windows architectures are included together.
     */
    private String bundledClasspath(Path distribution) throws Exception {
        Path jar = distribution.resolve("bot.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar));
             InputStream input = JnaNativeCache.class.getResourceAsStream("JnaNativeCache.class")) {
            output.putNextEntry(new JarEntry("bwapi/JnaNativeCache.class"));
            byte[] bytes = new byte[4096];
            int size;
            while ((size = input.read(bytes)) != -1) output.write(bytes, 0, size);
            output.closeEntry();
        }
        for (String arch : new String[] {"win32-x86", "win32-x86-64", "win32-aarch64"}) {
            String resource = "com/sun/jna/" + arch + "/jnidispatch.dll";
            Path library = distribution.resolve("jnidispatch-" + arch + ".dll");
            Files.createDirectories(library.getParent());
            try (InputStream input = Native.class.getResourceAsStream("/" + resource)) {
                Files.copy(input, library);
            }
        }
        return jar + System.getProperty("path.separator") + testClasspath();
    }

    private String testClasspath() {
        return System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    }

    private Process start(Path base, String... options) throws Exception {
        return startOnClasspath(base, testClasspath(), options);
    }

    // Use the test JVM's runtime and an isolated working/temp directory for each scenario.
    private Process startOnClasspath(Path base, String classpath, String... options) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        List<String> command = new ArrayList<>(Arrays.asList(
                Paths.get(System.getProperty("java.home"), "bin", executable).toString(),
                "-Xmx128m", "-XX:-UsePerfData", "-Djava.io.tmpdir=" + base, "-Djna.tmpdir=" + base));
        command.addAll(Arrays.asList(options));
        command.addAll(Arrays.asList("-cp", classpath, NativeLoadingProbe.class.getName()));
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

    /**
     * Child-JVM entry point: constructs a client without connecting to a game, then
     * reports the loaded library and boot-path setting for the parent's assertions.
     */
    public static class NativeLoadingProbe {
        public static void main(String[] args) {
            if (Boolean.getBoolean("probe.preload")) System.out.println("preload=" + Native.POINTER_SIZE);
            new Client(new BWClient(new DefaultBWListener()));
            System.out.println("pointer=" + Native.POINTER_SIZE);
            System.out.println("native=" + System.getProperty("jnidispatch.path"));
            System.out.println("boot=" + System.getProperty("jna.boot.library.path", "unset"));
            System.out.println("bootName=" + System.getProperty("jna.boot.library.name", "unset"));
        }
    }
}

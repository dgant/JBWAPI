package bwapi;

import com.sun.jna.Native;
import com.sun.jna.Platform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.*;

/** Loads a bundled or cached copy of JNA's native binary before ordinary extraction. */
final class JnaNativeCache {
    private static final String BOOT_PATH = "jna.boot.library.path";

    private JnaNativeCache() {
    }

    static synchronized void initialize() {
        String path;
        try {
            if (isConfigured()) return;
            String name = System.mapLibraryName("jnidispatch").replace(".dylib", ".jnilib");
            if (Platform.isAIX()) name = "libjnidispatch.a";
            String resource = "/com/sun/jna/" + Platform.RESOURCE_PREFIX + "/" + name;
            // A class literal does not initialize Native; use its loader's exact binary.
            byte[] binary;
            try (InputStream input = Native.class.getResourceAsStream(resource);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (input == null) return;
                byte[] buffer = new byte[32768];
                int size;
                while ((size = input.read(buffer)) != -1) output.write(buffer, 0, size);
                binary = output.toByteArray();
            }
            Path library = bundledLibrary(resource, binary);
            if (library == null && !Boolean.getBoolean("jna.nounpack")
                    && !"false".equalsIgnoreCase(System.getProperty("jbwapi.nativeCache"))) {
                Path base = Paths.get(System.getProperty("jna.tmpdir", System.getProperty("java.io.tmpdir")));
                library = cache(base.resolve(directoryName()), name, binary);
            }
            if (library == null || isConfigured()) return;
            path = library.getParent().toAbsolutePath().toString();
            System.setProperty(BOOT_PATH, path);
        } catch (IOException | NoSuchAlgorithmException | RuntimeException unavailable) {
            // An optional optimization must not prevent JNA's ordinary loading path.
            return;
        }
        try {
            // JNA itself falls back to ordinary extraction if this binary cannot be loaded.
            Class.forName(Native.class.getName(), true, Native.class.getClassLoader());
        } catch (ClassNotFoundException impossible) {
            throw new IllegalStateException(impossible);
        } finally {
            // Do not leave a process-wide path pointing at this class loader's JNA version.
            if (path.equals(System.getProperty(BOOT_PATH))) System.clearProperty(BOOT_PATH);
        }
    }

    private static boolean isConfigured() {
        if (Boolean.getBoolean("jna.loaded")) {
            return true;
        }
        for (String setting : new String[] {BOOT_PATH, "jna.boot.library.name",
                "jna.noclasspath", "jna.nosys", "jna.prefix", "jnidispatch.preserve"}) {
            if (System.getProperty(setting) != null) return true;
        }
        return false;
    }

    private static Path bundledLibrary(String resource, byte[] binary) {
        if (!Platform.isWindows()) return null;
        try {
            // Resolve beside the JAR (or wrapped EXE), never relative to a launcher's working directory.
            Path artifact = Paths.get(JnaNativeCache.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path library = artifact.resolveSibling("natives").resolve(resource.substring(1));
            // A replaced JAR must not accidentally load a DLL left over from another JNA version.
            return matches(library, binary) ? library : null;
        } catch (IOException | URISyntaxException | RuntimeException unavailable) {
            return null;
        }
    }

    static String directoryName() {
        return "jbwapi-jna-" + Integer.toHexString(System.getProperty("user.name").hashCode());
    }

    /** Returns null when another process is publishing this binary; callers can use JNA's fallback. */
    static Path cache(Path root, String name, byte[] binary) throws IOException, NoSuchAlgorithmException {
        privateDirectory(root);
        String hash = new BigInteger(1, MessageDigest.getInstance("SHA-256").digest(binary)).toString(16);
        Path directory = root.resolve(hash);
        createDirectory(directory);
        Path library = directory.resolve(name);
        if (matches(library, binary)) return library;
        try (FileChannel channel = FileChannel.open(directory.resolve(".lock"), CREATE, WRITE, NOFOLLOW_LINKS);
             FileLock lock = channel.tryLock()) {
            if (lock == null) return null;
            if (matches(library, binary)) return library;
            // The lock permits a fixed staging name, avoiding SecureRandom startup for temp names.
            Path staging = directory.resolve(".partial");
            try {
                Files.write(staging, binary, CREATE, TRUNCATE_EXISTING, WRITE, NOFOLLOW_LINKS);
                Files.move(staging, library, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(staging);
            }
            return library;
        } catch (OverlappingFileLockException busy) {
            return null;
        }
    }

    private static boolean matches(Path file, byte[] expected) throws IOException {
        if (!Files.exists(file, NOFOLLOW_LINKS)) return false;
        try (SeekableByteChannel channel = Files.newByteChannel(file, READ, NOFOLLOW_LINKS)) {
            if (channel.size() != expected.length) return false;
            ByteBuffer bytes = ByteBuffer.allocate(expected.length);
            while (bytes.hasRemaining()) {
                if (channel.read(bytes) < 0) return false;
            }
            return Arrays.equals(expected, bytes.array());
        }
    }

    private static boolean createDirectory(Path directory, FileAttribute<?>... attributes) throws IOException {
        try {
            Files.createDirectory(directory, attributes);
            return true;
        } catch (FileAlreadyExistsException exists) {
            if (!Files.isDirectory(directory, NOFOLLOW_LINKS)) throw exists;
            return false;
        }
    }

    /** Never load executable code from a cache writable by other users. */
    private static void privateDirectory(Path directory) throws IOException {
        UserPrincipal user = directory.getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByName(System.getProperty("user.name"));
        if (Files.getFileStore(directory.getParent()).supportsFileAttributeView("posix")) {
            createDirectory(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            PosixFileAttributes attributes = Files.readAttributes(directory, PosixFileAttributes.class, NOFOLLOW_LINKS);
            if (!attributes.owner().equals(user) || !EnumSet.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE).containsAll(attributes.permissions())) {
                throw new IOException("Native cache is not private");
            }
        } else {
            List<AclEntry> acl = Collections.singletonList(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                    .setPrincipal(user).setPermissions(EnumSet.allOf(AclEntryPermission.class))
                    .setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT).build());
            boolean created = createDirectory(directory, new FileAttribute<List<AclEntry>>() {
                public String name() { return "acl:acl"; }
                public List<AclEntry> value() { return acl; }
            });
            AclFileAttributeView attributes = Files.getFileAttributeView(directory, AclFileAttributeView.class, NOFOLLOW_LINKS);
            if (attributes == null) throw new IOException("Native cache has no ACL support");
            // Elevated Windows processes can otherwise create directories owned by Administrators.
            if (created) attributes.setOwner(user);
            if (!attributes.getOwner().equals(user)) throw new IOException("Native cache owner mismatch");
            for (AclEntry entry : attributes.getAcl()) {
                if (entry.type() == AclEntryType.ALLOW && !entry.principal().equals(user)) {
                    throw new IOException("Native cache is not private");
                }
            }
        }
    }
}

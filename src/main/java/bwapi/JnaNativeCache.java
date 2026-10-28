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

/**
 * Prepares JNA's native bridge before the first BWAPI connection attempt.
 * JNA needs this platform-specific library to access shared memory; extracting it
 * from the JAR on every launch adds work before the client can connect to a server.
 *
 * <p>On Windows, first checks the architecture-named DLL beside the containing JAR
 * against the embedded library for the running JVM's architecture. Otherwise,
 * reuses or creates a verified copy in a private, per-user temporary cache.
 * Unavailable bundles or caches leave JNA's ordinary loading path available.
 * Explicit JNA loading settings and an already initialized JNA take precedence.
 */
final class JnaNativeCache {
    private static final String BOOT_PATH = "jna.boot.library.path";
    private static final String BOOT_NAME = "jna.boot.library.name";

    private JnaNativeCache() {
    }

    /**
     * Call before constructing a platform connector or otherwise initializing JNA.
     */
    static synchronized void initialize() {
        String path;
        String bootName;
        try {
            if (isConfigured()) {
                return;
            }
            String name = System.mapLibraryName("jnidispatch").replace(".dylib", ".jnilib");
            if (Platform.isAIX()) {
                name = "libjnidispatch.a";
            }
            byte[] binary = readEmbeddedLibrary(name);
            if (binary == null) {
                return;
            }
            Path library = bundledLibrary(binary);
            bootName = library == null ? null : "jnidispatch-" + Platform.RESOURCE_PREFIX;
            if (library == null && !Boolean.getBoolean("jna.nounpack")
                    && !"false".equalsIgnoreCase(System.getProperty("jbwapi.nativeCache"))) {
                Path base = Paths.get(System.getProperty("jna.tmpdir", System.getProperty("java.io.tmpdir")));
                library = cache(base.resolve(directoryName()), name, binary);
            }
            if (library == null || isConfigured()) {
                return;
            }
            path = library.getParent().toAbsolutePath().toString();
        } catch (IOException | NoSuchAlgorithmException | RuntimeException unavailable) {
            // An optional optimization must not prevent JNA's ordinary loading path.
            return;
        }
        loadPreparedLibrary(path, bootName);
    }

    private static byte[] readEmbeddedLibrary(String name) throws IOException {
        String resource = "/com/sun/jna/" + Platform.RESOURCE_PREFIX + "/" + name;
        // A class literal does not initialize Native; use its loader's exact binary.
        try (InputStream input = Native.class.getResourceAsStream(resource);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) {
                return null;
            }
            byte[] buffer = new byte[32768];
            int size;
            while ((size = input.read(buffer)) != -1) {
                output.write(buffer, 0, size);
            }
            return output.toByteArray();
        }
    }

    private static void loadPreparedLibrary(String path, String bootName) {
        try {
            try {
                System.setProperty(BOOT_PATH, path);
                if (bootName != null) {
                    System.setProperty(BOOT_NAME, bootName);
                }
            } catch (RuntimeException unavailable) {
                return;
            }
            // JNA itself falls back to ordinary extraction if this binary cannot be loaded.
            Class.forName(Native.class.getName(), true, Native.class.getClassLoader());
        } catch (ClassNotFoundException impossible) {
            throw new IllegalStateException("Cannot initialize JNA: its class loader could not find "
                    + "com.sun.jna.Native, which is required to load the native library. Ensure JNA is included "
                    + "in the bot's runtime classpath and is visible to its class loader.", impossible);
        } finally {
            // Do not leave a process-wide path pointing at this class loader's JNA version.
            if (path.equals(System.getProperty(BOOT_PATH))) {
                System.clearProperty(BOOT_PATH);
            }
            if (bootName != null && bootName.equals(System.getProperty(BOOT_NAME))) {
                System.clearProperty(BOOT_NAME);
            }
        }
    }

    private static boolean isConfigured() {
        if (Boolean.getBoolean("jna.loaded")) {
            return true;
        }
        for (String setting : new String[] {BOOT_PATH, BOOT_NAME,
                "jna.noclasspath", "jna.nosys", "jna.prefix", "jnidispatch.preserve"}) {
            if (System.getProperty(setting) != null) {
                return true;
            }
        }
        return false;
    }

    private static Path bundledLibrary(byte[] binary) {
        if (!Platform.isWindows()) {
            return null;
        }
        try {
            // Resolve beside the JAR (or wrapped EXE), never relative to a launcher's working directory.
            Path artifact = Paths.get(JnaNativeCache.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path library = artifact.resolveSibling("jnidispatch-" + Platform.RESOURCE_PREFIX + ".dll");
            // A replaced JAR must not accidentally load a DLL left over from another JNA version.
            return matches(library, binary) ? library : null;
        } catch (IOException | URISyntaxException | RuntimeException unavailable) {
            return null;
        }
    }

    // The hash keeps usernames out of paths; ownership and permissions are checked separately.
    static String directoryName() {
        return "jbwapi-jna-" + Integer.toHexString(System.getProperty("user.name").hashCode());
    }

    /**
     * Returns a verified library at {@code root/<SHA-256 of binary>/name}.
     * Content-based directories separate JNA versions and platform binaries. A process
     * lock and atomic move prevent readers from seeing partially written libraries.
     * Returns null if another writer holds the lock, allowing startup to fall back
     * immediately. An unsafe or inaccessible cache throws IOException to the caller.
     */
    static Path cache(Path root, String name, byte[] binary) throws IOException, NoSuchAlgorithmException {
        privateDirectory(root);
        String hash = new BigInteger(1, MessageDigest.getInstance("SHA-256").digest(binary)).toString(16);
        Path directory = root.resolve(hash);
        createDirectory(directory);
        Path library = directory.resolve(name);
        if (matches(library, binary)) {
            return library;
        }
        try (FileChannel channel = FileChannel.open(directory.resolve(".lock"), CREATE, WRITE, NOFOLLOW_LINKS);
             FileLock lock = channel.tryLock()) {
            if (lock == null) {
                return null;
            }
            if (matches(library, binary)) {
                return library;
            }
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
        if (!Files.exists(file, NOFOLLOW_LINKS)) {
            return false;
        }
        try (SeekableByteChannel channel = Files.newByteChannel(file, READ, NOFOLLOW_LINKS)) {
            if (channel.size() != expected.length) {
                return false;
            }
            ByteBuffer bytes = ByteBuffer.allocate(expected.length);
            while (bytes.hasRemaining()) {
                if (channel.read(bytes) < 0) {
                    return false;
                }
            }
            return Arrays.equals(expected, bytes.array());
        }
    }

    private static boolean createDirectory(Path directory, FileAttribute<?>... attributes) throws IOException {
        try {
            Files.createDirectory(directory, attributes);
            return true;
        } catch (FileAlreadyExistsException exists) {
            if (!Files.isDirectory(directory, NOFOLLOW_LINKS)) {
                throw new IOException("Cannot create native-library cache directory '" + directory
                        + "': expected a directory, but the existing path is not an accessible directory "
                        + "(symbolic links are not accepted). "
                        + "Set -Djna.tmpdir to another writable directory so JBWAPI can create its cache there.", exists);
            }
            return false;
        }
    }

    /**
     * Creates or validates a cache root accessible only to its owning user.
     * On POSIX, this parent directory protects all descendants from other users;
     * on Windows, descendants also inherit its user-only ACL. Existing roots with
     * unsuitable ownership or permissions are rejected before any cache file is used.
     */
    private static void privateDirectory(Path directory) throws IOException {
        UserPrincipal user = directory.getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByName(System.getProperty("user.name"));
        if (Files.getFileStore(directory.getParent()).supportsFileAttributeView("posix")) {
            createDirectory(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            PosixFileAttributes attributes = Files.readAttributes(directory, PosixFileAttributes.class, NOFOLLOW_LINKS);
            if (!attributes.owner().equals(user) || !EnumSet.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE).containsAll(attributes.permissions())) {
                throw new IOException("Cannot use native-library cache '" + directory + "': expected owner '"
                        + user + "' with no group or other permissions, but found owner '" + attributes.owner()
                        + "' and permissions '" + PosixFilePermissions.toString(attributes.permissions())
                        + "'. Set -Djna.tmpdir to another writable directory owned by this user "
                        + "so JBWAPI can create a private cache.");
            }
        } else {
            List<AclEntry> acl = Collections.singletonList(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                    .setPrincipal(user).setPermissions(EnumSet.allOf(AclEntryPermission.class))
                    .setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT).build());
            boolean created = createDirectory(directory, new FileAttribute<List<AclEntry>>() {
                @Override
                public String name() {
                    return "acl:acl";
                }

                @Override
                public List<AclEntry> value() {
                    return acl;
                }
            });
            AclFileAttributeView attributes = Files.getFileAttributeView(directory, AclFileAttributeView.class, NOFOLLOW_LINKS);
            if (attributes == null) {
                throw new IOException("Cannot use native-library cache '" + directory
                        + "': Windows access-control lists (ACLs) are unavailable, so JBWAPI cannot verify "
                        + "that only user '" + user + "' can access it. Set -Djna.tmpdir to a writable local "
                        + "directory on a filesystem that supports Windows ACLs.");
            }
            // Elevated Windows processes can otherwise create directories owned by Administrators.
            if (created) {
                attributes.setOwner(user);
            }
            UserPrincipal owner = attributes.getOwner();
            if (!owner.equals(user)) {
                throw new IOException("Cannot use native-library cache '" + directory + "': expected owner '"
                        + user + "', but the directory is owned by '" + owner
                        + "'. Set -Djna.tmpdir to another writable directory owned by this user "
                        + "so JBWAPI can create a private cache.");
            }
            for (AclEntry entry : attributes.getAcl()) {
                if (entry.type() == AclEntryType.ALLOW && !entry.principal().equals(user)) {
                    throw new IOException("Cannot use native-library cache '" + directory
                            + "': access must be limited to user '" + user + "', but its ACL also allows '"
                            + entry.principal() + "'. Set -Djna.tmpdir to another writable directory owned "
                            + "by this user so JBWAPI can create a private cache.");
                }
            }
        }
    }
}

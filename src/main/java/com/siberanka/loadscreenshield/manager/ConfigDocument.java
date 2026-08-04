package com.siberanka.loadscreenshield.manager;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Loads and rewrites config.yml without trusting Bukkit's forgiving static loader.
 * Rewrites are atomic and content-addressed backups prevent a persistent invalid
 * file from creating a new backup on every reload attempt.
 */
final class ConfigDocument {

    static final long MAX_CONFIG_BYTES = 1_048_576L;
    static final int MAX_BACKUPS = 10;
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path path;
    private final boolean existed;
    private final String originalContent;
    private final YamlConfiguration configuration;
    private final String loadProblem;

    private ConfigDocument(
            Path path,
            boolean existed,
            String originalContent,
            YamlConfiguration configuration,
            String loadProblem
    ) {
        this.path = path;
        this.existed = existed;
        this.originalContent = originalContent;
        this.configuration = configuration;
        this.loadProblem = loadProblem;
    }

    static ConfigDocument load(Path path, boolean existedBeforeLoad) throws IOException {
        Path absolutePath = path.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(absolutePath)) {
            throw new IOException("Refusing to read config.yml through a symbolic link");
        }
        boolean exists = Files.isRegularFile(absolutePath);
        if (!exists) {
            return new ConfigDocument(absolutePath, false, "", new YamlConfiguration(), "config.yml does not exist");
        }

        long size = Files.size(absolutePath);
        if (size > MAX_CONFIG_BYTES) {
            return new ConfigDocument(
                    absolutePath,
                    existedBeforeLoad,
                    null,
                    new YamlConfiguration(),
                    "config.yml exceeds the " + MAX_CONFIG_BYTES + " byte safety limit"
            );
        }

        String content = Files.readString(absolutePath);
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.options().parseComments(true);
        try {
            loaded.loadFromString(content);
            return new ConfigDocument(absolutePath, existedBeforeLoad, content, loaded, null);
        } catch (InvalidConfigurationException exception) {
            return new ConfigDocument(
                    absolutePath,
                    existedBeforeLoad,
                    content,
                    new YamlConfiguration(),
                    "config.yml is not valid YAML: " + exception.getMessage()
            );
        }
    }

    YamlConfiguration configuration() {
        return configuration;
    }

    String loadProblem() {
        return loadProblem;
    }

    Set<String> unknownKeys(YamlConfiguration template) {
        Set<String> unknown = new TreeSet<>(configuration.getKeys(true));
        unknown.removeAll(template.getKeys(true));
        return Set.copyOf(unknown);
    }

    Set<String> missingKeys(YamlConfiguration template) {
        Set<String> missing = new TreeSet<>();
        for (String key : template.getKeys(true)) {
            if (!configuration.contains(key)) {
                missing.add(key);
            }
        }
        return Set.copyOf(missing);
    }

    RewriteResult rewriteCanonical(String templateText, Map<String, Object> values) throws IOException {
        YamlConfiguration canonical = parseTemplate(templateText);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            canonical.set(entry.getKey(), entry.getValue());
        }
        String canonicalContent = canonical.saveToString();
        if (canonicalContent.equals(originalContent)) {
            return new RewriteResult(false, null);
        }

        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IOException("config.yml has no parent directory");
        }
        Files.createDirectories(parent);

        Path temporary = Files.createTempFile(parent, "config-", ".tmp");
        Path backup = null;
        try {
            writeAndSync(temporary, canonicalContent);
            if (existed && Files.isRegularFile(path)) {
                backup = createBackupOnce(parent);
            }
            atomicReplace(temporary, path);
            return new RewriteResult(true, backup);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static YamlConfiguration parseTemplate(String templateText) {
        YamlConfiguration template = new YamlConfiguration();
        template.options().parseComments(true);
        try {
            template.loadFromString(templateText);
        } catch (InvalidConfigurationException exception) {
            throw new IllegalStateException("Bundled config.yml is invalid", exception);
        }
        return template;
    }

    private Path createBackupOnce(Path parent) throws IOException {
        String digest = digest(path).substring(0, 16);
        String glob = "config.yml.backup-*-" + digest + ".yml";
        try (DirectoryStream<Path> backups = Files.newDirectoryStream(parent, glob)) {
            for (Path existingBackup : backups) {
                pruneBackups(parent, existingBackup);
                return existingBackup;
            }
        }

        String timestamp = BACKUP_TIME.format(LocalDateTime.now());
        for (int attempt = 0; attempt < 100; attempt++) {
            String suffix = attempt == 0 ? "" : "-" + attempt;
            Path backup = parent.resolve(
                    "config.yml.backup-" + timestamp + suffix + "-" + digest + ".yml"
            );
            try {
                Path created = Files.copy(path, backup);
                pruneBackups(parent, created);
                return created;
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
                // The timestamp can collide during repeated reloads; try a bounded suffix.
            }
        }
        throw new IOException("Could not allocate a unique config.yml backup name");
    }

    private static void pruneBackups(Path parent, Path retained) throws IOException {
        List<Path> backups = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent, "config.yml.backup-*.yml")) {
            stream.forEach(backups::add);
        }
        backups.sort(Comparator.comparingLong(ConfigDocument::lastModified).reversed());
        int retainedCount = backups.size();
        for (int index = backups.size() - 1; index >= 0 && retainedCount > MAX_BACKUPS; index--) {
            Path candidate = backups.get(index);
            if (!candidate.equals(retained) && Files.deleteIfExists(candidate)) {
                retainedCount--;
            }
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return Long.MIN_VALUE;
        }
    }

    private static String digest(Path source) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        try (InputStream input = Files.newInputStream(source)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void writeAndSync(Path target, String content) throws IOException {
        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(
                target,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
        )) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static void atomicReplace(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    record RewriteResult(boolean changed, Path backup) {
    }
}

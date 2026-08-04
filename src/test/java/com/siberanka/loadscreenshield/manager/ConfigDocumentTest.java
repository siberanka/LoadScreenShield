package com.siberanka.loadscreenshield.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigDocumentTest {

    Path temporaryDirectory;

    @BeforeEach
    void createTemporaryDirectory() throws IOException {
        temporaryDirectory = Path.of("target", "test-work", UUID.randomUUID().toString());
        Files.createDirectories(temporaryDirectory);
    }

    @AfterEach
    void deleteTemporaryDirectory() throws IOException {
        if (!Files.exists(temporaryDirectory)) {
            return;
        }
        try (var paths = Files.walk(temporaryDirectory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void backsUpRepairsDocumentsAndRemovesUnknownEntriesWithoutARewriteLoop() throws IOException {
        Path config = temporaryDirectory.resolve("config.yml");
        String original = """
                lang: tr
                timeout-seconds: 99999
                shield-block-type: DIAMOND_SWORD
                activation-mode: nonsense
                title:
                  enabled: maybe
                protection:
                  cancel-movement: false
                  meaningless-entry: true
                unknown-root: remove-me
                """;
        Files.writeString(config, original);

        String template = bundledConfig();
        ConfigDocument document = ConfigDocument.load(config, true);
        YamlConfiguration defaults = ConfigDocument.parseTemplate(template);
        assertEquals(Set.of("protection.meaningless-entry", "unknown-root"), document.unknownKeys(defaults));
        for (String path : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(path)) {
                assertFalse(defaults.getComments(path).isEmpty(), "Missing documentation for " + path);
            }
        }

        List<String> warnings = new ArrayList<>();
        ConfigManager.Snapshot validated = validate(document.configuration(), warnings);
        ConfigDocument.RewriteResult first = document.rewriteCanonical(
                template,
                ConfigManager.canonicalValues(validated)
        );

        assertTrue(first.changed());
        assertNotNull(first.backup());
        assertEquals(original, Files.readString(first.backup()));
        assertFalse(warnings.isEmpty());

        String normalizedText = Files.readString(config);
        YamlConfiguration normalized = new YamlConfiguration();
        try {
            normalized.loadFromString(normalizedText);
        } catch (org.bukkit.configuration.InvalidConfigurationException exception) {
            throw new AssertionError(exception);
        }
        assertEquals(3, normalized.getInt("schema-version"));
        assertEquals("tr", normalized.getString("lang"));
        assertEquals(1800, normalized.getInt("timeout-seconds"));
        assertEquals("BLACK_WOOL", normalized.getString("shield-block-type"));
        assertEquals("JOIN", normalized.getString("activation-mode"));
        assertTrue(normalized.getBoolean("title.enabled"));
        assertFalse(normalized.getBoolean("protection.cancel-movement"));
        assertFalse(normalized.contains("unknown-root"));
        assertFalse(normalized.contains("protection.meaningless-entry"));
        assertTrue(normalizedText.contains("Available values: JOIN, RESOURCE_PACK_STATUS."));
        assertTrue(normalizedText.contains("Available values: true, false."));
        assertTrue(normalizedText.indexOf("schema-version:") < normalizedText.indexOf("lang:"));
        assertTrue(normalizedText.indexOf("lang:") < normalizedText.indexOf("prefix:"));
        assertTrue(normalizedText.indexOf("prefix:") < normalizedText.indexOf("activation-mode:"));
        assertTrue(normalizedText.indexOf("activation-mode:") < normalizedText.indexOf("timeout-seconds:"));

        ConfigDocument secondDocument = ConfigDocument.load(config, true);
        ConfigManager.Snapshot secondValues = validate(secondDocument.configuration(), new ArrayList<>());
        ConfigDocument.RewriteResult second = secondDocument.rewriteCanonical(
                template,
                ConfigManager.canonicalValues(secondValues)
        );
        assertFalse(second.changed());
        assertNull(second.backup());
        assertEquals(1L, backupCount());

        Files.writeString(config, original);
        ConfigDocument repeatedBadDocument = ConfigDocument.load(config, true);
        ConfigManager.Snapshot repeatedValues = validate(
                repeatedBadDocument.configuration(), new ArrayList<>()
        );
        ConfigDocument.RewriteResult repeated = repeatedBadDocument.rewriteCanonical(
                template,
                ConfigManager.canonicalValues(repeatedValues)
        );
        assertTrue(repeated.changed());
        assertEquals(first.backup(), repeated.backup());
        assertEquals(1L, backupCount());
    }

    @Test
    void invalidYamlIsBackedUpBeforeDocumentedDefaultsReplaceIt() throws IOException {
        Path config = temporaryDirectory.resolve("config.yml");
        String invalid = "title: [not-closed";
        Files.writeString(config, invalid);

        ConfigDocument document = ConfigDocument.load(config, true);
        assertNotNull(document.loadProblem());
        ConfigManager.Snapshot validated = validate(document.configuration(), new ArrayList<>());
        ConfigDocument.RewriteResult result = document.rewriteCanonical(
                bundledConfig(),
                ConfigManager.canonicalValues(validated)
        );

        assertTrue(result.changed());
        assertNotNull(result.backup());
        assertEquals(invalid, Files.readString(result.backup()));
        assertTrue(Files.readString(config).contains("schema-version: 3"));
    }

    @Test
    void boundsDistinctAutomaticBackups() throws IOException {
        Path config = temporaryDirectory.resolve("config.yml");
        String template = bundledConfig();

        for (int index = 0; index < ConfigDocument.MAX_BACKUPS + 3; index++) {
            Files.writeString(config, "unknown-" + index + ": true\n");
            ConfigDocument document = ConfigDocument.load(config, true);
            ConfigManager.Snapshot validated = validate(document.configuration(), new ArrayList<>());
            document.rewriteCanonical(template, ConfigManager.canonicalValues(validated));
        }

        assertEquals(ConfigDocument.MAX_BACKUPS, backupCount());
    }

    @Test
    void rejectsFutureSchemasWithoutRewritingThem() throws IOException {
        Path config = temporaryDirectory.resolve("config.yml");
        String future = "schema-version: 99\nfuture-setting: true\n";
        Files.writeString(config, future);
        ConfigDocument document = ConfigDocument.load(config, true);

        assertThrows(
                IllegalStateException.class,
                () -> ConfigManager.ensureSupportedSchema(document.configuration(), message -> { })
        );
        assertEquals(future, Files.readString(config));
        assertEquals(0L, backupCount());
    }

    @Test
    void boundsConfigInputBeforeYamlParsing() throws IOException {
        Path config = temporaryDirectory.resolve("config.yml");
        byte[] oversized = new byte[(int) ConfigDocument.MAX_CONFIG_BYTES + 1];
        Files.write(config, oversized);

        ConfigDocument document = ConfigDocument.load(config, true);

        assertNotNull(document.loadProblem());
        assertTrue(document.configuration().getKeys(true).isEmpty());
    }

    private long backupCount() throws IOException {
        try (var paths = Files.list(temporaryDirectory)) {
            return paths.filter(path -> path.getFileName().toString().startsWith("config.yml.backup-"))
                    .count();
        }
    }

    private static ConfigManager.Snapshot validate(
            YamlConfiguration configuration,
            List<String> warnings
    ) {
        return ConfigManager.validate(configuration, warnings::add, materialName -> switch (materialName) {
            case "BLACK_WOOL" -> Material.BLACK_WOOL;
            case "BLACK_CONCRETE" -> Material.BLACK_CONCRETE;
            case "OBSIDIAN" -> Material.OBSIDIAN;
            default -> null;
        });
    }

    private static String bundledConfig() throws IOException {
        try (InputStream stream = ConfigDocumentTest.class.getResourceAsStream("/config.yml")) {
            if (stream == null) {
                throw new IOException("Bundled config.yml missing from test classpath");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

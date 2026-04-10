package com.huawei.hicampus.mate.matecampusclaw.codingagent.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SettingsManager {
    private static final Logger log = LoggerFactory.getLogger(SettingsManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Path GLOBAL_SETTINGS = com.huawei.hicampus.mate.matecampusclaw.codingagent.config.AppPaths.GLOBAL_SETTINGS;
    private static final String PROJECT_SETTINGS = com.huawei.hicampus.mate.matecampusclaw.codingagent.config.AppPaths.PROJECT_SETTINGS;

    private Path workingDir = Path.of(System.getProperty("user.dir"));

    public void setWorkingDir(Path workingDir) { this.workingDir = workingDir; }

    /** Load merged settings (project overrides global). */
    public Settings load() {
        JsonNode global = loadJsonFile(GLOBAL_SETTINGS);
        JsonNode project = loadJsonFile(workingDir.resolve(PROJECT_SETTINGS));
        JsonNode merged = deepMerge(global, project);
        try {
            return MAPPER.treeToValue(merged, Settings.class);
        } catch (Exception e) {
            log.warn("Failed to parse merged settings", e);
            return Settings.empty();
        }
    }

    /** Load only global settings. */
    public Settings loadGlobal() {
        return loadFromFile(GLOBAL_SETTINGS);
    }

    /** Save a value to global settings. */
    public void setGlobal(String key, Object value) {
        saveToFile(GLOBAL_SETTINGS, key, value);
    }

    /** Save a value to project settings. */
    public void setProject(String key, Object value) {
        saveToFile(workingDir.resolve(PROJECT_SETTINGS), key, value);
    }

    private Settings loadFromFile(Path path) {
        JsonNode node = loadJsonFile(path);
        try {
            return MAPPER.treeToValue(node, Settings.class);
        } catch (Exception e) {
            log.warn("Failed to parse settings from {}", path, e);
            return Settings.empty();
        }
    }

    private void saveToFile(Path path, String key, Object value) {
        try {
            ObjectNode root;
            if (Files.exists(path)) {
                root = (ObjectNode) MAPPER.readTree(Files.readString(path));
            } else {
                Files.createDirectories(path.getParent());
                root = MAPPER.createObjectNode();
            }
            root.set(key, MAPPER.valueToTree(value));
            Files.writeString(path, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (IOException e) {
            log.error("Failed to save settings to {}", path, e);
        }
    }

    private JsonNode loadJsonFile(Path path) {
        if (!Files.exists(path)) return MAPPER.createObjectNode();
        try {
            return MAPPER.readTree(Files.readString(path));
        } catch (Exception e) {
            log.warn("Failed to read settings from {}", path, e);
            return MAPPER.createObjectNode();
        }
    }

    /** Deep merge: project values override global values. */
    static JsonNode deepMerge(JsonNode base, JsonNode override) {
        if (!base.isObject() || !override.isObject()) return override;
        ObjectNode result = base.deepCopy();
        Iterator<String> fieldNames = ((ObjectNode) override).fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            JsonNode overrideVal = override.get(field);
            if (result.has(field) && result.get(field).isObject() && overrideVal.isObject()) {
                result.set(field, deepMerge(result.get(field), overrideVal));
            } else {
                result.set(field, overrideVal.deepCopy());
            }
        }
        return result;
    }
}

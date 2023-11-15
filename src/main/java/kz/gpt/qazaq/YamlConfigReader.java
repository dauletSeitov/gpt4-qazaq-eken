package kz.gpt.qazaq;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.util.Map;

public class YamlConfigReader {

    private final Map<String, Object> properties;

    public YamlConfigReader(String fileName) {
        Yaml yaml = new Yaml(new Constructor(Map.class));
        try (InputStream in = this.getClass().getClassLoader().getResourceAsStream(fileName)) {
            if (in == null) {
                throw new IllegalArgumentException("File not found: " + fileName);
            }
            properties = yaml.load(in);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load YAML file", e);
        }

    }

    public <T> T getValue(String key) {//TODO still cannot compile ${BOT_KEY:botkey} expression
        String[] keyParts = key.split("\\.");
        Map<String, Object> nestedMap = properties;

        for (int i = 0; i < keyParts.length - 1; i++) {
            String part = keyParts[i];
            if (nestedMap.containsKey(part) && nestedMap.get(part) instanceof Map) {
                nestedMap = (Map<String, Object>) nestedMap.get(part);
            } else {
                return null;
            }
        }

        return (T) nestedMap.get(keyParts[keyParts.length - 1]);
    }

}

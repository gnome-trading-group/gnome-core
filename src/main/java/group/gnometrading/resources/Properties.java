package group.gnometrading.resources;

import group.gnometrading.collections.GnomeMap;
import group.gnometrading.collections.PooledHashMap;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Properties class is used to maintain configuration properties loaded in
 * on startup. The constructor produces garbage, but thereafter no garbage
 * is produced when fetching properties.
 */
public class Properties {

    private final String filePath;
    private final GnomeMap<String, String> properties;

    public Properties(final String filePath) throws IOException {
        this.filePath = filePath;
        this.properties = new PooledHashMap<>();
        this.loadProperties();
    }

    private void loadProperties() throws IOException {
        try (var br = new BufferedReader(new FileReader(this.filePath))) {
            for (String line; (line = br.readLine()) != null; ) {
                processLine(line.trim());
            }
        }
    }

    private void processLine(final String line) {
        String[] parts = line.split("=");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid properties line: " + line);
        }

        String key = parts[0];
        String value = parts[1];

        properties.put(key, value);
    }

    public int getIntProperty(final String key) {
        return Integer.parseInt(this.getStringProperty(key));
    }

    public boolean getBooleanProperty(final String key) {
        return Boolean.parseBoolean(this.getStringProperty(key));
    }

    public String getStringProperty(final String key) {
        ensureValidProperty(key);
        return this.properties.get(key);
    }

    private void ensureValidProperty(final String key) {
        if (!this.properties.containsKey(key)) {
            throw new IllegalArgumentException("Invalid property: " + key);
        }
    }

}

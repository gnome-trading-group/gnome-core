package group.gnometrading.resources;

import java.io.IOException;

/**
 * Properties class is used to maintain configuration properties loaded in
 * on startup. The constructor produces garbage, but thereafter no garbage
 * is produced when fetching properties.
 */
public class Properties {

    private final String resourcePath;
    private final java.util.Properties internalProps;

    public Properties(final String resourcePath) throws IOException {
        this.resourcePath = resourcePath;
        this.internalProps = new java.util.Properties();
        this.loadProperties();
    }

    private void loadProperties() throws IOException {
        try (var inputStream = this.getClass().getClassLoader().getResourceAsStream(this.resourcePath)) {
            this.internalProps.load(inputStream);
        }
    }

    public int getIntProperty(final String key) {
        return Integer.parseInt(this.getStringProperty(key));
    }

    public boolean getBooleanProperty(final String key) {
        return Boolean.parseBoolean(this.getStringProperty(key));
    }

    public String getStringProperty(final String key) {
        ensureValidProperty(key);
        return this.internalProps.getProperty(key);
    }

    private void ensureValidProperty(final String key) {
        if (!this.internalProps.containsKey(key)) {
            throw new IllegalArgumentException("Invalid property: " + key);
        }
    }

}

package group.gnometrading.resources;

import group.gnometrading.collections.GnomeMap;
import group.gnometrading.collections.PooledHashMap;

import java.io.IOException;

/**
 * Properties class is used to maintain configuration properties loaded in
 * on startup. The constructor produces garbage, but thereafter no garbage
 * is produced when fetching properties.
 * <p>
 * Properties are resolved in the following order of precedence:
 * 1. Command-line arguments (highest priority)
 * 2. Environment variables
 * 3. Properties file (lowest priority)
 */
public class Properties {

    private final String resourcePath;
    private final java.util.Properties internalProps;
    private final GnomeMap<String, String> cliOverrides;
    private final GnomeMap<String, String> envOverrides;

    /**
     * Creates a Properties instance loading from the specified resource path.
     * No CLI or environment variable overrides are applied.
     *
     * @param resourcePath path to the properties file in the classpath
     * @throws IOException if the properties file cannot be loaded
     */
    public Properties(final String resourcePath) throws IOException {
        this(resourcePath, new String[0]);
    }

    /**
     * Creates a Properties instance loading from the specified resource path
     * with support for CLI arguments and environment variable overrides.
     * <p>
     * CLI arguments should be in the format: --property.name=value or -Dproperty.name=value
     * Environment variables are mapped by converting property keys to uppercase and replacing dots with underscores.
     * For example, "server.port" maps to environment variable "SERVER_PORT".
     *
     * @param resourcePath path to the properties file in the classpath
     * @param args command-line arguments to parse for property overrides
     * @throws IOException if the properties file cannot be loaded
     */
    public Properties(final String resourcePath, final String[] args) throws IOException {
        this.resourcePath = resourcePath;
        this.internalProps = new java.util.Properties();
        this.cliOverrides = new PooledHashMap<>();
        this.envOverrides = new PooledHashMap<>();

        this.loadProperties();
        this.loadEnvironmentOverrides();
        this.loadCliOverrides(args);
    }

    private void loadProperties() throws IOException {
        try (var inputStream = this.getClass().getClassLoader().getResourceAsStream(this.resourcePath)) {
            this.internalProps.load(inputStream);
        }
    }

    /**
     * Load environment variables for all defined environment variables.
     * For example, "server.port" property maps to "SERVER_PORT" environment variable.
     */
    private void loadEnvironmentOverrides() {
        for (String key : System.getenv().keySet()) {
            String propertyKey = key.toLowerCase().replace('_', '.');
            this.envOverrides.put(propertyKey, System.getenv(key));
        }
    }

    /**
     * Parses command-line arguments for property overrides.
     * Supports two formats:
     * - --property.name=value
     * - -Dproperty.name=value
     *
     * @param args command-line arguments
     */
    private void loadCliOverrides(final String[] args) {
        if (args == null) {
            return;
        }

        for (String arg : args) {
            if (arg.startsWith("--") || arg.startsWith("-D")) {
                parseCliArgument(arg.substring(2));
            }
        }
    }

    private void parseCliArgument(final String arg) {
        final int equalsIndex = arg.indexOf('=');
        if (equalsIndex > 0 && equalsIndex < arg.length() - 1) {
            final String key = arg.substring(0, equalsIndex);
            final String value = arg.substring(equalsIndex + 1);
            this.cliOverrides.put(key, value);
        }
    }

    public int getIntProperty(final String key) {
        return Integer.parseInt(this.getStringProperty(key));
    }

    public boolean getBooleanProperty(final String key) {
        return Boolean.parseBoolean(this.getStringProperty(key));
    }

    /**
     * Gets a string property with the following precedence:
     * 1. CLI argument override (highest priority)
     * 2. Environment variable override
     * 3. Properties file value (lowest priority)
     *
     * @param key the property key
     * @return the property value
     * @throws IllegalArgumentException if the property key is not defined
     */
    public String getStringProperty(final String key) {
        ensureValidProperty(key);

        if (this.cliOverrides.containsKey(key)) {
            return this.cliOverrides.get(key);
        }

        if (this.envOverrides.containsKey(key)) {
            return this.envOverrides.get(key);
        }

        return this.internalProps.getProperty(key);
    }

    private void ensureValidProperty(final String key) {
        if (!this.internalProps.containsKey(key) && !this.envOverrides.containsKey(key) && !this.cliOverrides.containsKey(key)) {
            throw new IllegalArgumentException("Invalid property: " + key);
        }
    }

}

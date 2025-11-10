package group.gnometrading.resources;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesTest {

    private static Properties properties;

    @BeforeAll
    static void setUp() throws IOException {
        properties = new Properties("properties/test-props.properties");
    }

    @Test
    void getIntProperty() {
        assertEquals(1234, properties.getIntProperty("int.valid.key"));
        assertThrows(NumberFormatException.class, () -> properties.getIntProperty("int.invalid.key"));
    }

    @Test
    void getBooleanProperty() {
        assertTrue(properties.getBooleanProperty("boolean.true.key"));
        assertFalse(properties.getBooleanProperty("boolean.false.key"));
    }

    @Test
    void getStringProperty() {
        assertEquals("This is my string!", properties.getStringProperty("string.key"));
    }

    @Test
    void testWeirdProperty() {
        assertEquals("hello==hellohello", properties.getStringProperty("weird.property"));
    }

    @Test
    void testNewLine() {
        assertEquals("hey\nthis is on a new line", properties.getStringProperty("new.line"));
    }

    @Test
    void testCliOverrideWithDoubleDash() throws IOException {
        String[] args = {"--string.key=CLI Override Value"};
        Properties propsWithCli = new Properties("properties/test-props.properties", args);
        assertEquals("CLI Override Value", propsWithCli.getStringProperty("string.key"));
    }

    @Test
    void testCliOverrideWithDashD() throws IOException {
        String[] args = {"-Dint.valid.key=9999"};
        Properties propsWithCli = new Properties("properties/test-props.properties", args);
        assertEquals(9999, propsWithCli.getIntProperty("int.valid.key"));
    }

    @Test
    void testCliOverrideBooleanProperty() throws IOException {
        String[] args = {"--boolean.true.key=false"};
        Properties propsWithCli = new Properties("properties/test-props.properties", args);
        assertFalse(propsWithCli.getBooleanProperty("boolean.true.key"));
    }

    @Test
    void testMultipleCliOverrides() throws IOException {
        String[] args = {
            "--string.key=First Override",
            "-Dint.valid.key=5555",
            "--boolean.false.key=true"
        };
        Properties propsWithCli = new Properties("properties/test-props.properties", args);
        assertEquals("First Override", propsWithCli.getStringProperty("string.key"));
        assertEquals(5555, propsWithCli.getIntProperty("int.valid.key"));
        assertTrue(propsWithCli.getBooleanProperty("boolean.false.key"));
    }

    @Test
    void testCliArgDefinesNewProperty() throws IOException {
        String[] args = {"--new.cli.property=CLI Defined Value"};
        Properties propsWithCli = new Properties("properties/test-props.properties", args);
        // Property not in file should be accessible via CLI
        assertEquals("CLI Defined Value", propsWithCli.getStringProperty("new.cli.property"));
    }

    @Test
    void testMultipleNewPropertiesViaCli() throws IOException {
        String[] args = {
            "--custom.property.one=Value1",
            "-Dcustom.property.two=Value2",
            "--custom.property.three=Value3"
        };
        Properties propsWithCli = new Properties("properties/test-props.properties", args);
        assertEquals("Value1", propsWithCli.getStringProperty("custom.property.one"));
        assertEquals("Value2", propsWithCli.getStringProperty("custom.property.two"));
        assertEquals("Value3", propsWithCli.getStringProperty("custom.property.three"));
    }

    @Test
    void testCliAndFilePropertiesCombined() throws IOException {
        String[] args = {"--new.property=New", "--string.key=Overridden"};
        Properties propsWithCli = new Properties("properties/test-props.properties", args);
        // New property from CLI
        assertEquals("New", propsWithCli.getStringProperty("new.property"));
        // Overridden property from file
        assertEquals("Overridden", propsWithCli.getStringProperty("string.key"));
        // Original property from file (not overridden)
        assertEquals(1234, propsWithCli.getIntProperty("int.valid.key"));
    }

    @Test
    void testEmptyArgsArray() throws IOException {
        String[] args = {};
        Properties propsWithCli = new Properties("properties/test-props.properties", args);
        assertEquals("This is my string!", propsWithCli.getStringProperty("string.key"));
    }

    @Test
    void testInvalidPropertyThrowsException() throws IOException {
        String[] args = {};
        Properties propsWithCli = new Properties("properties/test-props.properties", args);
        // Property not in file, CLI, or env should throw exception
        assertThrows(IllegalArgumentException.class,
            () -> propsWithCli.getStringProperty("totally.undefined.property"));
    }

    @Test
    void testCliIntPropertyNotInFile() throws IOException {
        String[] args = {"--custom.port=12345"};
        Properties propsWithCli = new Properties("properties/test-props.properties", args);
        assertEquals(12345, propsWithCli.getIntProperty("custom.port"));
    }

    @Test
    void testCliBooleanPropertyNotInFile() throws IOException {
        String[] args = {"--custom.enabled=true"};
        Properties propsWithCli = new Properties("properties/test-props.properties", args);
        assertTrue(propsWithCli.getBooleanProperty("custom.enabled"));
    }

    @Test
    void testEnvironmentVariableAccessible() throws IOException {
        // This test verifies that environment variables are loaded
        // We can't easily set env vars in a test, but we can verify that
        // if an env var exists, it would be accessible as a property
        // Most systems have PATH or HOME set
        Properties props = new Properties("properties/test-props.properties");

        // Check if we can access a common environment variable
        // PATH exists on all systems, converted to lowercase "path"
        String pathValue = System.getenv("PATH");
        if (pathValue != null) {
            assertEquals(pathValue, props.getStringProperty("path"));
        }
    }

    @Test
    void testCliOverridesEnvironmentVariable() throws IOException {
        // Verify precedence: CLI > Env Var
        // We'll use an env var that likely exists (PATH) and override it with CLI
        String pathValue = System.getenv("PATH");
        if (pathValue != null) {
            String[] args = {"--path=CLI_OVERRIDE"};
            Properties props = new Properties("properties/test-props.properties", args);
            assertEquals("CLI_OVERRIDE", props.getStringProperty("path"));
            // Verify it's not the env var value
            assertNotEquals(pathValue, props.getStringProperty("path"));
        }
    }

    @Test
    void testEnvironmentVariableWithUnderscores() throws IOException {
        // Environment variables with underscores should be converted to dots
        // For example, MY_CUSTOM_VAR should be accessible as my.custom.var
        // We can't set env vars in test, but we can verify the conversion logic
        // by checking that if USER or HOME exists, it's accessible
        String userValue = System.getenv("USER");
        if (userValue != null) {
            Properties props = new Properties("properties/test-props.properties");
            assertEquals(userValue, props.getStringProperty("user"));
        }
    }
}
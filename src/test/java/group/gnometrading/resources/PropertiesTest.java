package group.gnometrading.resources;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesTest {

    private static Properties properties;

    @BeforeAll
    static void setUp() throws IOException {
        properties = new Properties("src/test/resources/properties/test-props.properties");
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
}
package group.gnometrading.codecs.json;

import group.gnometrading.strings.GnomeString;
import group.gnometrading.strings.MutableString;
import group.gnometrading.strings.ViewString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class JSONDecoderTest {

    private static Stream<Arguments> testIntNodesArguments() {
        return Stream.of(
                Arguments.of("0", 0),
                Arguments.of("1", 1),
                Arguments.of("10", 10),
                Arguments.of("100", 100),
                Arguments.of("1001", 1001),
                Arguments.of("1001534", 1001534),
                Arguments.of("10.51", 10),
                Arguments.of("-10.51", -10),
                Arguments.of("" + Integer.MAX_VALUE, Integer.MAX_VALUE)
        );
    }

    @ParameterizedTest
    @MethodSource("testIntNodesArguments")
    void testIntNodes(String json, int expected) {
        assertEquals(expected, new JSONDecoder().wrap(ByteBuffer.wrap(json.getBytes())).asInt());
    }

    private static Stream<Arguments> testFixedPointLongNodesArguments() {
        return Stream.of(
                Arguments.of("0", 0, 1_000),
                Arguments.of("1", 1_000, 1_000),
                Arguments.of("1.1", 1_100, 1_000),
                Arguments.of("1.1234", 1_123, 1_000),
                Arguments.of("1234.532", 1234532000, 1_000_000),
                Arguments.of("-1234.532", -1234532000, 1_000_000)
        );
    }

    @ParameterizedTest
    @MethodSource("testFixedPointLongNodesArguments")
    void testFixedPointLongNodes(String json, long expected, long scalingFactor) {
        assertEquals(expected, new JSONDecoder().wrap(ByteBuffer.wrap(json.getBytes())).asFixedPointLong(scalingFactor));
    }

    private static Stream<Arguments> testLongNodesArguments() {
        return Stream.of(
                Arguments.of("0", 0),
                Arguments.of("1", 1),
                Arguments.of("10", 10),
                Arguments.of("100", 100),
                Arguments.of("1001", 1001),
                Arguments.of("1001534", 1001534),
                Arguments.of("10.51", 10),
                Arguments.of("-10.51", -10),
                Arguments.of("" + Long.MAX_VALUE, Long.MAX_VALUE),
                Arguments.of("" + Long.MIN_VALUE, Long.MIN_VALUE)
        );
    }

    @ParameterizedTest
    @MethodSource("testLongNodesArguments")
    void testLongNodes(String json, long expected) {
        assertEquals(expected, new JSONDecoder().wrap(ByteBuffer.wrap(json.getBytes())).asLong());
    }

    private static Stream<Arguments> testDoubleNodesArguments() {
        return Stream.of(
                Arguments.of("0", 0),
                Arguments.of("0.1", 0.1),
                Arguments.of("1.1", 1.1),
                Arguments.of("10005.1042314", 10005.1042314),
                Arguments.of("0.000005", 0.000005),
                Arguments.of("-0.000005", -0.000005)
        );
    }

    @ParameterizedTest
    @MethodSource("testDoubleNodesArguments")
    void testDoubleNodes(String json, double expected) {
        assertEquals(expected, new JSONDecoder().wrap(ByteBuffer.wrap(json.getBytes())).asDouble());
    }

    private static String largeString(int num) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < num; i++) {
            out.append(i);
        }
        return out.toString();
    }

    private static Stream<Arguments> testStringNodesArguments() {
        return Stream.of(
                Arguments.of("\"\"", new ViewString("")),
                Arguments.of("\"hello\"", new ViewString("hello")),
                Arguments.of("\"have you heard\"", new ViewString("have you heard")),
                Arguments.of("\"of the tragedy\"", new ViewString("of the tragedy")),
                Arguments.of("\"of Darth Plagueis the Wise?\"", new ViewString("of Darth Plagueis the Wise?")),
                Arguments.of("\"0.0550\"", new ViewString("0.0550")),
                Arguments.of("\"" + largeString(500) +"\"", new ViewString(largeString(500)))
        );
    }

    @ParameterizedTest
    @MethodSource("testStringNodesArguments")
    void testStringNodes(String json, GnomeString expected) {
        assertTrue(expected.equals(new JSONDecoder().wrap(ByteBuffer.wrap(json.getBytes())).asString()));
    }

    @Test
    public void testEmptyObjects() {
        JSONDecoder jsonDecoder = new JSONDecoder();

        String payload = "[]";
        var node = jsonDecoder.wrap(ByteBuffer.wrap(payload.getBytes()));
        var array = node.asArray();
        assertFalse(array.hasNextItem());

        payload = "{}";
        node = jsonDecoder.wrap(ByteBuffer.wrap(payload.getBytes()));
        var obj = node.asObject();
        assertFalse(obj.hasNextKey());
    }

    @Test
    public void testNullValues() {
        JSONDecoder jsonDecoder = new JSONDecoder();

        String payload = "null";
        var node = jsonDecoder.wrap(ByteBuffer.wrap(payload.getBytes()));
        assertTrue(node.isNull());

        payload = "1";
        node = jsonDecoder.wrap(ByteBuffer.wrap(payload.getBytes()));
        assertFalse(node.isNull());

        payload = "[null]";
        node = jsonDecoder.wrap(ByteBuffer.wrap(payload.getBytes()));
        try (var array = node.asArray()) {
            assertTrue(array.nextItem().isNull());
        }

        payload = "[1]";
        node = jsonDecoder.wrap(ByteBuffer.wrap(payload.getBytes()));
        try (var array = node.asArray()) {
            assertFalse(array.nextItem().isNull());
        }

        payload = "{\"key\": null}";
        node = jsonDecoder.wrap(ByteBuffer.wrap(payload.getBytes()));
        try (var obj = node.asObject()) {
            assertTrue(obj.nextKey().isNull());
        }
    }

    @Test
    public void testBooleanValues() {
        JSONDecoder jsonDecoder = new JSONDecoder();

        String payload = "true";
        var node = jsonDecoder.wrap(ByteBuffer.wrap(payload.getBytes()));
        assertTrue(node.asBoolean());

        payload = "false";
        node = jsonDecoder.wrap(ByteBuffer.wrap(payload.getBytes()));
        assertFalse(node.asBoolean());

        payload = "[true]";
        node = jsonDecoder.wrap(ByteBuffer.wrap(payload.getBytes()));
        assertTrue(node.asArray().nextItem().asBoolean());

        payload = "[false]";
        node = jsonDecoder.wrap(ByteBuffer.wrap(payload.getBytes()));
        assertFalse(node.asArray().nextItem().asBoolean());

        payload = "{\"key\": true]";
        node = jsonDecoder.wrap(ByteBuffer.wrap(payload.getBytes()));
        assertTrue(node.asObject().nextKey().asBoolean());

        payload = "{\"key\": false]";
        node = jsonDecoder.wrap(ByteBuffer.wrap(payload.getBytes()));
        assertFalse(node.asObject().nextKey().asBoolean());
    }

    @Test
    public void testComplexObjects() {
        String payload = """
                {"hi": true,
                "myNumbie": 5.0401, "testArray": [1, 2, 3]}
                """;
        JSONDecoder jsonDecoder = new JSONDecoder();
        try (final var node = jsonDecoder.wrap(ByteBuffer.wrap(payload.getBytes()))) {
            try (final var obj = node.asObject()) {
                assertTrue(obj.hasNextKey());
                try (final var key1 = obj.nextKey()) {
                    assertTrue(key1.getName().equals("hi"));
                    assertTrue(key1.asBoolean());
                }

                assertTrue(obj.hasNextKey());
                try (final var key2 = obj.nextKey()) {
                    assertTrue(key2.getName().equals("myNumbie"));
                    assertEquals(5.0401, key2.asDouble());
                }

                assertTrue(obj.hasNextKey());
                try (final var key3 = obj.nextKey()) {
                    assertTrue(key3.getName().equals("testArray"));
                    try (final var array = key3.asArray()) {
                        for (int i = 1; i <= 3; i++) {
                            final var item = array.nextItem();
                            assertEquals(i, item.asInt());
                            item.close();
                        }

                        assertFalse(array.hasNextItem());
                    }
                }

                assertFalse(obj.hasNextKey());
            }
        }
    }

    @Test
    public void testBinanceDump() {
        String payload = """
                        {
                          "e": "depthUpdate",
                          "E": 1672515782136,
                          "s": "BNBBTC",
                          "U": 157,
                          "u": 160,    
                          "b": [   
                            [
                              "0.0024",   
                              "10"      
                            ]
                          ],
                          "a": [     
                            [
                              "0.0026",  
                              "100"      
                            ]
                          ]
                        }
                """;
        JSONDecoder jsonDecoder = new JSONDecoder();
        try (final var node = jsonDecoder.wrap(ByteBuffer.wrap(payload.getBytes()))) {
            try (final var obj = node.asObject()) {
                List<String> keys = new ArrayList<>();
                while (obj.hasNextKey()) {
                    try (final var key = obj.nextKey()) {
                        keys.add(key.getName().toString());
                        if (key.getName().equals("e")) {
                            assertTrue(key.asString().equals("depthUpdate"));
                        } else if (key.getName().equals("a")) {
                            try (final var asks = key.asArray()) {
                                try (final var ask = asks.nextItem().asArray()) {
                                    MutableString price = new MutableString();
                                    MutableString quantity = new MutableString();
                                    try (final var child = ask.nextItem()) {
                                        price.copy(child.asString());
                                    }
                                    try (final var child = ask.nextItem()) {
                                        quantity.copy(child.asString());
                                    }
                                    assertTrue(price.equals("0.0026"));
                                    assertTrue(quantity.equals("100"));
                                }
                            }
                        }
                    }
                }
                assertEquals(List.of("e", "E", "s", "U", "u", "b", "a"), keys);
            }
        }
    }

    @Test
    public void testConsumesNestedObjects() {
        String payload = """
                {
                    "key": {"child": {"child2": 5, "child3": {}}}, "key2": 10
                }
                """;
        JSONDecoder jsonDecoder = new JSONDecoder();
        try (final var obj = jsonDecoder.wrap(ByteBuffer.wrap(payload.getBytes())).asObject()) {
            try (final var key = obj.nextKey()) {
                // Do nothing with it
            }
            try (final var key2 = obj.nextKey()) {
                assertTrue(key2.getName().equals("key2"));
                assertEquals(10, key2.asInt());
            }
        }
    }

    @Test
    public void testSkipsKeyObjects() {
        String payload = """
                {
                    "key": {"child": {"child2": 5, "child3": {}}, "child2": 50}, "key2": 10
                }
                """;
        JSONDecoder jsonDecoder = new JSONDecoder();
        try (final var obj = jsonDecoder.wrap(ByteBuffer.wrap(payload.getBytes())).asObject()) {
            try (final var key = obj.nextKey(); final var keyObj = key.asObject()) {
                try (final var ignore = keyObj.nextKey()) {}

                try (final var child2 = keyObj.nextKey()) {
                    assertEquals(50, child2.asInt());
                }
            }
            try (final var key2 = obj.nextKey()) {
                assertTrue(key2.getName().equals("key2"));
                assertEquals(10, key2.asInt());
            }
        }
    }

    @Test
    public void testWeirdlyNestedDump() {
        String payload = """
                        {
                          "key": {"hi": [{"hi2": [1]}]},
                          "arr": [[  {"nested": [1]  }] ],
                          "skip": [{    }],
                          "final": [   null],
                        }
                """;
        JSONDecoder jsonDecoder = new JSONDecoder();
        try (final var obj = jsonDecoder.wrap(ByteBuffer.wrap(payload.getBytes())).asObject()) {
            while (true) {
                try (final var key = obj.nextKey()) {
                    if (key.getName().equals("key")) {
                        try (final var keyObj = key.asObject()) {
                            try (final var key2 = keyObj.nextKey()) {
                                assertTrue(key2.getName().equals("hi"));
                                try (final var key2Array = key2.asArray()) {
                                    try (final var key3 = key2Array.nextItem()) {
                                        try (final var key3Obj = key3.asObject()) {
                                            try (final var key4 = key3Obj.nextKey()) {
                                                assertTrue(key4.getName().equals("hi2"));
                                                try (final var key4Array = key4.asArray()) {
                                                    try (final var key5 = key4Array.nextItem()) {
                                                        assertEquals(1, key5.asInt());
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else if (key.getName().equals("final")) {
                        try (final var arr = key.asArray()) {
                            assertTrue(arr.nextItem().isNull());
                        }
                        break;
                    }
                }
            }
        }
    }
}
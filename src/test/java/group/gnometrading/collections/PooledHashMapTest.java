package group.gnometrading.collections;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PooledHashMapTest {

    private static Stream<Arguments> testGetArguments() {
        return Stream.of(
                Arguments.of("1:1,5:1,3:1,10:1", "1", "1"),
                Arguments.of("1:1,5:1,3:1,10:1", "0", null),
                Arguments.of("-1:1,-5:1,3:1,10:1", "-5", "1"),
                Arguments.of("-5:1,-5:10", "-5", "10"));
    }

    @ParameterizedTest
    @MethodSource("testGetArguments")
    void testGet(String map, String key, String result) {
        assertEquals(result, generate(map).get(key));
    }

    private static Stream<Arguments> testRemoveArguments() {
        return Stream.of(
                Arguments.of("1:1", "1", ""),
                Arguments.of("1:1", "0", "1:1"),
                Arguments.of("1:1,2:1,3:1", "2", "1:1,3:1"));
    }

    @ParameterizedTest
    @MethodSource("testRemoveArguments")
    void testRemove(String start, String toRemove, String end) {
        var map1 = generate(start);
        map1.remove(toRemove);
        var map2 = generate(end);

        assertEquals(map1.size(), map2.size());
        for (var key : map1.keys()) {
            assertEquals(map1.get(key), map2.get(key));
        }
    }

    @Test
    void testResizing() {
        var map = new PooledHashMap<String, String>(1);
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());

        map.put("1", "1");
        map.put("2", "2");
        assertEquals(2, map.size());
        assertFalse(map.isEmpty());
        assertTrue(map.containsKey("1") && map.containsKey("2"));
        assertEquals("2", map.get("2"));

        for (int i = 0; i < (1 << 10); i++) {
            map.put(i + "", i + "");
        }
        assertEquals((1 << 10), map.size());
    }

    @Test
    void testClear() {
        var map = new PooledHashMap<String, String>(1);
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());

        assertNull(map.get("1"));
        map.put("1", "2");
        assertEquals("2", map.get("1"));
        assertEquals(1, map.size());

        map.clear();
        assertNull(map.get("1"));
        assertEquals(0, map.size());
    }

    @Test
    void testForEachValueVisitsAllValues() {
        GnomeMap<String, String> map = generate("a:x,b:y,c:z");
        List<String> values = new ArrayList<>();
        map.forEachValue(values::add);
        assertEquals(3, values.size());
        assertTrue(values.contains("x"));
        assertTrue(values.contains("y"));
        assertTrue(values.contains("z"));
    }

    @Test
    void testForEachValueOnEmptyMap() {
        GnomeMap<String, String> map = new PooledHashMap<>();
        List<String> values = new ArrayList<>();
        map.forEachValue(values::add);
        assertTrue(values.isEmpty());
    }

    @Test
    void testForEachKeyVisitsAllKeys() {
        GnomeMap<String, String> map = generate("a:x,b:y,c:z");
        List<String> keys = new ArrayList<>();
        map.forEachKey(keys::add);
        assertEquals(3, keys.size());
        assertTrue(keys.contains("a"));
        assertTrue(keys.contains("b"));
        assertTrue(keys.contains("c"));
    }

    @Test
    void testForEachKeyOnEmptyMap() {
        GnomeMap<String, String> map = new PooledHashMap<>();
        List<String> keys = new ArrayList<>();
        map.forEachKey(keys::add);
        assertTrue(keys.isEmpty());
    }

    private static GnomeMap<String, String> generate(String pairs) {
        GnomeMap<String, String> map = new PooledHashMap<>();
        for (String item : pairs.split(",")) {
            String[] items = item.split(":");
            if (items.length == 2) {
                map.put(items[0], items[1]);
            }
        }
        return map;
    }
}

package group.gnometrading.collections;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IntToIntHashMapTest {

    private static Stream<Arguments> testGetArguments() {
        return Stream.of(
                Arguments.of("1:1,5:1,3:1,10:1", 1, 1),
                Arguments.of("1:1,5:1,3:1,10:1", 0, IntToIntHashMap.MISSING),
                Arguments.of("-1:1,-5:1,3:1,10:1", -5, 1),
                Arguments.of("-5:1,-5:10", -5, 10));
    }

    @ParameterizedTest
    @MethodSource("testGetArguments")
    void testGet(String map, int key, int result) {
        assertEquals(result, generate(map).get(key));
    }

    private static Stream<Arguments> testRemoveArguments() {
        return Stream.of(
                Arguments.of("1:1", 1, ""), Arguments.of("1:1", 0, "1:1"), Arguments.of("1:1,2:1,3:1", 2, "1:1,3:1"));
    }

    @ParameterizedTest
    @MethodSource("testRemoveArguments")
    void testRemove(String start, int toRemove, String end) {
        var map1 = generate(start);
        map1.remove(toRemove);
        var map2 = generate(end);

        assertEquals(map1.size(), map2.size());
        for (var key : map1.keys()) {
            assertEquals(map1.get(key), map2.get(key));
        }
    }

    @Test
    void testRemoveReturnsOldValue() {
        var map = generate("1:10,2:20");
        assertEquals(10, map.remove(1));
        assertEquals(IntToIntHashMap.MISSING, map.remove(1));
    }

    @Test
    void testContainsKey() {
        var map = generate("1:10,2:20");
        assertTrue(map.containsKey(1));
        assertTrue(map.containsKey(2));
        assertFalse(map.containsKey(3));
    }

    @Test
    void testResizing() {
        var map = new IntToIntHashMap(1);
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());

        map.put(1, 1);
        map.put(2, 2);
        assertEquals(2, map.size());
        assertFalse(map.isEmpty());
        assertTrue(map.containsKey(1) && map.containsKey(2));
        assertEquals(2, map.get(2));

        for (int i = 0; i < (1 << 10); i++) {
            map.put(i, i);
        }
        assertEquals((1 << 10), map.size());
    }

    @Test
    void testClear() {
        var map = new IntToIntHashMap(1);
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());

        assertEquals(IntToIntHashMap.MISSING, map.get(1));
        map.put(1, 2);
        assertNotEquals(IntToIntHashMap.MISSING, map.get(1));
        assertEquals(1, map.size());

        map.clear();
        assertEquals(IntToIntHashMap.MISSING, map.get(1));
        assertEquals(0, map.size());
    }

    @Test
    void testForEachValueVisitsAllValues() {
        IntToIntMap map = generate("1:10,2:20,3:30");
        List<Integer> values = new ArrayList<>();
        map.forEachValue(values::add);
        assertEquals(3, values.size());
        assertTrue(values.contains(10));
        assertTrue(values.contains(20));
        assertTrue(values.contains(30));
    }

    @Test
    void testForEachValueOnEmptyMap() {
        IntToIntMap map = new IntToIntHashMap();
        List<Integer> values = new ArrayList<>();
        map.forEachValue(values::add);
        assertTrue(values.isEmpty());
    }

    @Test
    void testForEachKeyVisitsAllKeys() {
        IntToIntMap map = generate("1:10,2:20,3:30");
        List<Integer> keys = new ArrayList<>();
        map.forEachKey(keys::add);
        assertEquals(3, keys.size());
        assertTrue(keys.contains(1));
        assertTrue(keys.contains(2));
        assertTrue(keys.contains(3));
    }

    @Test
    void testForEachKeyOnEmptyMap() {
        IntToIntMap map = new IntToIntHashMap();
        List<Integer> keys = new ArrayList<>();
        map.forEachKey(keys::add);
        assertTrue(keys.isEmpty());
    }

    private static IntToIntHashMap generate(String pairs) {
        IntToIntHashMap map = new IntToIntHashMap();
        for (String item : pairs.split(",")) {
            String[] items = item.split(":");
            if (items.length == 2) {
                int key = Integer.parseInt(items[0]);
                int value = Integer.parseInt(items[1]);
                map.put(key, value);
            }
        }
        return map;
    }
}

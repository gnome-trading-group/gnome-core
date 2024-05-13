package group.gnometrading.collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class IntHashMapTest {

    private static Stream<Arguments> testGetArguments() {
        return Stream.of(
                Arguments.of("1:1,5:1,3:1,10:1", 1, 1),
                Arguments.of("1:1,5:1,3:1,10:1", 0, null),
                Arguments.of("-1:1,-5:1,3:1,10:1", -5, 1),
                Arguments.of("-5:1,-5:10", -5, 10)
       );
    }

    @ParameterizedTest
    @MethodSource("testGetArguments")
    void testGet(String map, int key, Integer result) {
        assertEquals(result, generate(map).get(key));
    }

    private static Stream<Arguments> testRemoveArguments() {
        return Stream.of(
                Arguments.of("1:1", 1, ""),
                Arguments.of("1:1", 0, "1:1"),
                Arguments.of("1:1,2:1,3:1", 2, "1:1,3:1")
        );
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
    void testResizing() {
        var map = new IntHashMap<Integer>(1);
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

    private static IntMap<Integer> generate(String pairs) {
        IntMap<Integer> map = new IntHashMap<>();
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
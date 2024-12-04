package group.gnometrading.codecs.json;

import group.gnometrading.strings.ViewString;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class JSONEncoderTest {

    private static Stream<Arguments> testJSONEncoderArguments() {
        return Stream.of(
                Arguments.of((Consumer<JSONEncoder>) (encoder) -> {
                    encoder
                            .writeObjectStart()
                            .writeObjectEnd();
                }, "{}"),
                Arguments.of((Consumer<JSONEncoder>) (encoder) -> {
                    encoder
                            .writeArrayStart()
                            .writeArrayEnd();
                }, "[]"),
                Arguments.of((Consumer<JSONEncoder>) (encoder) -> {
                    encoder
                            .writeObjectStart()
                            .writeString("key")
                            .writeColon()
                            .writeNumber(5.51, 3)
                            .writeObjectEnd();
                }, "{\"key\":5.510}"),
                Arguments.of((Consumer<JSONEncoder>) (encoder) -> {
                    encoder
                            .writeObjectStart()
                            .writeString("nullKey")
                            .writeColon()
                            .writeNull()
                            .writeComma()
                            .writeString("boolKey")
                            .writeColon()
                            .writeBoolean(false)
                            .writeObjectEnd();
                }, "{\"nullKey\":null,\"boolKey\":false}"),
                Arguments.of((Consumer<JSONEncoder>) (encoder) -> {
                    encoder
                            .writeObjectStart()
                            .writeObjectEntry("boolKey", true)
                            .writeComma()
                            .writeObjectEntry("boolKey", false)
                            .writeComma()
                            .writeObjectEntry("int", 505)
                            .writeComma()
                            .writeObjectEntry("double", 5.24, 4)
                            .writeObjectEnd();
                }, "{\"boolKey\":true,\"boolKey\":false,\"int\":505,\"double\":5.2400}"),
                Arguments.of((Consumer<JSONEncoder>) (encoder) -> {
                    encoder
                            .writeObjectStart()
                            .writeObjectEntry(new ViewString("boolKey"), true)
                            .writeComma()
                            .writeObjectEntry(new ViewString("boolKey"), false)
                            .writeComma()
                            .writeObjectEntry(new ViewString("int"), 505)
                            .writeComma()
                            .writeObjectEntry(new ViewString("double"), 5.24, 4)
                            .writeObjectEnd();
                }, "{\"boolKey\":true,\"boolKey\":false,\"int\":505,\"double\":5.2400}"),
                Arguments.of((Consumer<JSONEncoder>) (encoder) -> {
                    encoder
                            .writeObjectStart()
                            .writeString("nested")
                            .writeColon()
                            .writeObjectStart()
                            .writeString("arrayObj")
                            .writeColon()
                            .writeObjectStart()
                            .writeString("arr")
                            .writeColon()
                            .writeArrayStart()
                            .writeNumber(1).writeComma().writeNumber(2).writeComma().writeNumber(3)
                            .writeArrayEnd()
                            .writeObjectEnd()
                            .writeObjectEnd()
                            .writeObjectEnd();
                }, "{\"nested\":{\"arrayObj\":{\"arr\":[1,2,3]}}}")
        );
    }

    @ParameterizedTest
    @MethodSource("testJSONEncoderArguments")
    void testJSONEncoder(Consumer<JSONEncoder> consumer, String result) {
        ByteBuffer buffer = ByteBuffer.allocate(1 << 12);
        JSONEncoder encoder = new JSONEncoder();
        encoder.wrap(buffer);

        consumer.accept(encoder);
        buffer.flip();
        assertEquals(result, StandardCharsets.UTF_8.decode(buffer).toString());
    }

}
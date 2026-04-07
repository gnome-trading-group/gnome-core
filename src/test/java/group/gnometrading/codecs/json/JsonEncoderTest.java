package group.gnometrading.codecs.json;

import static org.junit.jupiter.api.Assertions.*;

import group.gnometrading.strings.ViewString;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JsonEncoderTest {

    private static Stream<Arguments> testJsonEncoderArguments() {
        return Stream.of(
                Arguments.of(
                        (Consumer<JsonEncoder>) (encoder) -> {
                            encoder.writeObjectStart().writeObjectEnd();
                        },
                        "{}"),
                Arguments.of(
                        (Consumer<JsonEncoder>) (encoder) -> {
                            encoder.writeArrayStart().writeArrayEnd();
                        },
                        "[]"),
                Arguments.of(
                        (Consumer<JsonEncoder>) (encoder) -> {
                            encoder.writeObjectStart()
                                    .writeString("key")
                                    .writeColon()
                                    .writeNumber(5.51, 3)
                                    .writeObjectEnd();
                        },
                        "{\"key\":5.510}"),
                Arguments.of(
                        (Consumer<JsonEncoder>) (encoder) -> {
                            encoder.writeObjectStart()
                                    .writeString("nullKey")
                                    .writeColon()
                                    .writeNull()
                                    .writeComma()
                                    .writeString("boolKey")
                                    .writeColon()
                                    .writeBoolean(false)
                                    .writeObjectEnd();
                        },
                        "{\"nullKey\":null,\"boolKey\":false}"),
                Arguments.of(
                        (Consumer<JsonEncoder>) (encoder) -> {
                            encoder.writeObjectStart()
                                    .writeObjectEntry("boolKey", true)
                                    .writeComma()
                                    .writeObjectEntry("boolKey", false)
                                    .writeComma()
                                    .writeObjectEntry("int", 505)
                                    .writeComma()
                                    .writeObjectEntry("double", 5.24, 4)
                                    .writeObjectEnd();
                        },
                        "{\"boolKey\":true,\"boolKey\":false,\"int\":505,\"double\":5.2400}"),
                Arguments.of(
                        (Consumer<JsonEncoder>) (encoder) -> {
                            encoder.writeObjectStart()
                                    .writeObjectEntry(new ViewString("boolKey"), true)
                                    .writeComma()
                                    .writeObjectEntry(new ViewString("boolKey"), false)
                                    .writeComma()
                                    .writeObjectEntry(new ViewString("int"), 505)
                                    .writeComma()
                                    .writeObjectEntry(new ViewString("double"), 5.24, 4)
                                    .writeComma()
                                    .writeObjectEntry(new ViewString("long"), 5555L)
                                    .writeObjectEnd();
                        },
                        "{\"boolKey\":true,\"boolKey\":false,\"int\":505,\"double\":5.2400,\"long\":5555}"),
                Arguments.of(
                        (Consumer<JsonEncoder>) (encoder) -> {
                            encoder.writeObjectStart()
                                    .writeString("nested")
                                    .writeColon()
                                    .writeObjectStart()
                                    .writeString("arrayObj")
                                    .writeColon()
                                    .writeObjectStart()
                                    .writeString("arr")
                                    .writeColon()
                                    .writeArrayStart()
                                    .writeNumber(1)
                                    .writeComma()
                                    .writeNumber(2)
                                    .writeComma()
                                    .writeNumber(3)
                                    .writeComma()
                                    .writeNumber(5666L)
                                    .writeArrayEnd()
                                    .writeObjectEnd()
                                    .writeObjectEnd()
                                    .writeObjectEnd();
                        },
                        "{\"nested\":{\"arrayObj\":{\"arr\":[1,2,3,5666]}}}"));
    }

    @ParameterizedTest
    @MethodSource("testJsonEncoderArguments")
    void testJsonEncoder(Consumer<JsonEncoder> consumer, String result) {
        ByteBuffer buffer = ByteBuffer.allocate(1 << 12);
        JsonEncoder encoder = new JsonEncoder();
        encoder.wrap(buffer);

        consumer.accept(encoder);
        buffer.flip();
        assertEquals(result, StandardCharsets.UTF_8.decode(buffer).toString());
    }
}

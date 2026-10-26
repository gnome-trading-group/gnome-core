package group.gnometrading.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class CauseChainTest {

    @Test
    void namesEveryCauseOutermostFirst() {
        Throwable error = new IllegalStateException(
                "gave up", new RuntimeException("connect", new IOException("HTTP/1.1 401 body: bad key")));

        assertEquals(
                "IllegalStateException: gave up <- RuntimeException: connect"
                        + " <- IOException: HTTP/1.1 401 body: bad key",
                CauseChain.of(error));
    }

    @Test
    void aCauseWithoutAMessageIsNamedAlone() {
        assertEquals(
                "IllegalStateException <- IOException",
                CauseChain.of(new IllegalStateException(null, new IOException())));
    }

    @Test
    void aCycleOfCausesStopsAtTheDepthLimit() {
        RuntimeException outer = new RuntimeException("a");
        RuntimeException inner = new RuntimeException("b", outer);
        outer.initCause(inner);

        assertEquals(8, CauseChain.of(outer).split(" <- ").length);
    }
}

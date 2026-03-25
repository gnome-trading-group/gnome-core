package group.gnometrading.codecs.json;

import group.gnometrading.pools.Pool;
import group.gnometrading.pools.PoolNode;
import group.gnometrading.pools.SingleThreadedObjectPool;
import group.gnometrading.strings.ExpandingMutableString;
import group.gnometrading.strings.GnomeString;
import group.gnometrading.strings.MutableString;
import java.nio.ByteBuffer;

/**
 * JsonDecoder is used to walk a JSON tree. This can *only* be used sequentially to walk the path of a JSON tree.
 */
public final class JsonDecoder {

    private static final byte CH_SPACE = ' ';
    private static final byte CH_LINEFEED = '\n';
    private static final byte CH_TAB = '\t';
    private static final byte CH_CARRIAGE_RETURN = '\r';
    private static final int DEFAULT_NODES = 100;
    private static final int DEFAULT_NODE_CAPACITY = 200;
    private static final byte NULL_BYTE = 0;

    private final Pool<JsonNode> jsonNodePool;
    private ByteBuffer byteBuffer;

    public JsonDecoder() {
        this(DEFAULT_NODES);
    }

    public JsonDecoder(int defaultCapacity) {
        this.jsonNodePool = new SingleThreadedObjectPool<>(JsonNode::new, defaultCapacity);
    }

    public JsonNode wrap(final ByteBuffer newByteBuffer) {
        this.byteBuffer = newByteBuffer;
        return consumeNode();
    }

    private JsonNode consumeNode() {
        return consumeNode(NULL_BYTE);
    }

    private JsonNode consumeNode(final byte closing) {
        final PoolNode<JsonNode> poolNode = jsonNodePool.acquire();
        final JsonNode node = poolNode.getItem();
        node.wrap(this.byteBuffer, poolNode, closing);
        return node;
    }

    private void consumeWhitespace() {
        while (byteBuffer.hasRemaining() && isWhitespace(byteBuffer.get(byteBuffer.position()))) {
            byteBuffer.get();
        }
    }

    private void consumeUntilNextItem(final byte closingChar) {
        while (byteBuffer.hasRemaining()) {
            if (this.byteBuffer.get(this.byteBuffer.position()) == closingChar) {
                return;
            }

            final byte at = this.byteBuffer.get();
            if (at == ',') {
                break;
            } else if (at == '[') {
                consumeRecursively((byte) ']');
            } else if (at == '{') {
                consumeRecursively((byte) '}');
            }
        }
    }

    private boolean isWhitespace(final byte value) {
        return value == CH_SPACE || value == CH_TAB || value == CH_LINEFEED || value == CH_CARRIAGE_RETURN;
    }

    private void consume(final byte target) {
        while (byteBuffer.hasRemaining()) {
            final byte at = byteBuffer.get();
            if (at == target) {
                return;
            }
        }
    }

    private void consumeRecursively(final byte target) {
        while (byteBuffer.hasRemaining()) {
            final byte at = byteBuffer.get();
            if (at == target) {
                return;
            }

            if (at == '[') {
                consumeRecursively((byte) ']');
            } else if (at == '{') {
                consumeRecursively((byte) '}');
            } else if (at == '\\') {
                if (byteBuffer.hasRemaining() && byteBuffer.get(byteBuffer.position()) == target) {
                    byteBuffer.get();
                }
            }
        }
    }

    public final class JsonNode implements AutoCloseable {
        private final MutableString name;
        private final ExpandingMutableString value;
        private final JsonObject jsonObject;
        private final JsonArray jsonArray;

        private ByteBuffer byteBuffer;
        private PoolNode<JsonNode> parent;
        private byte closing;

        public JsonNode() {
            this.name = new MutableString(DEFAULT_NODE_CAPACITY); // Over 200 byte key, I quit
            this.value = new ExpandingMutableString(DEFAULT_NODE_CAPACITY); // TODO: Profile this? Store this somewhere?
            this.jsonObject = new JsonObject();
            this.jsonArray = new JsonArray();
        }

        public void wrap(final ByteBuffer newByteBuffer, final PoolNode<JsonNode> newParent, final byte newClosing) {
            this.name.reset();
            this.value.reset();
            this.byteBuffer = newByteBuffer;
            this.parent = newParent;
            this.closing = newClosing;
        }

        @Override
        public void close() {
            if (this.closing != NULL_BYTE) {
                consumeUntilNextItem(this.closing);
            }
            jsonNodePool.release(this.parent);
        }

        public GnomeString getName() {
            return name;
        }

        public int asInt() {
            consumeWhitespace();
            boolean sign = false;
            if (byteBuffer.get(byteBuffer.position()) == '-') {
                sign = true;
                byteBuffer.get();
            }

            int result = 0;
            while (byteBuffer.hasRemaining() && isNumber(byteBuffer.get(byteBuffer.position()))) {
                byte at = byteBuffer.get();
                result = 10 * result + at - '0';
            }
            return sign ? -result : result;
        }

        public long asFixedPointLong(final long scalingFactor) {
            consumeWhitespace();
            boolean sign = false;
            if (byteBuffer.get(byteBuffer.position()) == '-') {
                sign = true;
                byteBuffer.get();
            }

            long result = 0;
            while (byteBuffer.hasRemaining() && isNumber(byteBuffer.get(byteBuffer.position()))) {
                final byte at = byteBuffer.get();
                result = 10 * result + (at - '0') * scalingFactor;
            }

            if (byteBuffer.hasRemaining() && byteBuffer.get(byteBuffer.position()) == '.') {
                byteBuffer.get();
                long fractionalMultiplier = scalingFactor;
                while (byteBuffer.hasRemaining() && isNumber(byteBuffer.get(byteBuffer.position()))) {
                    fractionalMultiplier /= 10;
                    final byte at = byteBuffer.get();
                    result += (at - '0') * fractionalMultiplier;
                }
            }

            return sign ? -result : result;
        }

        public long asLong() {
            consumeWhitespace();
            boolean sign = false;
            if (byteBuffer.get(byteBuffer.position()) == '-') {
                sign = true;
                byteBuffer.get();
            }

            long result = 0;
            while (byteBuffer.remaining() > 0 && isNumber(byteBuffer.get(byteBuffer.position()))) {
                final byte at = byteBuffer.get();
                result = 10 * result + at - '0';
            }

            return sign ? -result : result;
        }

        public double asDouble() {
            // I will not be handling E's here, sorry!
            consumeWhitespace();
            boolean sign = false;
            if (byteBuffer.get(byteBuffer.position()) == '-') {
                sign = true;
                byteBuffer.get();
            }

            int result = 0;
            while (byteBuffer.hasRemaining() && isNumber(byteBuffer.get(byteBuffer.position()))) {
                byte at = byteBuffer.get();
                result = 10 * result + at - '0';
            }

            double remainder = 0;
            if (byteBuffer.hasRemaining() && byteBuffer.get(byteBuffer.position()) == '.') {
                int divisor = 10;
                byteBuffer.get();
                while (byteBuffer.hasRemaining() && isNumber(byteBuffer.get(byteBuffer.position()))) {
                    byte at = byteBuffer.get();
                    remainder += (double) (at - '0') / divisor;
                    divisor *= 10;
                }
            }

            return sign ? -(result + remainder) : result + remainder;
        }

        private boolean isNumber(final byte byteValue) {
            return byteValue >= '0' && byteValue <= '9';
        }

        public GnomeString asString() {
            consume((byte) '"');

            while (byteBuffer.remaining() > 0) {
                byte at = byteBuffer.get();
                if (at == '"') { // TODO: Handle escape characters
                    break;
                } else {
                    this.value.append(at);
                }
            }
            return this.value;
        }

        public JsonObject asObject() {
            jsonObject.wrap(byteBuffer);
            return jsonObject;
        }

        public JsonArray asArray() {
            jsonArray.wrap(byteBuffer);
            return jsonArray;
        }

        public boolean asBoolean() {
            consumeWhitespace();
            return byteBuffer.get() == 't'; // lol, tFalse == true
        }

        public boolean isNull() {
            consumeWhitespace();
            return byteBuffer.get(byteBuffer.position()) == 'n'
                    && byteBuffer.get(byteBuffer.position() + 1) == 'u'
                    && byteBuffer.get(byteBuffer.position() + 2) == 'l'
                    && byteBuffer.get(byteBuffer.position() + 3) == 'l';
        }
    }

    public final class JsonObject implements AutoCloseable {

        private ByteBuffer byteBuffer;

        public void wrap(final ByteBuffer newByteBuffer) {
            this.byteBuffer = newByteBuffer;
            consume((byte) '{');
        }

        public boolean hasNextKey() {
            consumeWhitespace();
            return byteBuffer.get(byteBuffer.position()) != '}';
        }

        public JsonNode nextKey() {
            final JsonNode node = consumeNode((byte) '}');
            consume((byte) '"');
            while (true) {
                final byte at = byteBuffer.get();
                if (at == '"') {
                    break;
                } else {
                    node.name.append(at);
                }
            }
            consume((byte) ':');

            return node;
        }

        @Override
        public void close() {
            consumeRecursively((byte) '}');
        }
    }

    public final class JsonArray implements AutoCloseable {

        private ByteBuffer byteBuffer;

        public void wrap(final ByteBuffer newByteBuffer) {
            this.byteBuffer = newByteBuffer;
            consume((byte) '[');
        }

        public JsonNode nextItem() {
            consumeWhitespace();
            return consumeNode((byte) ']');
        }

        public boolean hasNextItem() {
            consumeWhitespace();
            return byteBuffer.get(byteBuffer.position()) != ']';
        }

        @Override
        public void close() {
            consumeRecursively((byte) ']');
        }
    }
}

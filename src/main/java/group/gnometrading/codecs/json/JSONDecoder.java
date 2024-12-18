package group.gnometrading.codecs.json;

import group.gnometrading.pools.Pool;
import group.gnometrading.pools.PoolNode;
import group.gnometrading.pools.SingleThreadedObjectPool;
import group.gnometrading.strings.ExpandingMutableString;
import group.gnometrading.strings.GnomeString;
import group.gnometrading.strings.MutableString;

import java.nio.ByteBuffer;

/**
 * JSONDecoder is used to walk a JSON tree. This can *only* be used sequentially to walk the path of a JSON tree.
 */
public class JSONDecoder {

    private static final byte CH_SPACE = ' ';
    private static final byte CH_LINEFEED = '\n';
    private static final byte CH_TAB = '\t';
    private static final byte CH_CARRIAGE_RETURN = '\r';
    private static final int DEFAULT_NODES = 100;
    private static final byte NULL_BYTE = 0;

    private final Pool<JSONNode> jsonNodePool;
    private ByteBuffer byteBuffer;

    public JSONDecoder() {
        this(DEFAULT_NODES);
    }

    public JSONDecoder(int defaultCapacity) {
        this.jsonNodePool = new SingleThreadedObjectPool<>(JSONNode::new, defaultCapacity);
    }

    public JSONNode wrap(final ByteBuffer buffer) {
        this.byteBuffer = buffer;
        return consumeNode();
    }

    private JSONNode consumeNode() {
        return consumeNode(NULL_BYTE);
    }

    private JSONNode consumeNode(final byte closing) {
        final PoolNode<JSONNode> poolNode = jsonNodePool.acquire();
        final JSONNode node = poolNode.getItem();
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

    private boolean isWhitespace(final byte b) {
        return b == CH_SPACE || b == CH_TAB || b == CH_LINEFEED || b == CH_CARRIAGE_RETURN;
    }

    private void consume(final byte target) {
        while (byteBuffer.hasRemaining()) {
            final byte b = byteBuffer.get();
            if (b == target) {
                return;
            }
        }
    }

    private void consumeRecursively(final byte target) {
        while (byteBuffer.hasRemaining()) {
            final byte b = byteBuffer.get();
            if (b == target) {
                return;
            }

            if (b == '[') {
                consumeRecursively((byte) ']');
            } else if (b == '{') {
                consumeRecursively((byte) '}');
            } else if (b == '\\') {
                if (byteBuffer.hasRemaining() && byteBuffer.get(byteBuffer.position()) == target) {
                    byteBuffer.get();
                }
            }
        }
    }

    public class JSONNode implements AutoCloseable {
        private final MutableString name;
        private final ExpandingMutableString value;
        private final JSONArray jsonArray;
        private final JSONObject jsonObject;

        private ByteBuffer byteBuffer;
        private PoolNode<JSONNode> parent;
        private byte closing;

        public JSONNode() {
            this.name = new MutableString(200); // Over 200 byte key, I quit
            this.value = new ExpandingMutableString(200); // TODO: Profile this? Store this somewhere?
            this.jsonObject = new JSONObject();
            this.jsonArray = new JSONArray();
        }

        public void wrap(final ByteBuffer byteBuffer, final PoolNode<JSONNode> parent, final byte closing) {
            this.name.reset();
            this.value.reset();
            this.byteBuffer = byteBuffer;
            this.parent = parent;
            this.closing = closing;
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

            int value = 0;
            while (byteBuffer.remaining() > 0 && isNumber(byteBuffer.get(byteBuffer.position()))) {
                byte at = byteBuffer.get();
                value = 10 * value + at - '0';
            }
            return sign ? -value : value;
        }

        public long asLong() {
            consumeWhitespace();
            boolean sign = false;
            if (byteBuffer.get(byteBuffer.position()) == '-') {
                sign = true;
                byteBuffer.get();
            }

            long value = 0;
            while (byteBuffer.remaining() > 0 && isNumber(byteBuffer.get(byteBuffer.position()))) {
                final byte at = byteBuffer.get();
                value = 10 * value + at - '0';
            }

            return sign ? -value : value;
        }

        public double asDouble() {
            // I will not be handling E's here, sorry!
            consumeWhitespace();
            boolean sign = false;
            if (byteBuffer.get(byteBuffer.position()) == '-') {
                sign = true;
                byteBuffer.get();
            }

            int value = 0;
            while (byteBuffer.remaining() > 0 && isNumber(byteBuffer.get(byteBuffer.position()))) {
                byte at = byteBuffer.get();
                value = 10 * value + at - '0';
            }

            double remainder = 0;
            if (byteBuffer.remaining() > 0 && byteBuffer.get(byteBuffer.position()) == '.') {
                int divisor = 10;
                byteBuffer.get();
                while (byteBuffer.remaining() > 0 && isNumber(byteBuffer.get(byteBuffer.position()))) {
                    byte at = byteBuffer.get();
                    remainder += (double) (at - '0') / divisor;
                    divisor *= 10;
                }
            }

            return sign ? -(value + remainder) : value + remainder;
        }

        private boolean isNumber(final byte b) {
            return b >= '0' && b <= '9';
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

        public JSONObject asObject() {
            jsonObject.wrap(byteBuffer);
            return jsonObject;
        }

        public JSONArray asArray() {
            jsonArray.wrap(byteBuffer);
            return jsonArray;
        }

        public boolean asBoolean() {
            consumeWhitespace();
            return byteBuffer.get() == 't'; // lol, tFalse == true
        }

        public boolean isNull() {
            consumeWhitespace();
            return byteBuffer.get(byteBuffer.position()) == 'n' &&
                    byteBuffer.get(byteBuffer.position() + 1) == 'u' &&
                    byteBuffer.get(byteBuffer.position() + 2) == 'l' &&
                    byteBuffer.get(byteBuffer.position() + 3) == 'l';
        }
    }

    public class JSONObject implements AutoCloseable {

        private ByteBuffer byteBuffer;

        public void wrap(final ByteBuffer byteBuffer) {
            this.byteBuffer = byteBuffer;
            consume((byte) '{');
        }

        public boolean hasNextKey() {
            consumeWhitespace();
            return byteBuffer.get(byteBuffer.position()) != '}';
        }

        public JSONNode nextKey() {
            final JSONNode node = consumeNode((byte) '}');
            consume((byte) '"');
            while (true) {
                final byte b = byteBuffer.get();
                if (b == '"') {
                    break;
                } else {
                    node.name.append(b);
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

    public class JSONArray implements AutoCloseable {

        private ByteBuffer byteBuffer;

        public void wrap(final ByteBuffer byteBuffer) {
            this.byteBuffer = byteBuffer;
            consume((byte) '[');
        }

        public JSONNode nextItem() {
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

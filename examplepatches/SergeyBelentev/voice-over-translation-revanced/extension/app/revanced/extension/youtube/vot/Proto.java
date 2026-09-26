package app.revanced.extension.youtube.vot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Minimal bounded protobuf wire codec. Unknown fields are skipped for forward compatibility. */
public final class Proto {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    public Proto number(int field, long value) { varint(field << 3); varint(value); return this; }
    public Proto text(int field, String value) { return bytes(field, value.getBytes(StandardCharsets.UTF_8)); }
    public Proto bytes(int field, byte[] value) {
        varint((field << 3) | 2); varint(value.length); out.write(value, 0, value.length); return this;
    }
    public Proto decimal(int field, double value) {
        varint((field << 3) | 1);
        byte[] bytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(value).array();
        out.write(bytes, 0, bytes.length); return this;
    }
    private void varint(long value) {
        while ((value & ~127L) != 0) { out.write(((int) value & 127) | 128); value >>>= 7; }
        out.write((int) value);
    }
    public byte[] build() { return out.toByteArray(); }

    public static final class Reader {
        private final byte[] data;
        private int pos, wire;
        public int field;
        public Reader(byte[] data) { this.data = data; }
        public boolean next() throws IOException {
            if (pos == data.length) return false;
            long tag = varint();
            if (tag < 8 || tag > 0xffffffffL) throw new IOException("Invalid protobuf tag");
            field = (int) (tag >>> 3); wire = (int) (tag & 7); return true;
        }
        private void require(int type) throws IOException {
            if (wire != type) throw new IOException("Unexpected protobuf wire type");
        }
        public long number() throws IOException { require(0); return varint(); }
        public String text() throws IOException {
            require(2); long n = varint(); check(n);
            String value = new String(data, pos, (int) n, StandardCharsets.UTF_8); pos += (int) n; return value;
        }
        public void skip() throws IOException {
            long n;
            switch (wire) {
                case 0: varint(); return;
                case 1: n = 8; break;
                case 2: n = varint(); break;
                case 5: n = 4; break;
                default: throw new IOException("Unsupported protobuf wire type");
            }
            check(n); pos += (int) n;
        }
        private void check(long n) throws IOException {
            if (n < 0 || n > data.length - pos) throw new IOException("Truncated protobuf");
        }
        private long varint() throws IOException {
            long result = 0;
            for (int i = 0; i < 10; i++) {
                check(1); int b = data[pos++] & 255;
                if (i == 9 && b > 1) throw new IOException("Protobuf varint overflow");
                result |= (long) (b & 127) << (7 * i);
                if ((b & 128) == 0) return result;
            }
            throw new IOException("Invalid protobuf varint");
        }
    }
}

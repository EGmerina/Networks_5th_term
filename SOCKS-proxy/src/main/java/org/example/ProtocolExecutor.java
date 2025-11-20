package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ProtocolExecutor {
    private static final Logger logger = LogManager.getLogger(ProtocolExecutor.class);

//    public static enum AType {
//        IPv4((byte) 0x01),
//        DOMAIN((byte) 0x03),
//        IPv6((byte) 0x04);
//
//        private final byte code;
//
//        AType(byte code) {
//            this.code = code;
//        }
//
//        public byte getCode() {
//            return code;
//        }
//    }

//    public static boolean isReadyToRead(ByteBuffer buffer, int expectedNum) {
//        if (buffer.remaining() < expectedNum) {
//            buffer.reset();
//            buffer.compact();
//            return false;
//        }
//        return true;
//    }

    public static ByteBuffer getOkMethodMessage() {
        return ByteBuffer.wrap(new byte[]{0x05, 0x00});
    }

    public static ByteBuffer getNotOkMethodMessage() {
        return ByteBuffer.wrap(new byte[]{0x05, (byte) 0xff});
    }

    public static boolean checkMethod(ByteBuffer buffer) throws IOException { //TODO тут тоже покрасивее сделать
        logger.trace("checking method....");

        if (buffer.remaining() < 2) return false;
        buffer.mark();
        byte ver = buffer.get();
        byte methodsNum = buffer.get();
        if (buffer.remaining() < methodsNum) {
            buffer.reset();
            return false;
        }

        boolean noauth = false;
        for (int i = 0; i < methodsNum; i++) {
            byte m = buffer.get();
            if (m == 0x00) noauth = true;
        }
        //logger.trace("message from client {}, arr {}", buffer, buffer.array());
        if (ver != 0x05) {
            logger.trace("version socks isn't 5 ");
            throw new IOException("version socks isn't 5 ");
        } else if (!noauth) {
            logger.trace(" unsupported connection");
            throw new IOException(" unsupported connection");
        }
        return true;
    }

    public static Byte getAType(ByteBuffer buffer) throws IOException {
        buffer.mark();
        if (buffer.remaining() < 4) {
            buffer.reset();
            buffer.compact();
            return null;
        }

        byte ver = buffer.get();
        byte cmd = buffer.get();
        byte rsv = buffer.get();
        byte atyp = buffer.get();

        if (ver != 0x05) throw new IOException("invalid SOCKS version");
        if (cmd != 0x01) throw new IOException("only CONNECT supported");
        return atyp;
    }


    public static InetSocketAddress getInetSocketAddress(ByteBuffer buffer) {
        if (buffer.remaining() < 4 + 2) {
            buffer.reset();
            buffer.compact();
            return null;
        }

        byte[] ip4 = new byte[4];
        buffer.get(ip4);
        int port = (buffer.get() & 0xFF) << 8 | (buffer.get() & 0xFF);

        String ipStr =
                (ip4[0] & 0xff) + "." +
                        (ip4[1] & 0xff) + "." +
                        (ip4[2] & 0xff) + "." +
                        (ip4[3] & 0xff);

        return new InetSocketAddress(ipStr, port);
    }

    public static String getDomain(ByteBuffer buffer) {
        if (buffer.remaining() < 1) {
            buffer.reset();
            buffer.compact();
            return null;
        }

        int len = buffer.get() & 0xff;

        if (buffer.remaining() < len + 2) {
            buffer.reset();
            buffer.compact();
            return null;
        }

        byte[] dom = new byte[len];
        buffer.get(dom);
        return new String(dom, StandardCharsets.US_ASCII);
    }

    public static int getPort(ByteBuffer buffer) {
        return (buffer.get() & 0xFF) << 8 | (buffer.get() & 0xFF);
    }

    public static ByteBuffer getResponsePacket() {
        ByteBuffer reply = ByteBuffer.allocate(10);
        reply.put((byte) 0x05); // VER
        reply.put((byte) 0x00); // REP = succeeded
        reply.put((byte) 0x00); // RSV
        reply.put((byte) 0x01); // ATYP = IPv4
        reply.putInt(0);        // BND.ADDR = 0.0.0.0
        reply.putShort((short) 0); // BND.PORT = 0
        return reply;
    }

}

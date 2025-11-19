package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class ProtocolExecutor {
    private static final Logger logger = LogManager.getLogger(ProtocolExecutor.class);

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

    public static byte getAType(ByteBuffer buffer) throws IOException {
        if (buffer.remaining() < 4) {
            logger.trace("wait data...");
            return 0x07;
        }

        buffer.mark();
        byte ver = buffer.get();
        byte cmd = buffer.get();
        byte rsv = buffer.get();
        byte atyp = buffer.get();

        if (ver != 0x05) throw new IOException("invalid socks5 version");
        if (cmd != 0x01) { // only CONNECT supported
            throw new IOException("unsupported command");
        }
        return atyp;
    }


    public static InetSocketAddress getInetSocketAddress(ByteBuffer buffer) {
        byte[] ip4 = new byte[4];
        buffer.get(ip4);
        int port = (buffer.get() & 0xFF) << 8 | (buffer.get() & 0xFF);

        String ipStr = (ip4[0] & 0xff) + "." +
                (ip4[1] & 0xff) + "." +
                (ip4[2] & 0xff) + "." +
                (ip4[3] & 0xff);

        return new InetSocketAddress(ipStr, port);
    }
}

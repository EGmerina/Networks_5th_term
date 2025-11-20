package org.example;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class Connection {
    private static final int BUFFER_SIZE = 32 * 1024;
    State state = State.WAIT_AUTH;
    SocketChannel clientChannel;
    SocketChannel targetChannel;

    // inBuffer: Client -> Proxy -> Target
    ByteBuffer inBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    // outBuffer: Target -> Proxy -> Client
    ByteBuffer outBuffer = ByteBuffer.allocate(BUFFER_SIZE);

    int destinationPort;
    int dnsID = 0;
    boolean isHalfClosed = false;

    SelectionKey clientKey() {
        return clientChannel.keyFor(clientChannel.provider() == null ? null : ((SocketChannel) clientChannel).keyFor(null).selector());
    }
}

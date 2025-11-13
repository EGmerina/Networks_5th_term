package org.example;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Queue;

public class Connection { // private static final int MAX_QUEUE_SIZE = 100;
    private final ByteBuffer handshakeBuffer = ByteBuffer.allocate(256); //for handshake and so on
    private SocketChannel remote = null;
    private final Queue<ByteBuffer> pending = new ArrayDeque<>();
    private ProtocolStage stage = ProtocolStage.METHOD;

    public ProtocolStage getStage() {
        return stage;
    }

    public Queue<ByteBuffer> getPending() {
        return pending;
    }

    public SocketChannel getRemote() {
        return remote;
    }

    public ByteBuffer getHandshakeBuffer() {
        return handshakeBuffer;
    }
}

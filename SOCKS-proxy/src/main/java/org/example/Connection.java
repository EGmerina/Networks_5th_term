package org.example;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Queue;

public class Connection { // private static final int MAX_QUEUE_SIZE = 100;
    private final ByteBuffer handshakeBuffer = ByteBuffer.allocate(256); //for handshake and so on
    private SocketChannel remote = null;
    private SocketChannel client = null;
    private final Queue<ByteBuffer> pending = new ArrayDeque<>();
    private ProtocolStage stage = ProtocolStage.METHOD;

    public Connection(SocketChannel client) {
        this.client = client;
    }

    public ProtocolStage getStage() {
        return stage;
    }

    public SocketChannel getClient() {
        return client;
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

    public void setStage(ProtocolStage stage) {
        this.stage = stage;
    }

    public void setRemote(SocketChannel remote) {
        this.remote = remote;
    }

}

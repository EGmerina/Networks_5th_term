package org.example;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;

public class Connection { // private static final int MAX_QUEUE_SIZE = 100;
    private final ByteBuffer handshakeBuffer = ByteBuffer.allocate(256); //for handshake and so on
    private SocketChannel remote = null;
    private SocketChannel client = null;
    private final ArrayDeque<ByteBuffer> pending = new ArrayDeque<>();
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

    public void addToQueue(ByteBuffer buffer) {
        pending.add(buffer);
    }

    public void pushToQueue(ByteBuffer buffer) {
        pending.push(buffer);
    }

    public ByteBuffer getFromQueue(SelectionKey key) {
        ByteBuffer buffer = pending.poll();
        if (pending.isEmpty()) {
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE); //TODO плохо
        }
        return buffer;
    }

    public void clearQueue() {
        pending.clear();
    }
}

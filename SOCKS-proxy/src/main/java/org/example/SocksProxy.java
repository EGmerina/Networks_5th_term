package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class SocksProxy {
    private static final Logger logger = LogManager.getLogger(SocksProxy.class);
    private static final int DATA_BUFFER_SIZE = 8192;
    private final Selector selector;

    public SocksProxy() {
        try {
            selector = Selector.open();
        } catch (IOException e) {
            logger.error("can't open selector");
            throw new RuntimeException(e);
        }
    }

    public void start(int port) {
        logger.info("SocksProxy started on port {}", port);
        try (selector; ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(new InetSocketAddress(port));
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            while (true) {
                selector.select();
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectedKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    if (!key.isValid()) {
                        iterator.remove();
                        continue;
                    }
                    try {
                        if (key.isAcceptable()) {
                            handleAccept((ServerSocketChannel) key.channel());
                        } else if (key.isConnectable()) {
                            handleConnect(key);
                        } else if (key.isReadable()) {
                            handleRead(key);
                        } else if (key.isWritable()) {
                            handleWrite(key);
                        }
                    } catch (IOException e) {
                        logger.warn("client close connection unexpected: " + e.getMessage());
                        closeConnection(key);
                    }
                }


            }

        } catch (IOException e) {
            logger.error("exception during working server: " + e.getMessage() + " and suppressed:" + e.getSuppressed());
            throw new RuntimeException(e);
        }
    }


    private void handleAccept(ServerSocketChannel channel) {
        try {
            SocketChannel clientSocket = channel.accept();
            clientSocket.configureBlocking(false);
            Connection newConnection = new Connection();
            clientSocket.register(selector, SelectionKey.OP_READ, newConnection); //пока только handshake
            logger.info("client {} was accepted", clientSocket.getRemoteAddress());
        } catch (IOException e) {
            logger.error("can't accept socketChannel ");
            throw new RuntimeException(e);
        }

    }


    private void handleConnect(SelectionKey key) {
    }

    private void handleWrite(SelectionKey key) {
    }


    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        Connection currentConnection = (Connection) key.attachment();
        ProtocolStage stage = currentConnection.getStage();
        switch (stage) {
            case METHOD -> {
                sendMethodToClient();
            }
            case STATUS -> {
                sendStatusToClient();
            }
            case CONNECTING -> {
                connectToRemote();
            }
            case RELAY -> {
                relayData(socketChannel, currentConnection);
            }
            case CLOSING -> {
            }
            default -> {
                logger.error("unknown protocol stage of client {}", socketChannel.getRemoteAddress());
            }

        }

    }

    private void sendMethodToClient() {
    }

    private void sendStatusToClient() {
        
        
    }

    private void connectToRemote() {
        
        
    }


    private void relayData(SocketChannel socketChannel, Connection currentConnection) throws IOException {
        SocketChannel remoteChannel = currentConnection.getRemote();
        if (!remoteChannel.isConnected()) {
            return;
        }
        ByteBuffer buffer = ByteBuffer.allocate(DATA_BUFFER_SIZE);
        int bytesNum = socketChannel.read(buffer);
        if (bytesNum == -1) {
            throw new IOException("end of stream");
        } else if (bytesNum == 0) {
            return;
        }
        buffer.flip();
        remoteChannel.write(buffer);
        if (buffer.hasRemaining()) {
            currentConnection.getPending().add(buffer);
            SelectionKey remoteKey = remoteChannel.keyFor(selector);
            remoteKey.interestOps(remoteKey.interestOps() | SelectionKey.OP_WRITE);
        }

    }

    private void closeConnection(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        Connection currentConnection = (Connection) key.attachment();
        currentConnection.getPending().clear();
        currentConnection.getHandshakeBuffer().clear();
        SocketChannel remote = currentConnection.getRemote();
        if (remote != null) {
            remote.close();
        }
        socketChannel.close();
        key.cancel();
    }
}

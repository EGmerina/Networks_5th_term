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
    private static final int CLIENT_BUFFER_SIZE = 1024;

    public void start(int port) {
        logger.info("SocksProxy started on port {}", port);
        try (Selector selector = Selector.open(); ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
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
                    if (key.isAcceptable()) {
                        handleAccept((ServerSocketChannel) key.channel(), selector);
                    }
                    if (key.isReadable()) {
                        handleRead(key);
                    }
                    //TODO закрывать channel при ошибке
                }

            }


        } catch (IOException e) {
            logger.error("exception during working server: " + e.getMessage() + " and suppressed:" + e.getSuppressed());
            throw new RuntimeException(e);
        }
    }

    private void handleAccept(ServerSocketChannel channel, Selector selector) {
        try {
            SocketChannel clientSocket = channel.accept();
            clientSocket.configureBlocking(false);
            clientSocket.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(CLIENT_BUFFER_SIZE)); //пока только handshake
            logger.info("client {} was accepted", clientSocket.getRemoteAddress());
        } catch (IOException e) {
            logger.error("can't accept socketChannel ");
            throw new RuntimeException(e);
        }

    }

    private void handleRead(SelectionKey key) {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();
        if (buffer == null) {
            logger.warn("buffer is null .why?");
            return;
        }
        int bytesReadNum = socketChannel.read(buffer);
        if (bytesReadNum == -1) {
            closeConnection(key);
            return;
        } else if (bytesReadNum > 0){
            buffer.flip();
            if()
        }

    }

    private void closeConnection(SelectionKey key) {
    }


}

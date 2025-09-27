package org.example;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class Server {
    private Logger logger = LogManager.getLogger(Server.class);

    public void start(int port) {
        ServerSocketChannel serverSocketChannel = null;
        Selector selector = null;
        try {
            serverSocketChannel = ServerSocketChannel.open();
            selector = Selector.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(new InetSocketAddress(port));
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        } catch (IOException e) {
            logger.error("can't start server");
            throw new RuntimeException(e);
        }
        while (true) {
            try {
                selector.select();
            } catch (IOException e) {
                logger.error("something wrong with selector. Maybe it was closed");
                throw new RuntimeException(e);
            }
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectedKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                if (!key.isValid()) {
                    iterator.remove();
                    continue;
                }
                if (key.isAcceptable()) {

                    try {
                        SocketChannel client = serverSocketChannel.accept();
                        client.configureBlocking(false);
                        client.register(selector, SelectionKey.OP_READ);
                    } catch (IOException e) {
                        logger.error("can't accept socketChannel");
                        throw new RuntimeException(e);
                    }

                }
                if (key.isReadable()) {
                    handleRead();
                }
            }
        }
    }

    private void handleRead() {
    }
}

package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;

public class Client {
    private Logger logger = LogManager.getLogger(Client.class);

    public void start(Path path, InetSocketAddress inetSocketAddress) {
        logger.info("client starts");
        Selector selector = null;
        SocketChannel socketChannel = null;
        try {
            selector = Selector.open();
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.connect(inetSocketAddress);
            socketChannel.register(selector, SelectionKey.OP_CONNECT);
        } catch (IOException e) {
            logger.error("can't open selector");
            throw new RuntimeException(e);
        }
        while (true) {
            try {
                selector.select();
            } catch (IOException e) {
                logger.error("selector can't select");
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
                if (key.isConnectable()) {
                    SocketChannel client = (SocketChannel) key.channel();
                    try {
                        if (client.finishConnect()) {
                            logger.info("connected to server");
                            client.register(selector, SelectionKey.OP_WRITE);
                        } else {
                            logger.info("wait connection to server...");
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (key.isWritable()) {
                }
                iterator.remove();
            }
        }

    }
}

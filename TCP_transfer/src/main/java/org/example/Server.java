package org.example;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class Server {
    private Logger logger = LogManager.getLogger(Server.class);

    public void start(int port) {
        logger.info("server starts");
        createUploadsDirectory();
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
                    ServerSocketChannel server = (ServerSocketChannel) key.channel();
                    try {
                        SocketChannel client = server.accept();
                        client.configureBlocking(false);
                        client.register(selector, SelectionKey.OP_READ);
                        logger.info("client {} was accepted", client.getRemoteAddress());
                    } catch (IOException e) {
                        logger.error("can't accept socketChannel");
                        throw new RuntimeException(e);
                    }

                }
                if (key.isReadable()) {
                    handleRead();
                }
                iterator.remove();
            }
        }
    }

    private void createUploadsDirectory() {
        Path pathToDirectory = Paths.get("uploads");
        if (!Files.exists(pathToDirectory)) {
            try {
                Files.createDirectory(pathToDirectory);
                logger.info("create directory 'uploads'");
            } catch (IOException e) {
                logger.error("can't create directory");
                throw new RuntimeException(e);
            }
        } else {
            logger.info("directory 'uploads' exists");
        }
    }

    private void handleRead() {
    }
}

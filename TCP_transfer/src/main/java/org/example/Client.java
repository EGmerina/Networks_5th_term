package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;


public class Client {
    private Logger logger = LogManager.getLogger(Client.class);

    public void start(Path path, InetSocketAddress inetSocketAddress) {
        logger.info("client starts");
        SocketChannel socketChannel = null;
        try {
            socketChannel = SocketChannel.open();
            socketChannel.connect(inetSocketAddress);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        logger.info("connected to server");
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            logger.error("File path is incorrect");
            throw new RuntimeException();
        }
        try {
            sendMetaData(socketChannel, path.getFileName().toString(), Files.size(path));
        } catch (IOException e) {
            logger.error("can't know file size");
            throw new RuntimeException(e);
        }

    }

    private void sendMetaData(SocketChannel socketChannel, String fileName, long size) {

    }

}

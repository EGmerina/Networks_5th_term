package org.example;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class Server {
    private static final ObjectMapper mapper = new ObjectMapper();
    private Logger logger = LogManager.getLogger(Server.class);
    private final ExecutorService threadPool = Executors.newFixedThreadPool(10);

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
                    SocketChannel socketChannel = (SocketChannel) key.channel();
                    threadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                handleRead(socketChannel);
                            } catch (IOException e) {
                            } finally {
                                try {
                                    logger.info("closing client {} ", socketChannel.getRemoteAddress());
                                    socketChannel.close();
                                } catch (IOException ex) {
                                    logger.error("can't close socket channel");
                                    throw new RuntimeException(ex);
                                }
                                key.cancel();

                            }
                        }
                    });
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
                logger.trace("create directory 'uploads'");
            } catch (IOException e) {
                logger.error("can't create directory");
                throw new RuntimeException(e);
            }
        } else {
            logger.trace("directory 'uploads' exists");
        }
    }

    private void handleRead(SocketChannel channel) throws IOException {
        try {
            ByteBuffer headerSizeBuffer = ByteBuffer.allocate(4);
            channel.read(headerSizeBuffer);
            int jsonHeaderSize = headerSizeBuffer.getInt();
            ByteBuffer jsonHeaderBuffer = ByteBuffer.allocate(jsonHeaderSize);
            channel.read(jsonHeaderBuffer);
            String jsonHeader = new String(jsonHeaderBuffer.array(), StandardCharsets.UTF_8);
            FileMetaData fileMetaData = mapper.readValue(jsonHeader, FileMetaData.class);
            if (fileMetaData.getFileName().length() > 4096 || fileMetaData.getFileSize() > 1024 * 1024 * 1024 * 1024) {   // Длина имени файла не превышает 4096 байт в кодировке UTF-8. Размер файла не более 1 терабайта.
                sendResponseToClient("NO", channel);
                throw new IOException("file too large or file name too long");
            }
            sendResponseToClient("OK", channel);
            сreateFile(fileMetaData.getFileName());
            try {
                receiveFileData(channel, fileMetaData);
            } catch (IOException e) {
                sendResponseToClient("FAIL", channel);
                logger.error(e);
            }
            sendResponseToClient("SUC", channel);
            logger.trace("downloading file was successful");

        } catch (IOException e) {
            logger.error("can't read json header");
            throw new RuntimeException(e);
        }
    }

    private void receiveFileData(SocketChannel channel, FileMetaData fileMetaData) throws IOException {
        long recBytesNum = 0;
        ByteBuffer recBuffer = ByteBuffer.allocate(8192);
        while (recBytesNum != fileMetaData.getFileSize()) {
            try {
                recBytesNum += channel.read(recBuffer);
            } catch (IOException e) {
                logger.error("error with reading file data");
                throw new RuntimeException(e);
            }
            if (recBytesNum > fileMetaData.getFileSize()) {
                throw new IOException("client sent more data than was going to");
            }
        }
    }

    private void сreateFile(String fileName) {
        Path pathToFile = Paths.get("uploads/" + fileName);
        if (!Files.exists(pathToFile)) {
            try {
                Files.createFile(pathToFile);
            } catch (IOException e) {
                logger.trace("can't create file " + fileName);
                throw new RuntimeException(e);
            }
            logger.trace("file " + fileName + " was created");
        } else {
            logger.trace("file " + fileName + " exists");
        }
    }

    private void sendResponseToClient(String response, SocketChannel socketChannel) {
        ByteBuffer buffer = ByteBuffer.wrap(response.getBytes());
        try {
            socketChannel.write(buffer);
        } catch (IOException ex) {
            logger.error("error when send response to client");
            throw new RuntimeException(ex);
        }
    }
}


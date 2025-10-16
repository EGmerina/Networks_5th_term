package org.example;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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
    private static final int BUFFER_SIZE = 8192;

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
                    key.cancel();
                    threadPool.execute(() -> {
                        try {
                            handleRead(socketChannel);
                        } catch (IOException e) {
                            logger.error("Error while handling client {}", socketChannel, e);
                        } finally {
                            try {
                                logger.info("closing client {} ", socketChannel.getRemoteAddress());
                                // отменяем ключ
                                socketChannel.close(); // закрываем канал
                            } catch (IOException ex) {
                                logger.error("can't close socket channel", ex);
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
            readFully(channel, headerSizeBuffer);
            // channel.read(headerSizeBuffer);
            int jsonHeaderSize = headerSizeBuffer.getInt();
            ByteBuffer jsonHeaderBuffer = ByteBuffer.allocate(jsonHeaderSize);
            // channel.read(jsonHeaderBuffer);
            readFully(channel, jsonHeaderBuffer);
            String jsonHeader = new String(jsonHeaderBuffer.array(), StandardCharsets.UTF_8);
            FileMetaData fileMetaData = mapper.readValue(jsonHeader, FileMetaData.class);
            if (fileMetaData.getFileName().length() > 4096 || fileMetaData.getFileSize() > (1024L * 1024 * 1024 * 1024)) {   // Длина имени файла не превышает 4096 байт в кодировке UTF-8. Размер файла не более 1 терабайта.
                sendResponseToClient("NO", channel);
                logger.trace("file name length {} ,  file size {}", fileMetaData.getFileName().length(), fileMetaData.getFileSize());
                throw new IOException("file too large or file name too long");
            }
            sendResponseToClient("OK", channel);
            try {
                receiveFileData(channel, fileMetaData);
            } catch (IOException e) {
                sendResponseToClient("FAIL", channel);
                logger.error(e);
            }
            sendResponseToClient("SUC", channel);
            logger.info("downloading file was successful");

        } catch (IOException e) {
            logger.error("can't read json header");
            throw new RuntimeException(e);
        }
    }

    private void receiveFileData(SocketChannel channel, FileMetaData fileMetaData) throws IOException {
        long recBytesNum = 0;
        //TODO тут надо правильно выбрать размер буфера и не читать лишнее
        ByteBuffer recBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        Path filePath = Paths.get("uploads", fileMetaData.getFileName());

        try (FileChannel fileChannel = FileChannel.open(filePath,
                StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            while (recBytesNum != fileMetaData.getFileSize()) {
                recBuffer.clear();
                logger.trace("try to get data...");

                int remaining = (int) Math.min(recBuffer.capacity(), fileMetaData.getFileSize() - recBytesNum);
                recBuffer.limit(remaining);

                recBytesNum += readFully(channel, recBuffer);
                logger.trace("totally get {} bytes", recBytesNum);

                while (recBuffer.hasRemaining()) {
                    fileChannel.write(recBuffer);
                }

                if (recBytesNum > fileMetaData.getFileSize()) {
                    throw new IOException("client sent more data than was going to");
                }
            }
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

    private long readFully(SocketChannel socketChannel, ByteBuffer buffer) {
        long recNum = 0;
        while (buffer.hasRemaining()) {
            logger.trace("reading data...received {} bytes", recNum);
            try {
                recNum += socketChannel.read(buffer);
            } catch (IOException e) {
                logger.error("can't read from channel");
                throw new RuntimeException(e);
            }
            if (recNum == -1) {
                logger.warn("client closed connection unexpectedly");
                throw new RuntimeException("Client closed connection before sending full data");
            }
        }
        buffer.flip();
        return recNum;
    }
}


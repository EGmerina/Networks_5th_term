package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// Формат сообщения:
// [4 байта - длина JSON] [JSON заголовок] [данные файла]

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
            FileMetaData fileMetaData = new FileMetaData(path.getFileName().toString(), Files.size(path));
            sendMetaData(socketChannel, fileMetaData);
            sendFileData(socketChannel, path);
        } catch (IOException e) {
            logger.error("can't know file size");
            throw new RuntimeException(e);
        }

    }

    private void sendFileData(SocketChannel socketChannel, Path path) {
        logger.trace("start send file data");
        try (FileChannel fileChannel = FileChannel.open(path)) {
            ByteBuffer fileBuffer = ByteBuffer.allocate(8192);
            while (fileChannel.read(fileBuffer) != -1) {
                fileBuffer.flip();
                while (fileBuffer.hasRemaining()) {
                    socketChannel.write(fileBuffer);
                    fileBuffer.compact();
                }
                fileBuffer.clear();
            }
        } catch (IOException e) {
            logger.error("error with send data");
            throw new RuntimeException(e);
        }
        logger.trace("file data was sent");
    }

    private void sendMetaData(SocketChannel socketChannel, FileMetaData fileMetaData) {
        logger.trace("start send meta data");
        String jsonHeader = null;
        try {
            jsonHeader = fileMetaData.getMetaDataInJsonFormat();
        } catch (JsonProcessingException e) {
            logger.error("can't convert fileMetaData to json format");
            throw new RuntimeException(e);
        }
        byte[] headerBytes = jsonHeader.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(4);
        ByteBuffer headerBuffer = ByteBuffer.wrap(headerBytes);
        buffer.putInt(headerBytes.length);
        buffer.flip();
        try {
            socketChannel.write(buffer);
            socketChannel.write(headerBuffer);
        } catch (IOException e) {
            logger.error("can't write header");
            throw new RuntimeException(e);
        }
        logger.trace("mata data was sent");
    }

}

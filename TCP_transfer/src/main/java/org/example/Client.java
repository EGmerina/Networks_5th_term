package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
// [4 байта - длина JSON] [JSON заголовок] [получить ответ сервера "OK"] [данные файла]
//TODO DNS
public class Client {
    private final Logger logger = LogManager.getLogger(Client.class);
    private static final int BUFFER_SIZE = 8192;

    public void start(Path path, InetSocketAddress inetSocketAddress) {
        logger.info("client starts");
        SocketChannel socketChannel = null;
        try {
            socketChannel = SocketChannel.open();
            socketChannel.connect(inetSocketAddress);
        } catch (IOException e) {
            System.out.println("server isn't working now, try to connect later");
            return;
        }
        logger.info("connected to server");
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            logger.error("File path is incorrect");
            throw new RuntimeException();
        }
        try {
            FileMetaData fileMetaData = new FileMetaData(path.getFileName().toString(), Files.size(path));
            sendMetaData(socketChannel, fileMetaData);
            String response = getServerResponse(socketChannel);
            if (!response.equals("OK")) {
                logger.error("server response is not OK, it is {}", response);
                socketChannel.close();
                return;
            }
            sendFileData(socketChannel, path);
            response = getServerResponse(socketChannel);
            System.out.println("sending completed : " + response);
            socketChannel.close();
        } catch (IOException e) {
            logger.error("can't know file size");
            throw new RuntimeException(e);
        }
        logger.info("client finished");
    }

    private String getServerResponse(SocketChannel socketChannel) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        try {
            socketChannel.read(buffer);
        } catch (IOException e) {
            logger.error("can't read server response");
            throw new RuntimeException(e);
        }
        buffer.flip();
        return new String(buffer.array(), StandardCharsets.UTF_8).trim();
    }

    private void sendFileData(SocketChannel socketChannel, Path path) {
        logger.trace("start send file data");
        try (FileChannel fileChannel = FileChannel.open(path)) {
            ByteBuffer fileBuffer = ByteBuffer.allocate(BUFFER_SIZE);
            while (fileChannel.read(fileBuffer) != -1) {
                fileBuffer.flip();
                while (fileBuffer.hasRemaining()) {
                    int writtenNum = socketChannel.write(fileBuffer);
                    logger.trace("was written {} bytes", writtenNum);
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
            jsonHeader = getDataInJsonFormat(fileMetaData);
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
        logger.trace("meta data was sent");
    }

    private String getDataInJsonFormat(FileMetaData fileMetaData) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(fileMetaData);

    }

}

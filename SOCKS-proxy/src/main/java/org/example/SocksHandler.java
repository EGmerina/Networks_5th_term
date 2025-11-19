package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
//TODO проблема, у нас только один канал для dns , нельзя хранить ProtocolStage в attachment, поэтому создаем фиктивный connection

public class SocksHandler {
    private static final Logger logger = LogManager.getLogger(SocksHandler.class);
    private static final int DATA_BUFFER_SIZE = 8192;
    private static Selector selector;
    private DnsResolver dnsResolver = new DnsResolver();


    public SocksHandler(Selector selector) {
        this.selector = selector;
    }


    public void handleDnsRead(SelectionKey key) {
        InetSocketAddress inetSocketAddress = dnsResolver.parseResponse((DatagramChannel) key.channel());
        //TODO и тут какой-то connect. я уже запуталась
        startConnect(conn, inetSocketAddress);
    }

    public void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel channel = (ServerSocketChannel) key.channel();
        try {
            SocketChannel clientSocket = channel.accept();
            clientSocket.configureBlocking(false);
            Connection newConnection = new Connection(clientSocket);
            clientSocket.register(selector, SelectionKey.OP_READ, newConnection);
            logger.info("client {} was accepted", clientSocket.getRemoteAddress());
        } catch (IOException e) {
            logger.error("can't accept socketChannel ");
            throw e;
        }

    }

    //TODO в итоге буду малодушно не отвечать клиенту если какая-то ошибка

    public void handleConnect(SelectionKey key) throws IOException {
        SocketChannel remote = (SocketChannel) key.channel();
        Connection connection = (Connection) key.attachment();

        if (connection == null) {
            throw new IOException("remote channel without attached connection");
        }

        if (remote.finishConnect()) {
            Connection newConnection = new Connection(remote);
            newConnection.setRemote(connection.getClient());

            key.interestOps(SelectionKey.OP_READ);

            connection.setStage(ProtocolStage.RELAY);
            newConnection.setStage(ProtocolStage.RELAY);

            logger.info("Connected to remote {} for client {}",
                    remote.getRemoteAddress(),
                    connection.getClient().getRemoteAddress());
        }
    }

    public void handleWrite(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        Connection currentConnection = (Connection) key.attachment();
        ByteBuffer buffer = currentConnection.getFromQueue(key);
        currentConnection.getRemote().write(buffer);
        if (buffer.hasRemaining()) {
            currentConnection.pushToQueue(buffer);
        }
    }


    public void handleRead(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        Connection currentConnection = (Connection) key.attachment();
        ProtocolStage stage = currentConnection.getStage();
        switch (stage) {
            case METHOD -> {
                sendMethodToClient(currentConnection);
            }
            case CONNECTING -> {
                connectToRemote(currentConnection);
            }
            case RELAY -> {
                relayData(currentConnection);
            }
            case RESOLVING_DNS -> {
                resolveDnsRequest(currentConnection);//TODO??????????????????????????????????????
            }
            default -> {
                logger.error("unknown protocol stage of client {}", socketChannel.getRemoteAddress());
            }

        }

    }


    private void sendMethodToClient(Connection connection) throws IOException {
        ByteBuffer buffer = connection.getHandshakeBuffer();

        boolean ok = false;
        try {
            ok = ProtocolExecutor.checkMethod(buffer);
        } catch (IOException e) {
            clientWriteRaw(connection, ByteBuffer.wrap(new byte[]{0x05, (byte) 0xff}));
            throw e;
        }
        if (!ok) {
            return;//wait
        }
        buffer.clear();
        clientWriteRaw(connection, ByteBuffer.wrap(new byte[]{0x05, 0x00}));
        connection.setStage(ProtocolStage.CONNECTING);
    }


    private void connectToRemote(Connection connection) throws IOException {
        ByteBuffer buf = connection.getHandshakeBuffer();

        byte atyp = ProtocolExecutor.getAType(buf);

        InetSocketAddress targetAddr = null;

        switch (atyp) {
            case 0x01: { // IPv4
                if (buf.remaining() < 4 + 2) {
                    buf.reset();
                    return;
                }
                targetAddr = ProtocolExecutor.getInetSocketAddress(buf);
            }
            break;

            case 0x03: { // DOMAIN
                if (buf.remaining() < 1) {
                    buf.reset();
                    return;
                }
                int len = buf.get() & 0xff;

                if (buf.remaining() < len + 2) {
                    buf.reset();
                    return;
                }

                byte[] dom = new byte[len];
                buf.get(dom);

                String domain = new String(dom, StandardCharsets.US_ASCII);
                int port = (buf.get() & 0xFF) << 8 | (buf.get() & 0xFF);

                buf.clear();
                sendDnsRequest(connection, domain, port);
                connection.setStage(ProtocolStage.RESOLVING_DNS);//TODO это тупо!!!!!!!! это нужно делать у dns резолвера
                return;
            }
            case 0x07: { //tODO пофиксить return
                return;
            }
            default:
                throw new IOException("unsupported address type");
        }

        buf.clear();
        startConnect(connection, targetAddr);
    }


    private void relayData(Connection currentConnection) throws IOException {
        SocketChannel socketChannel = currentConnection.getClient();
        ByteBuffer buffer = ByteBuffer.allocate(DATA_BUFFER_SIZE);
        int bytesNum = socketChannel.read(buffer);
        if (bytesNum == -1) {
            throw new IOException("end of stream");
        } else if (bytesNum == 0) {
            return;
        }
        clientWriteRaw(currentConnection, buffer);

    }

    private void resolveDnsRequest(Connection currentConnection) {

        // теперь делаем неблокирующий connect

    }

    private void clientWriteRaw(Connection connection, ByteBuffer buffer) throws IOException {

        SocketChannel remoteChannel = connection.getRemote();
        if (!remoteChannel.isConnected()) {
            throw new IOException("remote channel isn't connected, can't write data");
        }
        buffer.flip();
        remoteChannel.write(buffer);
        if (buffer.hasRemaining()) {
            connection.addToQueue(buffer);
            SelectionKey key = connection.getClient().keyFor(selector);
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE); //клиент хранит очередь на запись для remote
        }

    }

    private void startConnect(Connection connection, InetSocketAddress targetAddr) throws IOException {
        SocketChannel remote = SocketChannel.open();
        remote.configureBlocking(false);
        remote.connect(targetAddr);
        remote.register(selector, SelectionKey.OP_CONNECT, connection);
    }


}

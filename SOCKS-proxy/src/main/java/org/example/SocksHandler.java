package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;


public class SocksHandler {
    private static final Logger logger = LogManager.getLogger(SocksHandler.class);
    private static final int DATA_BUFFER_SIZE = 8192;
    private static final int DNS_PACKET_BUFFER_SIZE = 512;
    private static Selector selector;
    private HashMap<Integer, PendingConnection> pendingDns = new HashMap<>();
    private DatagramChannel dnsChannel;

    private static enum Destination {TO_CLIENT, TO_REMOTE}

    public SocksHandler(Selector selector, DatagramChannel dnsChannel) {
        this.selector = selector;
        this.dnsChannel = dnsChannel;
    }


    public void handleDnsRead(SelectionKey key) throws IOException {
        logger.trace("handle Dns Read");
        DatagramChannel dnsChannel = (DatagramChannel) key.channel();
        ByteBuffer buf = ByteBuffer.allocate(DNS_PACKET_BUFFER_SIZE);
        SocketAddress addr = dnsChannel.receive(buf);
        if (addr == null) {
            return;
        }
        buf.flip();

        byte[] arr = new byte[buf.remaining()];
        buf.get(arr);

        Message response = new Message(arr);

        int dnsId = response.getHeader().getID();
        PendingConnection pending = pendingDns.remove(dnsId);
        if (pending == null) {
            return;
        }

        Connection connection = pending.connection();
        int port = pending.port();

        for (Record r : response.getSection(Section.ANSWER)) {
            if (r.getType() == Type.A) {
                ARecord arec = (ARecord) r;
                InetAddress ip = arec.getAddress();

                logger.trace("Resolved: " + ip.getHostAddress());
                InetSocketAddress inetSocketAddress = new InetSocketAddress(ip, port);
                startConnect(connection, inetSocketAddress);
            }
        }

    }

    public void handleAccept(SelectionKey key) throws IOException {
        logger.trace("handle accept");
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


    public void handleConnect(SelectionKey key) throws IOException {//TODO в итоге буду малодушно не отвечать клиенту если какая-то ошибка
        logger.trace("handle connect");
        SocketChannel remote = (SocketChannel) key.channel();
        Connection connection = (Connection) key.attachment();

        if (connection == null) {
            throw new IOException("remote channel without attached connection");
        }

        if (remote.finishConnect()) {

            sendServerResponsePacket(connection);

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
        logger.trace("handle write");
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
        logger.trace("handle read, protocol stage : {}", stage);
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
                return;
            }
            default -> {
                logger.error("unknown protocol stage of client {}", socketChannel.getRemoteAddress());
            }

        }
        logger.trace("handle read finished");

    }


    private void sendMethodToClient(Connection connection) throws IOException {

        ByteBuffer buffer = connection.getHandshakeBuffer();
        SocketChannel channel = connection.getClient();
        channel.read(buffer);
        buffer.flip();
        boolean ok = false;
        try {
            ok = ProtocolExecutor.checkMethod(buffer);
            logger.trace("send method : method is {}", ok);
        } catch (IOException e) {
            clientWriteRaw(connection, ProtocolExecutor.getNotOkMethodMessage(), Destination.TO_CLIENT);
            throw e;
        }
        if (!ok) {
            logger.trace(" wait data");
            return;//wait
        }
        //buffer.compact();
        clientWriteRaw(connection, ProtocolExecutor.getOkMethodMessage(), Destination.TO_CLIENT);
        connection.setStage(ProtocolStage.CONNECTING);
        //connectToRemote(connection);
    }


    private void connectToRemote(Connection connection) throws IOException { //TODO разобраться
        logger.trace("connecting to remote...");

        SocketChannel channel = connection.getClient();
        ByteBuffer buf = connection.getHandshakeBuffer();


        int read = channel.read(buf);
        if (read == -1) {
            logger.error("can't read from channel => client {} closed", channel.getRemoteAddress());
            throw new IOException("client closed");
        } else if (read == 0 && buf.position() == 0) {
            logger.trace("wait data to connect, read = 0");
            return;
        }
        logger.trace("!!!!!!!!!!!!!!!!!get some data!!!!!!!!!!!!!!!!!!!!!!");

        buf.flip();

        Byte atyp = ProtocolExecutor.getAType(buf);
        InetSocketAddress targetAddr = null;
        switch (atyp) {
            case 0x01: {
                targetAddr = ProtocolExecutor.getInetSocketAddress(buf);
                if (targetAddr == null) {
                    logger.trace("wait ipv4");
                    return;
                }
                startConnect(connection, targetAddr);
                break;
            }
            case 0x03: {
                String domain = ProtocolExecutor.getDomain(buf);
                if (domain == null) {
                    logger.trace("wait domain");
                    return;
                }

                int port = ProtocolExecutor.getPort(buf);

                logger.trace("Need DNS resolve: {}:{}", domain, port);

                sendDnsRequest(connection, domain, port);
                connection.setStage(ProtocolStage.RESOLVING_DNS);
                break;
            }

            case 0x04:
                throw new IOException("IPv6 not supported");

            case null:
                logger.trace("wait data to connect");
                return;

            default:
                throw new IOException("unknown ATYP: " + atyp);
        }

        buf.clear();

    }


    private void relayData(Connection currentConnection) throws IOException {
        logger.trace("relay data");
        SocketChannel socketChannel = currentConnection.getClient();
        ByteBuffer buffer = ByteBuffer.allocate(DATA_BUFFER_SIZE);
        int bytesNum = socketChannel.read(buffer);
        if (bytesNum == -1) {
            throw new IOException("end of stream");
        } else if (bytesNum == 0) {
            return;
        }
        clientWriteRaw(currentConnection, buffer, Destination.TO_REMOTE);

    }


    private void clientWriteRaw(Connection connection, ByteBuffer buffer, Destination destination) throws IOException {
        logger.trace("write raw data {}", destination);
        SocketChannel channel = null;
        switch (destination) {
            case TO_CLIENT -> {
                channel = connection.getClient();
            }
            case TO_REMOTE -> {
                channel = connection.getRemote();
                if (!channel.isConnected()) {
                    logger.error("remote channel isn't connected, can't write data");
                    throw new IOException("remote channel isn't connected, can't write data");
                }
            }
        }

        buffer.flip();
        channel.write(buffer);
        if (buffer.hasRemaining()) {
            logger.trace("add data to queue");
            connection.addToQueue(buffer);
            SelectionKey key = connection.getClient().keyFor(selector);
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE); //клиент хранит очередь на запись для remote
        }
        logger.trace("data was written");
    }

    private void startConnect(Connection connection, InetSocketAddress targetAddr) throws IOException {
        SocketChannel remote = SocketChannel.open();
        remote.configureBlocking(false);
        remote.connect(targetAddr);
        remote.register(selector, SelectionKey.OP_CONNECT, connection);
    }


    private void sendDnsRequest(Connection connection, String domain, int port) throws IOException {
        logger.trace("send DNS request : domain {}, port {}", domain, port);
        List<InetSocketAddress> dnsServers = ResolverConfig.getCurrentConfig().servers();
        InetSocketAddress server = dnsServers.get(0);
        Name qname = null;
        try {
            qname = Name.fromString(domain, Name.fromString("."));
        } catch (TextParseException e) {
            logger.error("can't parse dns name");
            throw new RuntimeException(e);
        }
        org.xbill.DNS.Record question = Record.newRecord(qname, Type.A, DClass.IN);
        Message query = Message.newQuery(question);
        byte[] raw = query.toWire();
        pendingDns.put(query.getHeader().getID(), new PendingConnection(connection, port));
        dnsChannel.send(ByteBuffer.wrap(raw), server);
    }

    private void sendServerResponsePacket(Connection connection) throws IOException {
        logger.trace("send response to client");
        clientWriteRaw(connection, ProtocolExecutor.getResponsePacket(), Destination.TO_CLIENT);
    }

}

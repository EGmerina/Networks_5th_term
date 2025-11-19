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
//
//            // === MUST send SOCKS5 CONNECT reply ===
//            ByteBuffer reply = ByteBuffer.allocate(10);
//            reply.put((byte) 0x05); // VER
//            reply.put((byte) 0x00); // REP = succeeded
//            reply.put((byte) 0x00); // RSV
//            reply.put((byte) 0x01); // ATYP = IPv4
//            reply.putInt(0);        // BND.ADDR = 0.0.0.0
//            reply.putShort((short) 0); // BND.PORT = 0
//            reply.flip();
//
//            connection.getClient().write(reply);//&&&&&&&&&&&&&&&&&&&&&?????????????????????

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
            clientWriteRaw(connection, ByteBuffer.wrap(new byte[]{0x05, (byte) 0xff}), Destination.TO_CLIENT);
            throw e;
        }
        if (!ok) {
            logger.trace(" wait data");
            return;//wait
        }
        buffer.clear();
        clientWriteRaw(connection, ByteBuffer.wrap(new byte[]{0x05, 0x00}), Destination.TO_CLIENT);
        connection.setStage(ProtocolStage.CONNECTING);
    }


    private void connectToRemote(Connection connection) throws IOException { //TODO разобраться
        logger.trace("connecting to remote...");

        SocketChannel channel = connection.getClient();
        ByteBuffer buf = connection.getHandshakeBuffer();

        int read = channel.read(buf);
        if (read == -1) throw new IOException("client closed");

        buf.flip();       // switch into reading mode
        buf.mark();       // we will restore if data is incomplete

        // ----------- STEP 1: Read header ----------- //
        if (buf.remaining() < 4) {
            buf.reset();
            buf.compact();
            return;
        }

        byte ver = buf.get();
        byte cmd = buf.get();
        byte rsv = buf.get();
        byte atyp = buf.get();

        if (ver != 0x05) throw new IOException("invalid SOCKS version");
        if (cmd != 0x01) throw new IOException("only CONNECT supported");

        InetSocketAddress targetAddr = null;

        // ----------- STEP 2: Parse address type ----------- //
        switch (atyp) {
            case 0x01: // IPv4
            {
                if (buf.remaining() < 4 + 2) {
                    buf.reset();
                    buf.compact();
                    return;
                }

                byte[] ip4 = new byte[4];
                buf.get(ip4);
                int port = (buf.get() & 0xFF) << 8 | (buf.get() & 0xFF);

                String ipStr =
                        (ip4[0] & 0xff) + "." +
                                (ip4[1] & 0xff) + "." +
                                (ip4[2] & 0xff) + "." +
                                (ip4[3] & 0xff);

                targetAddr = new InetSocketAddress(ipStr, port);
            }
            break;

            case 0x03: // DOMAIN
            {
                if (buf.remaining() < 1) {
                    buf.reset();
                    buf.compact();
                    return;
                }

                int len = buf.get() & 0xff;

                if (buf.remaining() < len + 2) {
                    buf.reset();
                    buf.compact();
                    return;
                }

                byte[] dom = new byte[len];
                buf.get(dom);
                String domain = new String(dom, StandardCharsets.US_ASCII);

                int port = (buf.get() & 0xFF) << 8 | (buf.get() & 0xFF);

                logger.trace("Need DNS resolve: {}:{}", domain, port);

                buf.clear();
                sendDnsRequest(connection, domain, port);
                connection.setStage(ProtocolStage.RESOLVING_DNS);
                return;
            }

            case 0x04: // IPv6
                throw new IOException("IPv6 not supported");

            default:
                throw new IOException("unknown ATYP: " + atyp);
        }

        // ----------- STEP 3: Start connecting ----------- //
        buf.clear();
        startConnect(connection, targetAddr);
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
        SocketChannel channel = null;
        switch (destination) {
            case TO_CLIENT -> {
                channel = connection.getClient();
            }
            case TO_REMOTE -> {
                channel = connection.getRemote();
                if (!channel.isConnected()) {
                    throw new IOException("remote channel isn't connected, can't write data");
                }
            }
        }

        buffer.flip();
        channel.write(buffer);
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


    private void sendDnsRequest(Connection connection, String domain, int port) throws IOException {
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

}

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
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class SocksProxy {
    private static final Logger logger = LogManager.getLogger(SocksProxy.class);
    private static final int DATA_BUFFER_SIZE = 8192;
    private final Selector selector;
    private final DatagramChannel dnsChannel;
    private HashMap<Integer, Connection> pendingDns = new HashMap<>();


//    private DatagramChannel dnsChannel;
//    private InetSocketAddress dnsResolverAddr; // например 8.8.8.8:53

    public SocksProxy() {
        try {
            selector = Selector.open(); //TODO все-таки вынести в try-catch
            dnsChannel = DatagramChannel.open();
        } catch (IOException e) {
            logger.error("can't open selector");
            throw new RuntimeException(e);
        }


    }

    public void start(int port) {
        logger.info("SocksProxy started on port {}", port);
        try (selector; ServerSocketChannel serverSocketChannel = ServerSocketChannel.open(); dnsChannel;) {
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(new InetSocketAddress(port));
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            dnsChannel.configureBlocking(false);
            dnsChannel.bind(null); //сами разберутся какой порт
            dnsChannel.register(selector, SelectionKey.OP_READ);

            while (true) {
                selector.select();
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectedKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    if (!key.isValid()) {
                        iterator.remove();
                        continue;
                    }
                    try {
                        if (key.isAcceptable()) {
                            handleAccept(key);
                        } else if (key.isConnectable()) {
                            handleConnect(key);
                        } else if (key.isReadable()) {
                            handleRead(key);
                        } else if (key.isWritable()) {
                            handleWrite(key);
                        }
                    } catch (Exception e) {
                        logger.warn("client close connection unexpected: " + e.getMessage());
                        closeConnection(key);
                    }
                }


            }

        } catch (Exception e) {
            logger.error("exception during working server: " + e.getMessage() + " and suppressed:" + e.getSuppressed());
            throw new RuntimeException(e);
        }
    }


    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel channel = (ServerSocketChannel) key.channel();
        try {
            SocketChannel clientSocket = channel.accept();
            clientSocket.configureBlocking(false);
            Connection newConnection = new Connection(clientSocket);
            clientSocket.register(selector, SelectionKey.OP_READ, newConnection); //пока только handshake
            logger.info("client {} was accepted", clientSocket.getRemoteAddress());
        } catch (IOException e) {
            logger.error("can't accept socketChannel ");
            throw e;
        }

    }

    //TODO в итоге буду малодушно не отвечать клиенту если какая-то ошибка

    private void handleConnect(SelectionKey key) throws IOException {
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

    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        Connection currentConnection = (Connection) key.attachment();
        ByteBuffer buffer = currentConnection.getFromQueue(key);
        currentConnection.getRemote().write(buffer);
        if (buffer.hasRemaining()) {
            currentConnection.pushToQueue(buffer);
        }
    }


    private void handleRead(SelectionKey key) throws IOException {
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
                resolveDnsRequest(currentConnection);
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
        int id = resp.getHeader().getID();
        Connection conn = pendingDns.remove(id);
        if (conn == null) return; // чужой пакет

        ARecord a = null;

        for (Record r : resp.getSection(Section.ANSWER)) {
            if (r.getType() == Type.A) {
                a = (ARecord) r;
                break;
            }
        }

        if (a == null) {
            // ошибка DNS → послать SOCKS5 failure
            sendSocksReply(conn.client.channel, (byte) 0x04);
            conn.closeQuietly();
            return;
        }

        InetAddress ip = a.getAddress();
        InetSocketAddress addr = new InetSocketAddress(ip, conn.port);

        // теперь делаем неблокирующий connect
        startNonBlockingConnect(conn, addr);
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
        Record question = Record.newRecord(qname, Type.A, DClass.IN);
        Message query = Message.newQuery(question);
        byte[] raw = query.toWire();
        connection.setRemotePort(port);
        pendingDns.put(query.getHeader().getID(), connection);
        dnsChannel.send(ByteBuffer.wrap(raw), server);
    }


    private void closeConnection(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        Connection currentConnection = (Connection) key.attachment();
        currentConnection.clearQueue();
        currentConnection.getHandshakeBuffer().clear();
        SocketChannel remote = currentConnection.getRemote();
        if (remote != null) {
            remote.close();
        }
        socketChannel.close();
        key.cancel();
    }


}

package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;

public class SocksProxy {
    private static final Logger logger = LogManager.getLogger(SocksProxy.class);
    private static final int DATA_BUFFER_SIZE = 8192;
    private final Selector selector;

    public SocksProxy() {
        try {
            selector = Selector.open();
        } catch (IOException e) {
            logger.error("can't open selector");
            throw new RuntimeException(e);
        }
        DatagramChannel dnsChannel = DatagramChannel.open();
        dnsChannel.configureBlocking(false);
        dnsChannel.bind(null); // случайный локальный порт

        SelectionKey dnsKey = dnsChannel.register(selector, SelectionKey.OP_READ);
    }

    public void start(int port) {
        logger.info("SocksProxy started on port {}", port);
        try (selector; ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(new InetSocketAddress(port));
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

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
                            handleAccept((ServerSocketChannel) key.channel());
                        } else if (key.isConnectable()) {
                            handleConnect(key);
                        } else if (key.isReadable()) {
                            handleRead(key);
                        } else if (key.isWritable()) {
                            handleWrite(key);
                        }
                    } catch (IOException e) {
                        logger.warn("client close connection unexpected: " + e.getMessage());
                        closeConnection(key);
                    }
                }


            }

        } catch (IOException e) {
            logger.error("exception during working server: " + e.getMessage() + " and suppressed:" + e.getSuppressed());
            throw new RuntimeException(e);
        }
    }


    private void handleAccept(ServerSocketChannel channel) {
        try {
            SocketChannel clientSocket = channel.accept();
            clientSocket.configureBlocking(false);
            Connection newConnection = new Connection(clientSocket);
            clientSocket.register(selector, SelectionKey.OP_READ, newConnection); //пока только handshake
            logger.info("client {} was accepted", clientSocket.getRemoteAddress());
        } catch (IOException e) {
            logger.error("can't accept socketChannel ");
            throw new RuntimeException(e);
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
                startAsyncDnsResolve(connection, domain, port);
                connection.setStage(ProtocolStage.RESOLVING_DNS);
                return;
            }
            case 0x07: {
                return;
            }
            default:
                throw new IOException("unsupported address type");
        }

        buf.clear();
        startNonBlockingConnect(connection, targetAddr);
    }

    private void startAsyncDnsResolve(Connection conn, String domain, int port) throws IOException {
        // Create DNS query using dnsjava
        org.xbill.DNS.Record rec = org.xbill.DNS.Record.newRecord(
                org.xbill.DNS.Name.fromString(domain + "."),
                org.xbill.DNS.Type.A,
                org.xbill.DNS.DClass.IN
        );

        org.xbill.DNS.Message query = org.xbill.DNS.Message.newQuery(rec);

        int qid = query.getHeader().getID();

        pendingDns.put(qid, conn);
        conn.setDnsPort(port);

        byte[] out = query.toWire();

        ByteBuffer packet = ByteBuffer.wrap(out);
        dnsChannel.send(packet, dnsResolverAddr);
    }

    private void startNonBlockingConnect(Connection connection, InetSocketAddress targetAddr) throws IOException {
        SocketChannel remote = SocketChannel.open();

        remote.configureBlocking(false);
        remote.connect(targetAddr);
        remote.register(selector, SelectionKey.OP_CONNECT, connection);
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

    private void clientWriteRaw(Connection connection, ByteBuffer buffer) throws IOException {

        SocketChannel remoteChannel = connection.getRemote();
        if (!remoteChannel.isConnected()) {
            throw new IOException("remote channel isn't connected, can't write data");
        }
        buffer.flip(); //TODO??????
        remoteChannel.write(buffer);
        if (buffer.hasRemaining()) {
            connection.addToQueue(buffer);
            SelectionKey key = connection.getClient().keyFor(selector);
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE); //клиент хранит очередь на запись для remote
        }

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

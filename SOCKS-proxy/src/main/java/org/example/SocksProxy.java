package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
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

    private void handleWrite(SelectionKey key) {
    }


    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        Connection currentConnection = (Connection) key.attachment();
        ProtocolStage stage = currentConnection.getStage();
        switch (stage) {
            case METHOD -> {
                sendMethodToClient(currentConnection);
            }
            case STATUS -> {
                sendStatusToClient(currentConnection);
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
        if (buffer.remaining() < 2) return;
        buffer.mark();
        byte ver = buffer.get();
        byte methodsNum = buffer.get();
        if (buffer.remaining() < methodsNum) {
            buffer.reset();
            return;
        }
        boolean noauth = false;
        for (int i = 0; i < methodsNum; i++) {
            byte m = buffer.get();
            if (m == 0x00) noauth = true;
        }
        if (ver != 0x05 || !noauth) {
            clientWriteRaw(connection, ByteBuffer.wrap(new byte[]{0x05, (byte) 0xff}));
            throw new IOException("version socks isn't 5 or unsupported connection");
        }
        buffer.clear();
        clientWriteRaw(connection, ByteBuffer.wrap(new byte[]{0x05, 0x00}));
        connection.setStage(ProtocolStage.STATUS);
    }

    private void sendStatusToClient(Connection connection) {
        ByteBuffer buffer = connection.getHandshakeBuffer();

        connection.setStage(ProtocolStage.CONNECTING);
    }

    private void connectToRemote(Connection connection) throws IOException {
        ByteBuffer buf = connection.getHandshakeBuffer();

        if (buf.remaining() < 4) return; // need VER CMD RSV ATYP

        buf.mark();
        byte ver = buf.get();
        byte cmd = buf.get();
        byte rsv = buf.get();
        byte atyp = buf.get();

        if (ver != 0x05) throw new IOException("invalid socks5 version");
        if (cmd != 0x01) { // only CONNECT supported
            //sendSocksReply(conn, (byte) 0x07);
            throw new IOException("unsupported command");
        }

        InetSocketAddress targetAddr = null;

        switch (atyp) {
            case 0x01: { // IPv4
                if (buf.remaining() < 4 + 2) {
                    buf.reset();
                    return;
                }
                byte[] ip4 = new byte[4];
                buf.get(ip4);
                int port = (buf.get() & 0xFF) << 8 | (buf.get() & 0xFF);

                String ipStr = (ip4[0] & 0xff) + "." +
                        (ip4[1] & 0xff) + "." +
                        (ip4[2] & 0xff) + "." +
                        (ip4[3] & 0xff);

                targetAddr = new InetSocketAddress(ipStr, port);
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

                // Go to DNS resolving
                buf.clear();
                startAsyncDnsResolve(conn, domain, port);
                connection.setStage(ProtocolStage.RESOLVING_DNS);
                return;
            }

            default:
                // sendSocksReply(conn, (byte) 0x08);
                throw new IOException("unsupported address type");
        }

        buf.clear();
        startNonBlockingConnect(connection, targetAddr);
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

    private void closeConnection(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        Connection currentConnection = (Connection) key.attachment();
        currentConnection.getPending().clear();
        currentConnection.getHandshakeBuffer().clear();
        SocketChannel remote = currentConnection.getRemote();
        if (remote != null) {
            remote.close();
        }
        socketChannel.close();
        key.cancel();
    }

    private void clientWriteRaw(Connection connection, ByteBuffer buffer) throws IOException {

        SocketChannel remoteChannel = connection.getRemote();
        if (!remoteChannel.isConnected()) {
            throw new IOException("remote channel isn't connected, can't write data");
        }
        buffer.flip();
        remoteChannel.write(buffer);
        if (buffer.hasRemaining()) {
            connection.getPending().add(buffer);
            SelectionKey remoteKey = remoteChannel.keyFor(selector);
            remoteKey.interestOps(remoteKey.interestOps() | SelectionKey.OP_WRITE);
        }

    }
}

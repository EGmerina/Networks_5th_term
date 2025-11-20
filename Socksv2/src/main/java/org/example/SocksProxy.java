package org.example;

import org.xbill.DNS.*;
import org.xbill.DNS.Record;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SocksProxy {
    private static final Logger logger = LogManager.getLogger(SocksProxy.class);
    private int port;
    private Selector selector;
    private InetSocketAddress dnsServerAddress;

    private final Map<Integer, Connection> dnsPending = new HashMap<>();

    public SocksProxy(int port) throws IOException {
        this.port = port;
        this.selector = SelectorProvider.provider().openSelector();
        this.dnsServerAddress = ResolverConfig.getCurrentConfig().server();
    }

    public void start() throws IOException {
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(new InetSocketAddress(port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        DatagramChannel dnsChannel = DatagramChannel.open();
        dnsChannel.configureBlocking(false);
        dnsChannel.connect(dnsServerAddress);
        dnsChannel.register(selector, SelectionKey.OP_READ);

        logger.info("SOCKS5 Proxy started on port " + port);

        while (true) {
            selector.select();
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();

                if (!key.isValid()) continue;

                try {
                    if (key.isAcceptable()) {
                        accept(key);
                    } else if (key.isConnectable()) {
                        finishConnect(key);
                    } else if (key.isReadable()) {
                        if (key.channel() == dnsChannel) {
                            handleDNS(key);
                        } else {
                            read(key);
                        }
                    } else if (key.isWritable()) {
                        write(key);
                    }
                } catch (Exception e) {
                    closeSession(key);
                }
            }
        }
    }

    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverSocketChannel.accept();
        clientChannel.configureBlocking(false);

        Connection connection = new Connection();
        connection.clientChannel = clientChannel;
        connection.state = State.WAIT_AUTH;
        clientChannel.register(selector, SelectionKey.OP_READ, connection);
        logger.trace("client {} was accepted", clientChannel.getRemoteAddress());
    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        Connection att = (Connection) key.attachment();

        boolean isClient = (channel == att.clientChannel);
        logger.trace("handle read , stage {}", att.state);
        switch (att.state) {
            case WAIT_AUTH:
                handleAuth(key);
                break;
            case WAIT_REQUEST:
                handleRequest(key);
                break;
            case RELAY:
                if (isClient) {
                    relayData(att.clientChannel, att.inBuffer, att.targetChannel, att);
                } else {
                    relayData(att.targetChannel, att.outBuffer, att.clientChannel, att);
                }
                break;
            default:
                break;
        }
    }

    private void handleAuth(SelectionKey key) throws IOException {
        logger.trace("handle Auth");
        SocketChannel client = (SocketChannel) key.channel();
        Connection att = (Connection) key.attachment();
        ByteBuffer buf = att.inBuffer;

        int read = client.read(buf);
        if (read == -1) throw new IOException("Client closed connection");


        if (buf.position() < 3) return;

        buf.flip();
        byte ver = buf.get();
        byte nmethods = buf.get();

        if (ver != 0x05) {
            throw new IOException("Only SOCKS5 supported");
        }

        buf.clear();

        ByteBuffer response = ByteBuffer.wrap(new byte[]{0x05, 0x00});
        client.write(response);

        att.state = State.WAIT_REQUEST;
    }

    private void handleRequest(SelectionKey key) throws IOException {
        logger.trace("handle request ");
        SocketChannel client = (SocketChannel) key.channel();
        Connection att = (Connection) key.attachment();
        ByteBuffer buf = att.inBuffer;

        int read = client.read(buf);
        if (read == -1) throw new IOException("Client closed connection");

        if (buf.position() < 4) return;

        buf.flip();
        if (buf.get() != 0x05 || buf.get() != 0x01) { // VER=5, CMD=1 (Connect)
            throw new IOException("Unsupported SOCKS command");
        }
        buf.get(); // RSV
        byte atyp = buf.get();

        InetAddress targetIp = null;
        int targetPort = 0;

        if (atyp == 0x01) { // IPv4
            if (buf.remaining() < 4 + 2) { // IP(4) + Port(2)
                buf.position(buf.limit());
                buf.limit(buf.capacity());
                return;
            }
            byte[] ipBytes = new byte[4];
            buf.get(ipBytes);
            targetIp = InetAddress.getByAddress(ipBytes);
            targetPort = Short.toUnsignedInt(buf.getShort());

            initiateConnect(att, targetIp, targetPort);

        } else if (atyp == 0x03) { // Domain name
            if (buf.remaining() < 1) {
                buf.position(buf.limit());
                buf.limit(buf.capacity());
                return;
            }
            buf.mark();
            int len = Byte.toUnsignedInt(buf.get());
            if (buf.remaining() < len + 2) {
                buf.reset();
                buf.position(buf.limit());
                buf.limit(buf.capacity());
                return;
            }
            byte[] domainBytes = new byte[len];
            buf.get(domainBytes);
            String domain = new String(domainBytes);
            targetPort = Short.toUnsignedInt(buf.getShort());

            resolveDomain(att, domain, targetPort);

        } else {
            throw new IOException("Unsupported Address Type: " + atyp);
        }

        buf.clear();
    }

    private void resolveDomain(Connection att, String domain, int port) throws IOException {
        att.destinationPort = port;
        att.state = State.DNS_LOOKUP;

        logger.trace("resolve dns: {}, {}", domain, port);
        Name name = Name.fromString(domain, Name.root);
        Record question = Record.newRecord(name, Type.A, DClass.IN);
        Message query = Message.newQuery(question);

        int id = query.getHeader().getID();
        dnsPending.put(id, att);
        att.dnsID = id;

        DatagramChannel dnsChannel = (DatagramChannel) selector.keys().stream()
                .filter(k -> k.channel() instanceof DatagramChannel)
                .findFirst().get().channel();

        ByteBuffer dnsBuf = ByteBuffer.wrap(query.toWire());
        dnsChannel.write(dnsBuf);

        att.clientChannel.register(selector, 0, att);

    }

    private void handleDNS(SelectionKey key) throws IOException {
        logger.trace("handle dns");
        DatagramChannel dnsChannel = (DatagramChannel) key.channel();
        ByteBuffer buf = ByteBuffer.allocate(512);

        if (dnsChannel.read(buf) <= 0) return;
        buf.flip();


        Message response = new Message(buf.array());
        int id = response.getHeader().getID();

        Connection att = dnsPending.remove(id);
        if (att == null) return; // Не наш запрос или тайм-аут

        // Ищем A запись
        Record[] answers = response.getSectionArray(Section.ANSWER);
        InetAddress ip = null;
        for (Record r : answers) {
            if (r instanceof ARecord) {
                ip = ((ARecord) r).getAddress();
                break;
            }
        }

        if (ip != null) {
            initiateConnect(att, ip, att.destinationPort);
        } else {
            closeSession(att.clientKey());
        }

    }

    private void initiateConnect(Connection att, InetAddress ip, int port) throws IOException {
        att.state = State.CONNECTING;

        SocketChannel targetChannel = SocketChannel.open();
        targetChannel.configureBlocking(false);
        targetChannel.connect(new InetSocketAddress(ip, port));

        att.targetChannel = targetChannel;

        targetChannel.register(selector, SelectionKey.OP_CONNECT, att);

        // Отключаем интерес к клиенту пока соединяемся
        att.clientChannel.register(selector, 0, att);
    }

    private void finishConnect(SelectionKey key) throws IOException {
        SocketChannel target = (SocketChannel) key.channel();
        Connection att = (Connection) key.attachment();

        if (target.finishConnect()) {
            att.state = State.RELAY;

            byte[] response = new byte[10];
            response[0] = 0x05;
            response[1] = 0x00; // Success
            response[2] = 0x00;
            response[3] = 0x01; // IPv4

            att.clientChannel.write(ByteBuffer.wrap(response));

            att.clientChannel.register(selector, SelectionKey.OP_READ, att);
            target.register(selector, SelectionKey.OP_READ, att);
        }
    }


    private void relayData(SocketChannel source, ByteBuffer buffer, SocketChannel dest, Connection att) throws IOException {
        int read = source.read(buffer);

        if (read == -1) {
            dest.shutdownOutput();
            if (att.isHalfClosed) {
                closeSession(att.clientKey());
            } else {
                att.isHalfClosed = true;
                source.register(selector, source.keyFor(selector).interestOps() & ~SelectionKey.OP_READ, att);
            }
            return;
        }

        if (read > 0) {
            buffer.flip();
            SelectionKey destKey = dest.keyFor(selector);
            destKey.interestOps(destKey.interestOps() | SelectionKey.OP_WRITE);

            if (!buffer.hasRemaining()) {
                SelectionKey sourceKey = source.keyFor(selector);
                sourceKey.interestOps(sourceKey.interestOps() & ~SelectionKey.OP_READ);
            }
        }
    }

    private void write(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        Connection att = (Connection) key.attachment();

        ByteBuffer buffer = (channel == att.clientChannel) ? att.outBuffer : att.inBuffer;
        SocketChannel source = (channel == att.clientChannel) ? att.targetChannel : att.clientChannel;

        channel.write(buffer);

        if (!buffer.hasRemaining()) {
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);

            buffer.clear();
            if (source != null && source.isOpen()) {
                SelectionKey sourceKey = source.keyFor(selector);
                sourceKey.interestOps(sourceKey.interestOps() | SelectionKey.OP_READ);
            }
        }
    }

    private void closeSession(SelectionKey key) {
        if (key == null) return;
        closeSession((Connection) key.attachment());
    }

    private void closeSession(Connection att) {
        if (att == null) return;
        try {
            if (att.clientChannel != null) att.clientChannel.close();
            if (att.targetChannel != null) att.targetChannel.close();
            if (att.dnsID != 0) dnsPending.remove(att.dnsID);
        } catch (IOException e) {
            // ignore
        }
    }
}


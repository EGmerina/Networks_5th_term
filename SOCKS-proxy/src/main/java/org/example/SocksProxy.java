package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xbill.DNS.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

public class SocksProxy {
    private static final Logger logger = LogManager.getLogger(SocksProxy.class);

    private final Selector selector;


    public SocksProxy() throws IOException {
        try {
            selector = Selector.open();
        } catch (IOException e) {
            logger.error("can't open selector");
            throw e;
        }
    }

    public void start(int port) {
        logger.info("SocksProxy started on port {}", port);
        try (selector; ServerSocketChannel serverSocketChannel = ServerSocketChannel.open(); DatagramChannel dnsChannel = DatagramChannel.open();) {
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(new InetSocketAddress(port));
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            dnsChannel.configureBlocking(false);
            dnsChannel.bind(null);
            dnsChannel.register(selector, SelectionKey.OP_READ);

            SocksHandler socksHandler = new SocksHandler(selector, dnsChannel);

            while (true) {
                selector.select();
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                logger.trace("---------keys were selected------------");
                Iterator<SelectionKey> iterator = selectedKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    if (!key.isValid()) {
                        // iterator.remove();
                        continue;
                    }
                    try {
                        if (key.channel() == dnsChannel) {
                            socksHandler.handleDnsRead(key);
                        } else if (key.isAcceptable()) {
                            socksHandler.handleAccept(key);
                        } else if (key.isConnectable()) {
                            socksHandler.handleConnect(key);
                        } else if (key.isReadable()) {
                            socksHandler.handleRead(key);
                        } else if (key.isWritable()) {
                            socksHandler.handleWrite(key);
                        }

                    } catch (IOException e) {
                        logger.warn("client close connection unexpected: " + e.getMessage());
                        closeConnection(key);
                    }
                }


            }

        } catch (Exception e) {
            logger.error("exception during working server: " + e.getMessage() + " ,  suppressed exceptions:" + e.getSuppressed());
            throw new RuntimeException(e);
        } finally {
            closeAllChannels(selector);
            logger.info("SocksProxy finished");
        }
    }

    private void closeAllChannels(Selector selector) {
        try {
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
                closeConnection(key);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void closeConnection(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        Connection currentConnection = (Connection) key.attachment();
        if (currentConnection != null) {
            currentConnection.clearQueue();
            currentConnection.getHandshakeBuffer().clear();
            SocketChannel remote = currentConnection.getRemote();
            if (remote != null) {
                remote.close();
            }
        }
        socketChannel.close();
        key.cancel();
    }


}

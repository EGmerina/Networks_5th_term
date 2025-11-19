package org.example;

import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.HashMap;
import java.util.List;

public class DnsResolver {
    private HashMap<Integer, Connection> pendingDns = new HashMap<>();
    //    private InetSocketAddress dnsResolverAddr; // например 8.8.8.8:53 //TODO можно пихнуть в attachment у dnschannel?

    public InetSocketAddress parseResponse(DatagramChannel channel) {
        int id = resp.getHeader().getID();
        Connection conn = pendingDns.remove(id);
        if (conn == null) return; // чужой пакет

        ARecord a = null;

        for (org.xbill.DNS.Record r : resp.getSection(Section.ANSWER)) {
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

    }

    public void sendDnsRequest(Connection connection, String domain, int port) throws IOException {
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
        connection.setRemotePort(port);
        pendingDns.put(query.getHeader().getID(), connection);
        dnsChannel.send(ByteBuffer.wrap(raw), server);
    }
}

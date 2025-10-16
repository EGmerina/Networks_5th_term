package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.SocketAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TransferStatsMonitor {
    private final Logger logger = LogManager.getLogger(TransferStatsMonitor.class);
    private final ConcurrentHashMap<String, TransferStats> transferStatsMap = new ConcurrentHashMap<>();

    public void start() {
        new Thread(() -> {
            try {
                while (true) {
                    System.out.print("\033[2J\033[H");
                    System.out.flush();
                    Iterator<Map.Entry<String, TransferStats>> iterator = transferStatsMap.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<String, TransferStats> entry = iterator.next();
                        System.out.println("======================================");
                        System.out.println("client with address : " + entry.getKey());
                        System.out.println("⏲ instantaneous speed : " + entry.getValue().getInstantaneousSpeed() + " bytes/sec ");
                        System.out.println("⏲ average speed : " + entry.getValue().getAverageSpeed() + " bytes/sec ");
                        System.out.println("======================================");
                        if (entry.getValue().getFlagToDelete()) {
                            iterator.remove();
                        } else {
                            entry.getValue().resetLastBytesReceived();
                            entry.getValue().resetLastCheckTime();
                        }

                    }
                    Thread.sleep(3000);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("monitor thread was interrupted", e);
            }

        }).start();
    }

    public void registerClient(SocketAddress remoteAddress) {
        transferStatsMap.put(remoteAddress.toString(), new TransferStats());
    }

    public void updateReceivedBytes(String clientKey, long recBytesNum) {
        transferStatsMap.get(clientKey).addBytes(recBytesNum);
    }

    public void deleteClient(String clientKey) {
        transferStatsMap.get(clientKey).setFlagToDelete();
    }
}

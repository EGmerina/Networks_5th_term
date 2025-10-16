package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TransferStats {
    private final Logger logger = LogManager.getLogger(TransferStats.class);
    private long totalBytesReceived = 0;
    private final long startTime = System.nanoTime();
    private long lastCheckTime = startTime;
    private long lastBytesReceived = 0;
    private boolean willBeDeleted = false;

    public long getInstantaneousSpeed() {
        logger.info("time : {},   bytes : {}", (System.nanoTime() - lastCheckTime), lastBytesReceived);
        return (lastBytesReceived * 1_000_000_000L) / (System.nanoTime() - lastCheckTime);
    }

    public long getAverageSpeed() {
        return (totalBytesReceived * 1_000_000_000L) / (System.nanoTime() - startTime);
    }

    public void resetLastBytesReceived() {
        lastBytesReceived = 0;
    }

    public void resetLastCheckTime() {
        lastCheckTime = System.nanoTime();
    }

    public void addBytes(long recBytesNum) {
        lastBytesReceived += recBytesNum;
        totalBytesReceived += recBytesNum;
    }

    public void setFlagToDelete() {
        willBeDeleted = true;
    }

    public boolean getFlagToDelete() {
        return willBeDeleted;
    }
}

package org.example;

public class TransferStats {
    private long totalBytesReceived = 0;
    private final long startTime = System.currentTimeMillis();
    private long lastCheckTime = startTime;
    private long lastBytesReceived = 0;
    private boolean willBeDeleted = false;

    public long getInstantaneousSpeed() {
        return lastBytesReceived * 1000 / (System.currentTimeMillis() - lastCheckTime);
    }

    public long getAverageSpeed() {
        return totalBytesReceived * 1000 / (System.currentTimeMillis() - startTime);
    }

    public void resetLastBytesReceived() {
        lastBytesReceived = 0;
    }

    public void resetLastCheckTime() {
        lastCheckTime = System.currentTimeMillis();
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

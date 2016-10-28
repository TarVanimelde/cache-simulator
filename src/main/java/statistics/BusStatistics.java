package statistics;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class BusStatistics {
  private static final AtomicLong bytesWritten = new AtomicLong(0);
  private static final AtomicInteger busWrites = new AtomicInteger(0);
  private static final AtomicInteger busReads = new AtomicInteger(0);
  private static final AtomicInteger busUpdates = new AtomicInteger(0);
  private static final AtomicInteger flushes = new AtomicInteger(0);
  private static final AtomicInteger busInvalidations = new AtomicInteger(0);

  public void addBytesWritten(int bytesTransferred) {
    bytesWritten.addAndGet(bytesTransferred);
  }

  public void incrementBusWrites() {
    busWrites.incrementAndGet();
  }

  public void incrementBusReads() {
    busReads.incrementAndGet();
  }

  public void incrementBusUpdates() {
    busUpdates.incrementAndGet();
  }

  public void incrementFlushes() {
    flushes.incrementAndGet();
  }

  public int getBusReads() {
    return busReads.get();
  }

  public int getBusWrites() {
    return busWrites.get();
  }

  public int getBusUpdates() {
    return busUpdates.get();
  }

  public int getFlushes() {
    return flushes.get();
  }

  public int getInvalidations() { return busInvalidations.get(); }

  public long getBytesWritten() {
    return bytesWritten.get();
  }

  public String toString() {
    return "Bytes transferred on bus: " + getBytesWritten()
        + "\nBus reads: " +  getBusReads()
        + "\nBus writes: " + getBusWrites()
        + "\nBus updates: " + getBusUpdates()
        + "\nBus flushes: " + getFlushes()
        + "\nBus invalidations: " + getInvalidations();
  }

  public void reset() {
    bytesWritten.set(0);
    busWrites.set(0);
    busReads.set(0);
    busUpdates.set(0);
    flushes.set(0);
    busInvalidations.set(0);
  }

  public void incrementBusInvalidations() {
    busInvalidations.incrementAndGet();
  }
}

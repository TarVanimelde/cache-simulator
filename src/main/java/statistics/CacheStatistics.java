package statistics;

import java.util.concurrent.atomic.AtomicInteger;

public class CacheStatistics {
  private final AtomicInteger writes = new AtomicInteger(0);
  private final AtomicInteger writeMisses = new AtomicInteger(0);
  private final AtomicInteger reads = new AtomicInteger(0);
  private final AtomicInteger readMisses = new AtomicInteger(0);
  private final int id;

  public CacheStatistics(int id) {
    this.id = id;
  }

  public void incrementReads() {
    reads.incrementAndGet();
  }

  public void incrementReadMisses() {
    readMisses.incrementAndGet();
  }

  public void incrementWrites() {
    writes.incrementAndGet();
  }

  public void incrementWriteMisses() {
    writeMisses.incrementAndGet();
  }

  public int getReads() {
    return this.reads.get();
  }

  public int getReadMisses() {
    return this.readMisses.get();
  }

  public int getWrites() {
    return this.writes.get();
  }

  public int getWriteMisses() {
    return this.writeMisses.get();
  }

  public int getId() {
    return id;
  }

  public String toString() {
    return "Cache ID: " + getId();
//        + "Reads: " + getReads() + "\n"
//        + "Read misses: " + getReadMisses() + "\n"
//        + "Read hit rate: " + (double)(getReads() - getReadMisses())/getReads();//TODO
  }
}

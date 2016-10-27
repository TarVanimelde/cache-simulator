package statistics;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Processor-level statistics accumulator.
 */
public class ProcessorStatistics {

  private CacheStatistics l1Stats;
  /**
   * Have the statistics for a cache been added to this processor statistics accumulator?
   */
  private boolean cacheStatsAttached = false;

  /*
    The number of cycles that the processor has run.
   */
  private final AtomicInteger cycleCount = new AtomicInteger(0);

  private final AtomicInteger writeHits = new AtomicInteger(0);
  private final AtomicInteger writeMisses = new AtomicInteger(0);
  private final AtomicInteger readHits = new AtomicInteger(0);
  private final AtomicInteger readMisses = new AtomicInteger(0);

  /**
   * Increases the number of cycles the processor has run by one.
   */
  public void incrementCycles() {
    cycleCount.incrementAndGet();
  }

  public void attachCacheStats(CacheStatistics l1Stats) {
    if (!cacheStatsAttached) {
      this.l1Stats = l1Stats;
      cacheStatsAttached = true;
    } else {
      Logger.getLogger(ProcessorStatistics.class.getName()).log(Level.WARNING,
          "Tried to attach more than one cache statistics to a processor's statistics.");
    }
  }

  /**
   * Return the number of cycles that the processor has run.
   */
  public int getNumCycles() {
    return cycleCount.get();
  }

  public void incrementReadHit() {
    readHits.incrementAndGet();
  }

  public void incrementReadMiss() {
    readMisses.incrementAndGet();
  }

  public void incrementWriteHit() {
    writeHits.incrementAndGet();
  }

  public void incrementWriteMiss() {
    writeMisses.incrementAndGet();
  }

  public int getWriteHits() {
    return writeHits.get();
  }

  public int getWriteMisses() {
    return writeMisses.get();
  }

  public int getReadHits() {
    return readHits.get();
  }

  public int getReadMisses() {
    return readMisses.get();
  }

  public int getNumReads() {
    return getReadHits() + getReadMisses();
  }

  public int getNumWrites() {
    return getWriteHits() + getWriteMisses();
  }

  public int getNumAccesses() {
    return getNumReads() + getNumWrites();
  }

  public double getDataMissRate() {
    return ((double)getReadMisses() + getWriteMisses()) / getNumAccesses();
  }

  public ProcessorStatistics combine(ProcessorStatistics other) {
    ProcessorStatistics sum = new ProcessorStatistics();
    sum.cycleCount.set(Math.max(cycleCount.get(), other.cycleCount.get()));
    sum.writeHits.set(writeHits.get() + other.writeHits.get());
    sum.writeMisses.set(writeMisses.get() + other.writeMisses.get());
    sum.readHits.set(readHits.get() + other.readHits.get());
    sum.readMisses.set(readMisses.get() + other.readMisses.get());
    // Don't combine cache statistics?
    // TODO
    return sum;
  }

  public String toString() {
    return (cacheStatsAttached ?
            l1Stats.toString() + "\n" : "")
        + "Cycles: " + getNumCycles()
        + "\nData miss rate: " + getDataMissRate();
  }

}
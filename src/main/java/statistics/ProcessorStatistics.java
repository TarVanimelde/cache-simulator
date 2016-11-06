package statistics;

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
  private int cycleCount = 0;

  private int writeHits = 0;
  private int writeMisses = 0;
  private int readHits = 0;
  private int readMisses = 0;

  /**
   * Increases the number of cycles the processor has run by one.
   */
  public void incrementCycles() {
    cycleCount++;
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
    return cycleCount;
  }

  public void incrementReadHit() {
    readHits++;
  }

  public void incrementReadMiss() {
    readMisses++;
  }

  public void incrementWriteHit() {
    writeHits++;
  }

  public void incrementWriteMiss() {
    writeMisses++;
  }

  public int getWriteHits() {
    return writeHits;
  }

  public int getWriteMisses() {
    return writeMisses;
  }

  public int getReadHits() {
    return readHits;
  }

  public int getReadMisses() {
    return readMisses;
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
    sum.cycleCount = Math.max(cycleCount, other.cycleCount);
    sum.writeHits = writeHits + other.writeHits;
    sum.writeMisses = writeMisses + other.writeMisses;
    sum.readHits = readHits + other.readHits;
    sum.readMisses = readMisses + other.readMisses;
    // Don't combine cache statistics?
    // TODO
    return sum;
  }

  @Override
  public String toString() {
    return (cacheStatsAttached ?
            l1Stats.toString() + "\n" : "")
        + "Cycles: " + getNumCycles()
        + "\nData miss rate: " + String.format("%.8f", getDataMissRate());
  }

}

package statistics;

public class BusStatistics {
  private long bytesWritten = 0L;
  private int busWrites = 0;
  private int busReads = 0;
  private int busUpdates = 0;
  private int busUpgrades = 0;
  private int flushes = 0;
  private int busInvalidations = 0;
  private long numWrites = 0;
  private long writeLatencies = 0;

  public void addBytesWritten(int bytesTransferred) {
    bytesWritten += bytesTransferred;
  }

  public void incrementBusWrites() {
    busWrites++;
  }

  public void incrementBusReads() {
    busReads++;
  }

  public void incrementBusUpdates() {
    busUpdates++;
  }

  public void incrementBusUpgrades() {
    busUpgrades++;
  }

  public void incrementFlushes() {
    flushes++;
  }

  public int getBusReads() {
    return busReads;
  }

  public int getBusWrites() {
    return busWrites;
  }

  public int getBusUpdates() {
    return busUpdates;
  }

  public int getBusUpgrades() {
    return busUpgrades;
  }

  public int getFlushes() {
    return flushes;
  }

  public int getInvalidations() { return busInvalidations; }

  public long getBytesWritten() {
    return bytesWritten;
  }

  public long getAverageWriteLatency() {
    return numWrites == 0 ? 0L : writeLatencies / numWrites;
  }

  @Override
  public String toString() {
    return "Bytes transferred on bus: " + getBytesWritten()
        + "\nBus reads: " +  getBusReads()
        + "\nBus writes: " + getBusWrites()
        + "\nBus upgrades: " + getBusUpgrades()
        + "\nBus updates: " + getBusUpdates()
        + "\nBus flushes: " + getFlushes()
        + "\nBus invalidations: " + getInvalidations()
        + "\nAverage write latency: " + getAverageWriteLatency();
  }

  public void reset() {
    bytesWritten = 0;
    busWrites = 0;
    busReads = 0;
    busUpdates = 0;
    busUpgrades = 0;
    flushes = 0;
    busInvalidations = 0;
    numWrites = 0;
    writeLatencies = 0;
  }

  public void incrementBusInvalidations() {
    busInvalidations++;
  }

  public void addWriteLatency(long writeLatency) {
    numWrites++;
    writeLatencies += writeLatency;
  }
}

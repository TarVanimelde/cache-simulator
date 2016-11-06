package statistics;

public class BusStatistics {
  private static long bytesWritten = 0L;
  private static int busWrites = 0;
  private static int busReads = 0;
  private static int busUpdates = 0;
  private static int flushes = 0;
  private static int busInvalidations = 0;
  private static int numWrites = 0;
  private static long writeLatencies = 0;

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

  public int getFlushes() {
    return flushes;
  }

  public int getInvalidations() { return busInvalidations; }

  public long getBytesWritten() {
    return bytesWritten;
  }

  public long getAverageWriteLatency() {
    return writeLatencies / ((long)getBusWrites() + getBusUpdates());
  }

  @Override
  public String toString() {
    return "Bytes transferred on bus: " + getBytesWritten()
        + "\nBus reads: " +  getBusReads()
        + "\nBus writes: " + getBusWrites()
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
    flushes = 0;
    busInvalidations = 0;
  }

  public void incrementBusInvalidations() {
    busInvalidations++;
  }

  public void addWriteLatency(int writeLatency) {
    numWrites++;
    writeLatencies += writeLatency;
  }
}

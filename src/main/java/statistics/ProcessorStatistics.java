package statistics;

/**
 * Processor-level statistics accumulator.
 */
public class ProcessorStatistics {

  private final int id;

  /*
    The number of cycles that the processor has run.
   */
  private int cycleCount = 0;

  private int writeHits = 0;
  private int writeMisses = 0;
  private int readHits = 0;
  private int readMisses = 0;

  private int privateAccesses = 0;
  private int sharedAccesses = 0;

  public ProcessorStatistics() {
    this.id = -1;
  }

  public ProcessorStatistics(int id) {
    this.id = id;
  }


  public int getId() {
    return id;
  }

  public void incrementPrivateAccesses() {
    privateAccesses++;
  }

  public void incrementSharedAccesses() {
    sharedAccesses++;
  }

  public int getPrivateAccesses() {
    return privateAccesses;
  }

  public int getSharedAccesses() {
    return sharedAccesses;
  }

  /**
   * Increases the number of cycles the processor has run by one.
   */
  public void incrementCycles() {
    cycleCount++;
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
    sum.privateAccesses = privateAccesses + other.privateAccesses;
    sum.sharedAccesses = sharedAccesses + other.sharedAccesses;

    return sum;
  }

  @Override
  public String toString() {
    return "Cycles: " + getNumCycles()
        + "\nData miss rate: " + String.format("%.8f", getDataMissRate())
        + "\nShared accesses: " + getSharedAccesses()
        + "\nPrivate accesses: " + getPrivateAccesses();
  }

}

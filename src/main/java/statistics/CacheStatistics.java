package statistics;

public class CacheStatistics {
  private int writes = 0;
  private int writeMisses = 0;
  private int reads = 0;
  private int readMisses = 0;
  private final int id;

  public CacheStatistics(int id) {
    this.id = id;
  }

  public void incrementReads() {
    reads++;
  }

  public void incrementReadMisses() {
    readMisses++;
  }

  public void incrementWrites() {
    writes++;
  }

  public void incrementWriteMisses() {
    writeMisses++;
  }

  public int getReads() {
    return this.reads;
  }

  public int getReadMisses() {
    return this.readMisses;
  }

  public int getWrites() {
    return this.writes;
  }

  public int getWriteMisses() {
    return this.writeMisses;
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

package statistics;

public class CacheStatistics {
  private final int id;

  public CacheStatistics(int id) {
    this.id = id;
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

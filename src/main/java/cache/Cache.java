package cache;

import bus.Bus;
import bus.BusJob;
import cache.coherence.CoherenceState;
import statistics.CacheStatistics;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Cache {

  private final CacheStatistics stats;

  private List<CacheSet> sets; // The cache blocks, organized into sets.

  private BusJob busJob = BusJob.EMPTY_JOB; // The job the proc wants to/is performing on the bus.
  private boolean isFlushing = false; // Is the cache in the process of flushing a block?
  private Address flushTarget = new Address(-1); // The block being flushed.

  private final int id; // The unique cache ID.
  private static int idGenerator = 0; // A cache ID generator.

  public Cache() {
    this.id = idGenerator;
    idGenerator++;
    stats = new CacheStatistics(id);

    int numSets = CacheProperties.getNumSets();
    this.sets = new ArrayList<>(numSets);
    for (int i = 0; i < CacheProperties.getNumSets(); i++) {
      sets.add(new CacheSet(this, stats));
    }
  }

  public int getId() {
    return id;
  }

  public void procRead(Address address) {
    getSet(address).read(address);
  }

  public void procWrite(Address address) {
    getSet(address).write(address);
  }

  public void remoteRead(Address address) { getSet(address).remoteRead(address); }

  public void remoteWrite(Address address) {
    getSet(address).remoteWrite(address);
  }

  public void remoteUpdate(Address address) { getSet(address).remoteUpdate(address); }

  public boolean isBlocking() {
    return !busJob.isFinished() || !busJob.successorFinished() || isFlushing;
  }

  /**
   * Returns the set that the address is mapped to.
   */
  private CacheSet getSet(Address address) {
    return sets.get(address.getIndex());
  }

  /*
  Returns whether the cache contains a copy of the given memory address.
 */
  public boolean contains(Address address) {
    return getSet(address).contains(address);
  }

  /**
   * Returns true if there is a block already mapped to the address or if there is an unused block
   * available for the address to inhabit.
   * @param address the memory address that requires a block in the cache.
   */
  public boolean hasBlockAvailableFor(Address address) {
    return contains(address) || getSet(address).hasUnusedBlock();
  }

  /**
   * Processor issues a request to allocate space for a block such that the given memory address can
   * be added to the cache.
   */
  public void allocateBlockFor(Address address) {
    if (!hasBlockAvailableFor(address)) {
      getSet(address).startEvictionFor(address);
    }
  }

  /**
   * Finishes the eviction process for the address, to be called only from BusJob. Evicts an
   * appropriate block so that there is an empty block available for the cache that the address can
   * be placed into.
   */
  public void finishEvictionFor(Address address) {
    getSet(address).finishLruEviction();
  }

  public void setJob(BusJob job) {
    if (busJob.isFinished()) {
      busJob = job;
      Bus.enqueue(job);
    } else {
      Logger.getLogger(getClass().getName()).log(Level.SEVERE, "More than one job in the cache!");
    }
  }

  public void setState(Address address, CoherenceState state) {
    getSet(address).setState(address, state);
  }

  public void startFlush(Address address) {
    if (!isFlushing || address.equals(flushTarget)) {
      flushTarget = address;
      isFlushing = true;
    } else {
      Logger.getLogger(getClass().getName()).log(Level.SEVERE, "More than one flush in the cache!");
    }
  }

  public void finishFlush(Address address, CoherenceState finalState) {
    setState(address, finalState);
    isFlushing = false;
  }

  public CacheStatistics getStatistics() {
    return stats;
  }
}

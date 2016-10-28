package cache;

import bus.BusJob;
import cache.coherence.CoherenceState;
import statistics.CacheStatistics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Cache {

  private final CacheStatistics stats;

  private List<CacheSet> sets; // The cache blocks, organized into sets.

  private BusJob busJob = BusJob.EMPTY_JOB; // The job the proc wants to/is performing on the bus.
  private boolean awaitingBus = false; // Is the cache waiting to finish a bus job?
  private boolean isFlushing = false; // Is the cache in the process of flushing a block?
  private boolean isUpdating = false; // Is the cache in the process of having a word updated?

  private final int id; // The unique cache ID.
  private static AtomicInteger idGenerator = new AtomicInteger(0); // A cache ID generator.

  public Cache() {
    this.id = idGenerator.getAndIncrement();
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
    getSet(address).remoteReadExclusive(address);
  }

  public void remoteUpdate(Address address) { getSet(address).remoteUpdate(address); }

  /**
   * Returns the set that the address is mapped to.
   */
  private CacheSet getSet(Address address) {
    return sets.get(address.getIndex());
  }

  public boolean isBlocking() {
    return awaitingBus || isFlushing || isUpdating;
  }

  public void setJobFinished() {
    this.awaitingBus = false;
  }

  public boolean hasJob() {
    return awaitingBus;
  }

  public BusJob getJob() {
    return hasJob() ? busJob : BusJob.EMPTY_JOB;
  }

  /**
   * Finishes the eviction process for the address, to be called only from BusJob. Evicts an
   * appropriate block so that there is an empty block available for the cache that the address can
   * be placed into.
   */
  public void finishEvictionFor(Address address) {
    getSet(address).evictLru();
  }

  public void putJob(BusJob job) {
    if (awaitingBus || !busJob.isFinished()) {
      Logger.getLogger(getClass().getName())
          .log(Level.SEVERE, "More than one job in the cache!" + job.toString());
    } else {
      awaitingBus = true;
      this.busJob = job;
    }
  }

  public void setState(Address address, CoherenceState state) {
    this.getSet(address).setState(address, state);
  }

  /*
    Returns whether the cache contains a copy of the given memory address.
   */
  public boolean contains(Address address) {
    return getSet(address).contains(address);
  }

  public CacheStatistics getStatistics() {
    return stats;
  }

  public void startFlush() {
    if (isFlushing) {
      Logger.getLogger(getClass().getName()).log(Level.SEVERE, "More than one flush in the cache!");
    } else {
      isFlushing = true;
    }
  }

  public void startUpdate() {
    if (isUpdating) {
      Logger.getLogger(getClass().getName()).log(Level.SEVERE, "More than one update job in the cache!");
    } else {
      isUpdating = true;
    }
  }

  public void finishFlush(Address address, CoherenceState finalState) {
    setState(address, finalState);
    isFlushing = false;
  }

  public void finishUpdate(Address address, CoherenceState finalState) {
    setState(address, finalState);
    isUpdating = false;
  }

  /**
   * Returns true if there is a block already mapped to the address or if there is an unused block
   * available for the address to inhabit.
   * @param address the memory address that requires a block in the cache.
   */
  public boolean hasBlockAvailableFor(Address address) {
    return contains(address) || getSet(address).hasAvailableBlock();
  }

  /**
   * Processor issues a request to evict a block such that the given memory address can be added to
   * the cache.
   */
  public void procEvictBlockFor(Address address) {
    if (!hasBlockAvailableFor(address)) {
      this.getSet(address).procEvict(address);
    }
  }
}

package bus;

import cache.Cache;
import cache.Address;
import cache.coherence.CoherenceState;
import statistics.BusStatistics;

import java.util.*;

public class Bus {
  private static Deque<BusJob> jobQueue = new ArrayDeque<>();

  /*
    The job currently using the bus.
   */
  private static BusJob currentJob = BusJob.EMPTY_JOB;

  /**
   * The caches attached to the bus.
   */
  private static final List<Cache> caches = new ArrayList<>();

  /**
   * Statistics aggregator for bus information.
   */
  private static final BusStatistics stats = new BusStatistics();

  private static boolean flushing = false; // Is the bus currently being used to flush to memory?
  private static FlushJob flushJob;

  /*
   * The number of cycles it takes to perform certain actions over the bus:
   */
  public static final int READ_FROM_MEM_CYCLES = 100; // It takes 100 cycles to read a block from memory.
  public static final int WRITE_TO_MEM_CYCLES = 100; // It takes 100 cycles to write a block to memory.
  public static final int READ_WORD_CYCLES = 1; // It takes one cycle to send one word over the bus.

  public static void tick() {
    if (flushing) {
      // Prioritize flushing data when necessary
      flushJob.tick();
      if (flushJob.finished()) {
        flushing = false;
      }
      return;
    }

    // Get the next job if the current job is isFinished:
    if (currentJob.isFinished()) {
      // First check if it has a successor:
      currentJob.getSuccessor().ifPresent(successor -> {
        currentJob = successor;
        currentJob.start();
        });

      // Next check the regular queue:
      if (currentJob.isFinished() && !jobQueue.isEmpty()) {
        currentJob = jobQueue.pop();
        currentJob.start();
      }
    }

    if (!currentJob.isFinished()) {
      currentJob.tick();
    }
  }

  public static void enqueue(BusJob job) {
    jobQueue.addLast(job);
  }

  public static boolean remoteCacheContains(Cache local, Address address) {
    return numRemoteCachesContaining(local, address) > 0;
  }

  public static int numRemoteCachesContaining(Cache local, Address address) {
    /*
       The long to int cast's safe since there will never be more than int.max number of caches.
    */
    return (int)caches.stream()
        .filter(c -> c.getId() != local.getId())
        .filter(c -> c.contains(address))
        .count();
  }

  public static void broadcastRemoteWrite(Cache origin, Address address)  {
    caches.stream()
        .filter(c -> c.getId() != origin.getId()) // A cache doesn't broadcast to itself.
        .forEach(c -> c.remoteWrite(address));
  }

  public static void broadcastRemoteRead(Cache origin, Address address)  {
    caches.stream()
        .filter(c -> c.getId() != origin.getId()) // A cache doesn't broadcast to itself.
        .forEach(c -> c.remoteRead(address));
  }

  public static void broadcastRemoteUpdate(Cache origin, Address address) {
    caches.stream()
        .filter(c -> c.getId() != origin.getId()) // A cache doesn't broadcast to itself.
        .forEach(c -> c.remoteUpdate(address));
  }

  /**
   *
   * @param cache the cache flushing a block
   * @param address the block being flushed.
   * @param finalState the state the block will be in once it is done being flushed.
   */
  public static void flush(Cache cache, Address address, CoherenceState finalState) {
    cache.startFlush(address); // Make the cache block while flushing if it isn't already blocking.
    if (!flushing) {
      flushJob = new FlushJob(address);
      flushing = true;
    }
    flushJob.addCacheToFlush(cache, address, finalState);
    stats.incrementFlushes();
  }

  /**
   * Attaches a cache to the bus.
   */
  public static void add(Cache cache) {
    caches.add(cache);
  }

  public static BusStatistics getStatistics() {
    return stats;
  }

  public static void reset() {
    jobQueue.clear();
    caches.clear();
    flushing = false;
    currentJob = BusJob.EMPTY_JOB;
    stats.reset();
  }
}

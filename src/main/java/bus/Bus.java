package bus;

import cache.Cache;
import cache.Address;
import cache.coherence.CoherenceState;
import statistics.BusStatistics;

import java.util.ArrayList;
import java.util.List;

public class Bus {
  /**
   * The job currently using the bus.
   */
  private static BusJob currentJob = BusJob.EMPTY_JOB;

  /**
   * The caches attached to the bus.
   */
  private static List<Cache> caches = new ArrayList<>();

  /**
   * Statistics aggregator for bus information.
   */
  public static final BusStatistics stats = new BusStatistics();

  /**
   * The index of caches that currently has a job executing.
   */
  private static int executingCache = 0;

  private static boolean flushing = false; // Is the bus currently being used to flush to memory?
  private static FlushJob flushJob;

  private static boolean propagatingUpdate = false; // Is an update being sent to remote caches?
  private static UpdateJob updateJob;

  /*
   * The number of cycles it takes to perform certain actions over the bus:
   */
  public static final int READ_FROM_MEM = 100; // It takes 100 cycles to read a block from memory.
  public static final int WRITE_TO_MEM = 100; // It takes 100 cycles to write a block to memory.
  public static final int READ_WORD = 1; // It takes one cycle to send one word over the bus.

  /**
   * Grants use of the bus using round robin arbitration. If memory is being flushed to memory,
   * this gets priority over other jobs.
   */
  public static void tick() {
    if (propagatingUpdate) {
      // Prioritize updating data when necessary
      updateJob.tick();
      if (updateJob.finished()) {
        propagatingUpdate = false;
        stats.addBytesWritten(updateJob.getBytesTransferred());
      }
      return;
    }

    if (flushing) {
      // Prioritize flushing data when necessary
      flushJob.tick();
      if (flushJob.finished()) {
        flushing = false;
        stats.addBytesWritten(flushJob.getBytesTransferred());
      }
      return;
    }

    if (currentJob.isFinished()) {
      stats.addBytesWritten(currentJob.getBytesTransferred());
      // Find the next job for the bus:
      boolean foundJob = false;
      for (int i = 0; i < caches.size(); i++) {
        int c = (executingCache + i + 1) % caches.size();
        if (caches.get(c).hasJob()) {
          foundJob = true;
          executingCache = c;
          break;
        }
      }

      if (foundJob) {
        Cache cache = caches.get(executingCache);
        currentJob = cache.getJob();
        currentJob.start();
        currentJob.tick();
      } else {
        executingCache++;
      }

    } else {
      // Current job is still processing, just tick it.
      currentJob.tick();
    }
  }

  public static boolean remoteCacheContains(Cache local, Address address) {
    return numRemoteCachesContaining(local, address) > 0;
  }

  public static int numRemoteCachesContaining(Cache local, Address address) {
    /*
    Note: the long to int cast is safe since there will never be more than int's capacity number of
    caches.
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
    cache.startFlush(); // Make the cache block while flushing if it isn't already blocking.
    if (!flushing) {
      flushJob = new FlushJob(address);
      flushing = true;
    }
    flushJob.addCacheToFlush(cache, address, finalState);
    stats.incrementFlushes();
  }

  public static void update(Cache cache, Address address, CoherenceState finalState) {
    cache.startUpdate(); // Make the cache block while updating if it isn't already blocking.
    if (!propagatingUpdate) {
      updateJob = new UpdateJob(address);
      propagatingUpdate = true;
    }
    updateJob.update(cache, address, finalState);
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
    caches.clear();
    flushing = false;
    propagatingUpdate = false;
    executingCache = 0;
    stats.reset();
  }
}

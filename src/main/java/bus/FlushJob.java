package bus;

import cache.Cache;
import cache.CacheProperties;
import cache.CycleCountdown;
import cache.Address;
import cache.coherence.CoherenceState;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Special class for flushing blocks from the cache to memory.
 */
public class FlushJob {
  private final Address address;
  private Map<Cache, CoherenceState> stateOnFinish = new HashMap<>();
  private CycleCountdown cycleCountdown;
  private boolean started = false;

  public FlushJob(Address address) {
    this.address = address;
  }

  public void tick() {
    if (!started) {
      start();
    }

    if (!finished()) {
      cycleCountdown.tick();
      if (finished()) {
        // Done, set the states of all the cache blocks:
        stateOnFinish.entrySet()
            .forEach(entry -> entry.getKey().finishFlush(address, entry.getValue()));
      }
    }
  }

  private void start() {
    if (!started) {
      cycleCountdown = new CycleCountdown(Bus.WRITE_TO_MEM * stateOnFinish.entrySet().size());
      started = true;
    }
  }

  public void addCacheToFlush(Cache c, Address address, CoherenceState finalState) {
    if (!this.address.equals(address)) {
      Logger.getLogger(getClass().getName())
          .log(Level.SEVERE, "Attempted to flush different blocks simultaneously: "
          + address.toString() + ", " + this.address.toString());
    } else {
      stateOnFinish.put(c, finalState);
    }
  }

  /**
   * The number of bytes transferred over the bus is the number of caches that flush times the
   * number of cycles it takes for one cache to flush a block to memory.
   */
  public int getBytesTransferred() {
    return CacheProperties.getBlockSize() * stateOnFinish.entrySet().size();
  }

  public boolean finished() {
    return cycleCountdown.finished();
  }
}

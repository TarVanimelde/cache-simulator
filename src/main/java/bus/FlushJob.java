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
        Bus.getStatistics().addBytesWritten(CacheProperties.getBlockSize());
      }
    }
  }

  private void start() {
    if (!started) {
      cycleCountdown = new CycleCountdown(Bus.WRITE_TO_MEM_CYCLES);
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

  public boolean finished() {
    return cycleCountdown.isFinished();
  }
}

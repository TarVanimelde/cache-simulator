package bus;

import cache.Cache;
import cache.CycleCountdown;
import cache.Address;
import cache.coherence.CoherenceState;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Special class for updating a word in remote caches that contain the appropriate block.
 */
public class UpdateJob {
  private final Address address;
  private Map<Cache, CoherenceState> stateOnFinish = new HashMap<>();
  private CycleCountdown cycleCountdown = new CycleCountdown(Bus.READ_WORD);

  public UpdateJob(Address address) {
    this.address = address;
  }

  public void tick() {
    if (!finished()) {
      cycleCountdown.tick();
      if (finished()) {
        // Done, set the states of all the cache blocks:
        stateOnFinish.entrySet()
            .forEach(entry -> entry.getKey().finishUpdate(address, entry.getValue()));
      }
    }
  }

  public void update(Cache c, Address address, CoherenceState finalState) {
    if (!this.address.equals(address)) {
      Logger.getLogger(getClass().getName())
          .log(Level.SEVERE, "Attempted to flush different blocks simultaneously: "
              + address.toString() + ", " + this.address.toString());
    } else {
      stateOnFinish.put(c, finalState);
    }
  }

  public boolean finished() {
    return cycleCountdown.finished();
  }

  public int getBytesTransferred() {
    return Bus.READ_WORD;
  }
}

package bus;

import cache.Address;
import cache.Cache;
import cache.CycleCountdown;
import cache.coherence.CoherenceState;

import java.util.Hashtable;
import java.util.Map;

// TODO: currently not used, abstraction of FlushJob and UpdateJob.
public abstract class PriorityJob {
  protected CycleCountdown countdown = new CycleCountdown(0);
  protected Map<Cache, CoherenceState> stateOnFinish = new Hashtable<>();
  protected Address target;

  protected PriorityJob(Address address) {
    this.target = address;
  }

  public void tick() {
    if (!started()) {
      start();
    }

    if (!finished()) {
      countdown.tick();
      if (finished()) {
        // Done, set the states of all the cache blocks:
        stateOnFinish.entrySet()
            .forEach(entry -> entry.getKey().finishFlush(target, entry.getValue()));
      }
    }
  }

  public void addCache(Cache c, Address address, CoherenceState finalState) {
    if (!address.equals(target)) {
      // TODO: error!
    }
    stateOnFinish.put(c, finalState);
  }

  abstract void start();
  abstract boolean started();
  abstract boolean finished();
  abstract int getBytesTransferred();
}

package cache.coherence;

import bus.Bus;
import bus.BusAction;
import bus.BusJob;
import cache.Address;
import cache.Cache;

import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DragonCacheBlock extends CacheBlock {

  public DragonCacheBlock(Cache cache, Address address) {
    super(cache, address);
  }

  @Override
  public void readBlock(Address address) {
    switch (this.state) {
      case I:
        /*
          The state after reading in the block will depend on whether any other
          caches hold a copy of the data at the time the job is finished:
         */
        BiFunction<Cache, Address, CoherenceState> finalState = (Cache local, Address a) ->
            Bus.remoteCacheContains(local, a) ? CoherenceState.SC : CoherenceState.E;
        BusJob busRd = new BusJob(cache, address, BusAction.BUSRD, finalState);
        cache.putJob(busRd);
        break;
      case E:
        // State is not changed by a local read.
        break;
      case M:
        // State is not changed by a local read.
        break;
      case SC:
        // State is not changed by a local read.
        break;
      case SM:
        // State is not changed by a local read.
        break;
      default:
        Logger.getLogger(getClass().getName()).log(Level.SEVERE,
            "Invalid state in Dragon Read" + this.state);

    }
  }

  @Override
  public void writeBlock(Address address) {
    /*
      The state after writing to the block will depend on whether any other caches hold a copy of
      the data at the time the job is finished:
    */
    BiFunction<Cache, Address, CoherenceState> finalState = (Cache local, Address a) ->
        Bus.remoteCacheContains(local, a) ? CoherenceState.SM : CoherenceState.M;
    switch (this.state) {
      case I:
        BusJob busUpdI = new BusJob(cache, address, BusAction.BUSRD, finalState);
        cache.putJob(busUpdI);
        break;
      case E:
          this.state = CoherenceState.M;
        break;
      case M:
        // State is not changed by a local update.
        break;
      case SC:
        BusJob busRdSc = new BusJob(cache, address, BusAction.BUSUPD, finalState);
        cache.putJob(busRdSc);
        break;
      case SM:
        BusJob busRdM = new BusJob(cache, address, BusAction.BUSUPD, finalState);
        cache.putJob(busRdM);
        break;
      default:
        Logger.getLogger(getClass().getName()).log(Level.SEVERE,
            "Invalid state in Dragon Write: " + this.state);
    }
  }

  @Override
  public void remoteRead(Address address) {
    switch (this.state) {
      case I:
        // State is not changed by a remote read.
        break;
      case E:
        this.state = CoherenceState.SC;
        break;
      case M:
        Bus.flush(cache, address, CoherenceState.SM);
        break;
      case SC:
        // State is not changed by a remote read.
        break;
      case SM:
        Bus.flush(cache, address, CoherenceState.SM);
        break;
      default:
        Logger.getLogger(getClass().getName()).log(Level.SEVERE,
            "Invalid state in Dragon Remote Read: " + this.state);
    }
  }

  @Override
  public void remoteReadExclusive(Address address) {
    // This doesn't happen in the dragon protocol. Do you know what happens instead? Updates.
    Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Saw BUSRDX in the Dragon protocol?");
  }

  @Override
  public void remoteUpdate(Address address) {
    switch (this.state) {
      case I:
        // State is not changed by a remote update.
        break;
      case E:
        // A remote update cannot happen in E state:
        Logger.getLogger(getClass().getName()).log(Level.SEVERE,
            "Saw a remote update in Dragon while in E");
        break;
      case M:
        // A remote update cannot happen in M state:
        Logger.getLogger(getClass().getName()).log(Level.SEVERE,
            "Saw a remote update in Dragon while in M");
        break;
      case SC:
        Bus.update(cache, address, CoherenceState.SC);
        break;
      case SM:
        Bus.update(cache, address, CoherenceState.SM);
        break;
      default:
        Logger.getLogger(getClass().getName()).log(Level.SEVERE,
            "Invalid state in Dragon Remote Update: " + this.state);
    }
  }

  @Override
  public boolean writeBackOnEvict() {
    switch (this.state) {
      case I:
        return false;
      case E:
        return false;
      case SC:
        return false;
      case SM:
        return true;
      case M:
        return true;
      default:
        return false;
    }
  }
}

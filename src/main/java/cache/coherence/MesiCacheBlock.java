package cache.coherence;

import bus.Bus;
import bus.BusAction;
import bus.BusJob;
import cache.Address;
import cache.Cache;

import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MesiCacheBlock extends CacheBlock {
  public MesiCacheBlock(Cache cache, Address address) {
    super(cache, address);
  }

  @Override
  public void readBlock(Address address) {
    switch (this.state) {
      case M:
        // State is not changed by a local read.
        break;
      case E:
        // State is not changed by a local read.
        break;
      case S:
        // State is not changed by a local read.
        break;
      case I:
        /*
          The state after reading in the block will depend on whether any other caches hold a copy
          of the data at the time the job is finished:
         */
        BiFunction<Cache, Address, CoherenceState> finalState = (Cache local, Address a) ->
            Bus.remoteCacheContains(local, a) ? CoherenceState.S : CoherenceState.E;

        BusJob job = new BusJob(cache, address, BusAction.BUSRD, finalState);
        cache.putJob(job);
        break;
      default:
        Logger.getLogger(getClass().getName()).log(Level.SEVERE,
            "Invalid state in MESI Read" + this.state);
        break;
    }
  }

  @Override
  public void writeBlock(Address address) {
    BusJob busRdX = new BusJob(cache, address, BusAction.BUSRDX,
        (Cache local, Address a) ->CoherenceState.M);
    switch (this.state) {
      case M:
        // State is not changed by a local write.
        break;
      case E:
        this.state = CoherenceState.M;
        break;
      case S:
        cache.putJob(busRdX);
        break;
      case I:
        cache.putJob(busRdX);
        break;
      default:
        Logger.getLogger(getClass().getName()).log(Level.SEVERE,
            "Invalid state in MESI Write" + this.state);
        break;
    }
  }

  @Override
  public void remoteRead(Address address) {
    switch (this.state) {
      case M:
        Bus.flush(cache, address, CoherenceState.S);
        break;
      case E:
        //Bus.flush(cache, address, CoherenceState.S);
        this.state = CoherenceState.S; // TODO
        break;
      case S:
        // State is not changed by a remote read.
        break;
      case I:
        // State is not changed by a remote read.
        break;
      default:
        Logger.getLogger(getClass().getName()).log(Level.SEVERE,
            "Invalid state in MESI Remote Read" + this.state);
        break;
    }
  }

  @Override
  public void remoteReadExclusive(Address address) {
    switch (this.state) {
      case M:
        Bus.flush(cache, address, CoherenceState.I);
        break;
      case E:
        this.state = CoherenceState.I;
        //Bus.flush(cache, address, CoherenceState.I); // TODO
        break;
      case S:
        this.state = CoherenceState.I;
        break;
      case I:
        // State is not changed by a remote write.
        break;
      default:
        Logger.getLogger(getClass().getName()).log(Level.SEVERE,
            "Invalid state in MESI Remote Write" + this.state);
        break;
    }
  }

  @Override
  public void remoteUpdate(Address address) {
    // Do nothing: MESI is an invalidation-based protocol.
    Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Remote update in the MESI protocol?");
  }

  @Override
  public boolean writeBackOnEvict() {
    switch (this.state) {
      case M:
        return true;
      case E:
        return false;
        //return true; // TODO
      case S:
        return false;
      case I:
        return false;
      default:
        return false;
    }
  }
}

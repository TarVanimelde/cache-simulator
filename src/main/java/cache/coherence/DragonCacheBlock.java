package cache.coherence;

import bus.Bus;
import bus.BusAction;
import bus.BusJob;
import bus.StateEvaluator;
import cache.Address;
import cache.Cache;

import java.util.logging.Level;
import java.util.logging.Logger;

public class DragonCacheBlock extends CacheBlock {

  public DragonCacheBlock(Cache cache, Address address) {
    super(cache, address);
  }

  @Override
  public void readBlock(Address address) {
    switch (state) {
      case I:
        /*
          The state after reading in the block will depend on whether any other caches hold a copy
          of the data at the time the job is isFinished:
         */
        StateEvaluator checkOnlyCacheHolding = (Cache local, Address a) ->
            Bus.remoteCacheContains(local, a) ? CoherenceState.SC : CoherenceState.E;
        BusJob busRd = new BusJob(cache, address, BusAction.BUSRD, checkOnlyCacheHolding, true);
        cache.setJob(busRd);
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
            "Invalid state in Dragon Read" + state);

    }
  }

  @Override
  public void writeBlock(Address address) {
    /*
      The state after writing to the block will depend on whether any other caches hold a copy of
      the data at the time the job is finished:
    */
    StateEvaluator checkOnlyCacheHolding = (Cache local, Address a) ->
        Bus.remoteCacheContains(local, a) ? CoherenceState.SM : CoherenceState.M;
    switch (state) {
      case I:
        /*
         * Run a BusRd to obtain the block. Per the Dragon specification, only if another cache
         * holds the block (shared line asserted) is a BusUpd subsequently issued; otherwise it
         * transitions directly to M with no BusUpd.
         */
        BusJob read = BusJob.createDragonWriteMiss(cache, address, checkOnlyCacheHolding);
        cache.setJob(read);
        break;
      case E:
        state = CoherenceState.M;
        Bus.getStatistics().addWriteLatency(0);
        break;
      case M:
        // State is not changed by a local update.
        Bus.getStatistics().addWriteLatency(0);
        break;
      case SC:
        BusJob busRdSc = new BusJob(cache, address, BusAction.BUSUPD, checkOnlyCacheHolding, false);
        cache.setJob(busRdSc);
        break;
      case SM:
        BusJob busRdM = new BusJob(cache, address, BusAction.BUSUPD, checkOnlyCacheHolding, false);
        cache.setJob(busRdM);
        break;
      default:
        Logger.getLogger(getClass().getName()).log(Level.SEVERE,
            "Invalid state in Dragon Write: " + state);
    }
  }

  @Override
  public void remoteRead(Address address) {
    switch (state) {
      case I:
        // State is not changed by a remote read.
        break;
      case E:
        state = CoherenceState.SC;
        break;
      case M:
        // In Dragon, the M owner supplies the block cache-to-cache and becomes the Sm owner;
        // main memory is NOT updated until the Sm/M block is evicted.
        state = CoherenceState.SM;
        break;
      case SC:
        // State is not changed by a remote read.
        break;
      case SM:
        // Sm owner supplies the block cache-to-cache and remains Sm; memory is not updated.
        break;
      default:
        Logger.getLogger(getClass().getName()).log(Level.SEVERE,
            "Invalid state in Dragon Remote Read: " + state);
    }
  }

  @Override
  public void remoteWrite(Address address) {
    // This doesn't happen in the dragon protocol. Do you know what happens instead? Updates.
    Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Saw BUSRDX in the Dragon protocol?");
  }

  @Override
  public void remoteUpdate(Address address) {
    switch (state) {
      case I:
        // State is not changed by a remote update.
        break;
      case SC:
        // Saw an update, now this block is updated! State is not changed by a remote update.
        break;
      case SM:
        // Saw an update, now this block is updated!
        state = CoherenceState.SC;
        break;
      default:
        Logger.getLogger(getClass().getName()).log(Level.SEVERE,
            "Invalid state in Dragon Remote Update: " + state);
    }
  }

  @Override
  public boolean writeBackOnEvict() {
    switch (state) {
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

  @Override
  public boolean flushesToMemoryOnSnoop() {
    return false;
  }

  @Override
  public boolean isShared() {
    return state == CoherenceState.SC || state == CoherenceState.SM;
  }
}

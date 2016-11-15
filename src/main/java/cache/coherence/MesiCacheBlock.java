package cache.coherence;

import bus.Bus;
import bus.BusAction;
import bus.BusJob;
import bus.StateEvaluator;
import cache.Address;
import cache.Cache;

import java.util.logging.Level;
import java.util.logging.Logger;

public class MesiCacheBlock extends CacheBlock {
  public MesiCacheBlock(Cache cache, Address address) {
    super(cache, address);
  }

  @Override
  public void readBlock(Address address) {
    switch (state) {
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
          of the data at the time the job is isFinished:
         */
        StateEvaluator finalState = (Cache local, Address a) -> {
          if (Bus.remoteCacheContains(local, a)) {
            return CoherenceState.S;
          } else {
            return CoherenceState.E;
          }
          //Bus.remoteCacheContains(local, a) ? CoherenceState.S : CoherenceState.E;
        };

        BusJob job = new BusJob(cache, address, BusAction.BUSRD, finalState);
        cache.setJob(job);
        break;
      default:
        Logger.getLogger(getClass().getName()).log(Level.SEVERE,
            "Invalid state in MESI Read" + state);
        break;
    }
  }

  @Override
  public void writeBlock(Address address) {
    BusJob busRdX = new BusJob(cache, address, BusAction.BUSRDX, (local, a) -> CoherenceState.M);
    switch (state) {
      case M:
        // State is not changed by a local write.
        Bus.getStatistics().addWriteLatency(0);
        break;
      case E:
        state = CoherenceState.M;
        Bus.getStatistics().addWriteLatency(0);
        break;
      case S:
        // Since this is already in the shared state, no other processor can be in the M state.
        // Just upgrade this block to M and invalidate the other caches' copies.
        //Bus.broadcastRemoteWrite(cache, address);
        //BusJob busRdX = new BusJob(cache, address, BusAction.BUSRDX,
         //   (Cache local, Address a) -> CoherenceState.M);
        //state = CoherenceState.M;
        cache.setJob(busRdX);
        break;
      case I:
        cache.setJob(busRdX);
        break;
      default:
        Logger.getLogger(getClass().getName()).log(Level.SEVERE,
            "Invalid state in MESI Write" + state);
        break;
    }
  }

  @Override
  public void remoteRead(Address address) {
    switch (state) {
      case M:
        Bus.flush(cache, address, CoherenceState.S);
        break;
      case E:
        state = CoherenceState.S;
        break;
      case S:
        // State is not changed by a remote read.
        break;
      case I:
        // State is not changed by a remote read.
        break;
      default:
        Logger.getLogger(getClass().getName()).log(Level.SEVERE,
            "Invalid state in MESI Remote Read" + state);
        break;
    }
  }

  @Override
  public void remoteWrite(Address address) {
    switch (state) {
      case M:
        Bus.flush(cache, address, CoherenceState.I);
        Bus.getStatistics().incrementBusInvalidations();
        break;
      case E:
        state = CoherenceState.I;
        Bus.getStatistics().incrementBusInvalidations();
        break;
      case S:
        state = CoherenceState.I;
        Bus.getStatistics().incrementBusInvalidations();
        break;
      case I:
        // State is not changed by a remote write.
        break;
      default:
        Logger.getLogger(getClass().getName()).log(Level.SEVERE,
            "Invalid state in MESI Remote Write" + state);
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
    switch (state) {
      case M:
        return true;
      case E:
        return false;
      case S:
        return false;
      case I:
        return false;
      default:
        return false;
    }
  }

  @Override
  public boolean isShared() {
    return state == CoherenceState.S;
  }
}

package cache.coherence;

import bus.Bus;
import bus.BusAction;
import bus.BusJob;
import cache.Address;
import cache.Cache;

import java.util.logging.Level;
import java.util.logging.Logger;

public class MsiCacheBlock extends CacheBlock {
  public MsiCacheBlock(Cache cache, Address address) {
    super(cache, address);
  }

  @Override
  public void readBlock(Address address) {
    switch (state) {
      case M:
        // State is not changed by a local read.
        break;
      case S:
        // State is not changed by a local read.
        break;
      case I:
        BusJob job = new BusJob(cache, address, BusAction.BUSRD, (local, a) -> CoherenceState.S,
            true);
        cache.setJob(job);
        break;
      default:
        Logger.getLogger(MsiCacheBlock.class.getName())
            .log(Level.SEVERE, "Invalid state in procRead");
        break;
    }
  }

  @Override
  public void writeBlock(Address address) {
    switch (state) {
      case M:
        // State is not changed by a local write.
        Bus.getStatistics().addWriteLatency(0);
        break;
      case S:
        // Basic MSI (without BusUpgr) issues BusRdX to invalidate remote copies and upgrade S -> M.
        BusJob upgradeRdX = new BusJob(cache, address, BusAction.BUSRDX,
            (local, a) -> CoherenceState.M, false);
        cache.setJob(upgradeRdX);
        break;
      case I:
        BusJob missRdX = new BusJob(cache, address, BusAction.BUSRDX,
            (local, a) -> CoherenceState.M, true);
        cache.setJob(missRdX);
        break;
      default:
        Logger.getLogger(MsiCacheBlock.class.getName())
            .log(Level.SEVERE, "Invalid state in procWrite");
        break;
    }
  }

  @Override
  public void remoteRead(Address address) {
    switch (state) {
      case M:
        state = CoherenceState.S;
        Bus.getStatistics().incrementFlushes();
        break;
      case S:
        // State is not changed by a remote read.
        break;
      case I:
        // State is not changed by a remote read.
        break;
      default:
        Logger.getLogger(MsiCacheBlock.class.getName())
            .log(Level.SEVERE, "Invalid state in remoteRead");
    }
  }

  @Override
  public void remoteWrite(Address address) {
    switch (state) {
      case M:
        state = CoherenceState.I;
        Bus.getStatistics().incrementFlushes();
        Bus.getStatistics().incrementBusInvalidations();
        break;
      case S:
        state = CoherenceState.I;
        Bus.getStatistics().incrementBusInvalidations();
        break;
      case I:
        // State is not changed by a remote procWrite.
        break;
      default:
        Logger.getLogger(MsiCacheBlock.class.getName())
            .log(Level.SEVERE, "Invalid state in remoteWrite");
    }
  }

  @Override
  public void remoteUpdate(Address address) {
    // Do nothing: MSI is an invalidation-based protocol.
    Logger.getLogger(MsiCacheBlock.class.getName())
        .log(Level.SEVERE, "Remote update in the MSI protocol?");
  }

  @Override
  public boolean writeBackOnEvict() {
    switch (state) {
      case M:
        return true;
      case S:
        return false;
      case I:
        return false;
      default:
        return false;
    }
  }

  @Override
  public boolean canSupplyData() {
    // In MSI (and MUSI), only dirty (M) blocks intervene on the bus; clean (S) misses come from memory.
    return state == CoherenceState.M;
  }

  @Override
  public boolean isShared() {
    return state == CoherenceState.S;
  }
}

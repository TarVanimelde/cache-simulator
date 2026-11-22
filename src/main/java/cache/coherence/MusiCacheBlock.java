package cache.coherence;

import bus.Bus;
import bus.BusAction;
import bus.BusJob;
import cache.Address;
import cache.Cache;

import java.util.logging.Level;
import java.util.logging.Logger;

public class MusiCacheBlock extends MsiCacheBlock {
  public MusiCacheBlock(Cache cache, Address address) {
    super(cache, address);
  }

  @Override
  public void writeBlock(Address address) {
    switch (state) {
      case M:
        // State is not changed by a local write.
        Bus.getStatistics().addWriteLatency(0);
        break;
      case S:
        // Since this is already in the shared state, issue a BusUpgr on the shared bus to
        // serialize the upgrade to M and invalidate remote copies without transferring a block.
        BusJob busUpgr = new BusJob(cache, address, BusAction.BUSUPGR, (l, a) -> CoherenceState.M,
            false);
        cache.setJob(busUpgr);
        break;
      case I:
        BusJob busRdX = new BusJob(cache, address, BusAction.BUSRDX, (l, a) -> CoherenceState.M,
            true);
        cache.setJob(busRdX);
        break;
      default:
        Logger.getLogger(MsiCacheBlock.class.getName())
            .log(Level.SEVERE, "Invalid state in MUSI write.");
        break;
    }
  }
}

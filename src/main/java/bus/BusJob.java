package bus;

import cache.Cache;
import cache.CacheProperties;
import cache.CycleCountdown;
import cache.Address;
import cache.coherence.CoherenceState;

import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BusJob {
  /**
   * An empty job that does nothing. Used as a placeholder.
   */
  public static final BusJob EMPTY_JOB = new BusJob(null, null, BusAction.NONE,
      (Cache local, Address a) -> CoherenceState.I);

  /*
  The cache being modified (e.g., reading in a block) by the job.
   */
  private final Cache origin; // The cache that spawned the job.
  private final Address target; // The memory address being acted upon.
  private final BusAction action; // The type of bus operation being performed.
  /**
   * Given the cache and address, returns the state of the address in that cache. Produces the state
   * the origin cache's block will be in upon completion given the origin and the address. This
   * allows external conditions (e.g., whether remote caches hold the same block) to be evaluated
   * upon completion of the job.
   */
  private final BiFunction<Cache, Address, CoherenceState> finalStateEval;

  private boolean started = false; // Indicates whether the job has been started.
  /**
   * The number of cycles of bus use remaining until the job is finished.
   */
  private CycleCountdown cyclesRemaining;

  private int bytesTransferred = 0;

  public BusJob(Cache origin, Address target, BusAction action, BiFunction<Cache, Address,
      CoherenceState> finalState) {
    this.origin = origin;
    this.target = target;
    this.action = action;
    this.finalStateEval = finalState;
    this.cyclesRemaining = new CycleCountdown(0);
  }

  public void start() {
    if (!started) {
      switch (action) {
        case BUSRD:
          // fall through, do the same as in BUSRDX:
        case BUSRDX:
          if (Bus.remoteCacheContains(origin, target)) {
            // At least one cache contains the block, get it from a cache:
            int numRemote = Bus.numRemoteCachesContaining(origin, target);
            bytesTransferred = CacheProperties.getBlockSize() * numRemote;
            cyclesRemaining = new CycleCountdown(CacheProperties.getWordsPerBlock() * numRemote);
          } else {
            // The block is not cached: read, it from main memory:
            bytesTransferred = CacheProperties.getBlockSize();
            cyclesRemaining = new CycleCountdown(Bus.READ_FROM_MEM);
          }
          break;
        case BUSUPD:
          bytesTransferred = CacheProperties.WORD_SIZE;
          cyclesRemaining = new CycleCountdown(Bus.READ_WORD);
          break;
        case EVICTLRU:
          bytesTransferred = CacheProperties.getBlockSize();
          cyclesRemaining = new CycleCountdown(Bus.WRITE_TO_MEM);
        case NONE:
          // Do nothing.
          break;
        default:
          // Do nothing.
      }
      started = true;
    }
  }

  public void tick() {
    if (!isFinished()) {
      cyclesRemaining.tick();
      if (isFinished() && action != BusAction.NONE) {
        onFinish();
      }
    }
  }

  /**
   *
   * @return The number of bytes transferred from the origin over the bus by this job.
   */
  public int getBytesTransferred() {
    return bytesTransferred;
  }

  public boolean isFinished() {
    return cyclesRemaining.finished();
  }

  private void onFinish() {
    if (isFinished() && action != BusAction.NONE) {
      switch (action) {
        case EVICTLRU:
          origin.finishEvictionFor(target);
          break;
        case BUSRDX:
          origin.setState(target, finalStateEval.apply(origin, target));
          Bus.broadcastRemoteWrite(origin, target);
          Bus.getStatistics().incrementBusWrites();
          break;
        case BUSRD:
          origin.setState(target, finalStateEval.apply(origin, target));
          Bus.broadcastRemoteRead(origin, target);
          Bus.getStatistics().incrementBusReads();
          break;
        case BUSUPD:
          origin.setState(target, finalStateEval.apply(origin, target));
          Bus.broadcastRemoteUpdate(origin, target);
          Bus.getStatistics().incrementBusUpdates();
          break;
        default:
          // All cases should be enumerated above, log the action:
          Logger.getLogger(getClass().getName())
              .log(Level.WARNING, "Did not handle bus job final case for" + action.toString());
          break;
      }
      origin.setJobFinished();
    }
  }
}

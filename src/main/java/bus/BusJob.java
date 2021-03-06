package bus;

import cache.Cache;
import cache.CacheProperties;
import cache.CycleCountdown;
import cache.Address;
import cache.coherence.CoherenceState;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BusJob {
  /**
   * An empty job that does nothing. Used as a placeholder.
   */
  public static final BusJob EMPTY_JOB = new BusJob(null, null, BusAction.NONE,
      (local, a) -> CoherenceState.I);

  /*
  The cache being modified (e.g., reading in a block) by the job.
   */
  private final Cache origin; // The cache that spawned the job.
  private final Address target; // The memory address being acted upon.
  private final BusAction action; // The type of bus operation being performed.

  private int startedAtCycle = -1; // The cycle of the bus in which the job was started.

  private final StateEvaluator finalStateEval; // Determines the final state of the calling block.

  private boolean started = false; // Indicates whether the job has been started.
  /**
   * The number of cycles of bus use remaining until the job is isFinished.
   */
  private CycleCountdown cycleCountdown;

  private final BusJob successor;

  private int bytesTransferred = 0;

  public BusJob(Cache origin, Address target, BusAction action, StateEvaluator finalState) {
    this.origin = origin;
    this.target = target;
    this.action = action;
    this.finalStateEval = finalState;
    this.cycleCountdown = new CycleCountdown(0);
    this.successor = null;
    if (action == BusAction.NONE) {
      started = true;
    }
    startedAtCycle = Bus.getCycle();
  }

  public BusJob(Cache origin, Address target, BusAction action, StateEvaluator finalState,
                BusJob successorJob) {
    this.origin = origin;
    this.target = target;
    this.action = action;
    this.finalStateEval = finalState;
    this.cycleCountdown = new CycleCountdown(0);
    this.successor = successorJob;
    this.startedAtCycle = Bus.getCycle();
  }

  public void start() {
    if (!started) {
      switch (action) {
        case BUSRD:
          bytesTransferred = CacheProperties.getBlockSize();
          if (Bus.remoteCacheContains(origin, target)) {
            // At least one cache contains the block, get it from a cache:
            cycleCountdown = new CycleCountdown(CacheProperties.getWordsPerBlock());
          } else {
            // The block is not cached: read, it from main memory:
            cycleCountdown = new CycleCountdown(Bus.READ_FROM_MEM_CYCLES);
          }
          break;
        case BUSRDX:
          bytesTransferred = CacheProperties.getBlockSize();
          if (Bus.remoteCacheContains(origin, target)) {
            // At least one cache contains the block, get it from a cache:
            cycleCountdown = new CycleCountdown(CacheProperties.getWordsPerBlock());// * numRemote);
          } else {
            // The block is not cached: read, it from main memory:
            cycleCountdown = new CycleCountdown(Bus.READ_FROM_MEM_CYCLES);
          }
          break;
        case BUSUPD:
          bytesTransferred = CacheProperties.WORD_SIZE;
          cycleCountdown = new CycleCountdown(Bus.READ_WORD_CYCLES);
          break;
        case EVICTLRU:
          bytesTransferred = CacheProperties.getBlockSize();
          cycleCountdown = new CycleCountdown(Bus.WRITE_TO_MEM_CYCLES);
          break;
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
    if (!started) {
      start();
    }

    if (!isFinished()) {
      cycleCountdown.tick();
      if (isFinished()) {
        onFinish();
      }
    }
  }

  private boolean hasSuccessor() {
    return getSuccessor().isPresent();
  }

  public Optional<BusJob> getSuccessor() {
    return Optional.ofNullable(successor);
  }

  public boolean isFinished() {
    return started && cycleCountdown.isFinished();
  }

  public boolean successorFinished() {
    return isFinished() && (!hasSuccessor() || successor.isFinished());
  }

  private void onFinish() {
    if (isFinished()) {
      Bus.getStatistics().addBytesWritten(bytesTransferred);
      switch (action) {
        case EVICTLRU:
          origin.finishEvictionFor(target);
          break;
        case BUSRDX:
          Bus.broadcastRemoteWrite(origin, target);
          origin.setState(target, finalStateEval.apply(origin, target));
          Bus.getStatistics().incrementBusWrites();
          Bus.getStatistics().addWriteLatency(Bus.getCycle() - startedAtCycle + 1);
          break;
        case BUSRD:
          Bus.broadcastRemoteRead(origin, target);
          origin.setState(target, finalStateEval.apply(origin, target));
          Bus.getStatistics().incrementBusReads();
          break;
        case BUSUPD:
          Bus.broadcastRemoteUpdate(origin, target);
          origin.setState(target, finalStateEval.apply(origin, target));
          Bus.getStatistics().incrementBusUpdates();
          Bus.getStatistics().addWriteLatency(Bus.getCycle() - startedAtCycle + 1);
          break;
        default:
          // All cases should be enumerated above, log the action:
          Logger.getLogger(getClass().getName())
              .log(Level.WARNING, "Did not handle bus job final case for" + action.toString());
          break;
      }
    }
  }

  public String toString() {
    return "From cache: " + origin.getId() + ", Job: " + action;
  }
}

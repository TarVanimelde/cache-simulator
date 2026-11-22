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
  private BusAction action; // The type of bus operation being performed.

  private final long startedAtCycle; // The cycle of the bus in which the job was created.

  private StateEvaluator finalStateEval; // Determines the final state of the calling block.

  private boolean started = false; // Indicates whether the job has been started.
  /**
   * The number of cycles of bus use remaining until the job is finished.
   */
  private CycleCountdown cycleCountdown;

  private BusJob successor;

  private int bytesTransferred = 0;
  private boolean recordMissAccess = false;
  private boolean dragonWriteMissRead = false;

  public BusJob(Cache origin, Address target, BusAction action, StateEvaluator finalState) {
    this(origin, target, action, finalState, null, false);
  }

  public BusJob(Cache origin, Address target, BusAction action, StateEvaluator finalState,
                boolean recordMissAccess) {
    this(origin, target, action, finalState, null, recordMissAccess);
  }

  public BusJob(Cache origin, Address target, BusAction action, StateEvaluator finalState,
                BusJob successorJob) {
    this(origin, target, action, finalState, successorJob, false);
  }

  private BusJob(Cache origin, Address target, BusAction action, StateEvaluator finalState,
                 BusJob successorJob, boolean recordMissAccess) {
    this.origin = origin;
    this.target = target;
    this.action = action;
    this.finalStateEval = finalState;
    this.cycleCountdown = new CycleCountdown(0);
    this.successor = successorJob;
    this.recordMissAccess = recordMissAccess;
    if (action == BusAction.NONE) {
      this.started = true;
    }
    this.startedAtCycle = origin != null ? origin.getEffectiveWriteStartCycle() : Bus.getCycle();
  }

  /**
   * Creates the initial BusRd job for a Dragon write miss (from state I). Only executes the
   * follow-up BusUpd if at least one remote cache holds the block when BusRd acquires the bus.
   */
  public static BusJob createDragonWriteMiss(Cache origin, Address target,
                                             StateEvaluator updStateEval) {
    BusJob successor = new BusJob(origin, target, BusAction.BUSUPD, updStateEval, true);
    BusJob read = new BusJob(origin, target, BusAction.BUSRD,
        (local, a) -> CoherenceState.SC, successor, false);
    read.dragonWriteMissRead = true;
    return read;
  }

  public void start() {
    if (!started) {
      // Culler §5.4.2 race: if a queued BUSUPGR was invalidated by an earlier bus write before
      // acquiring the bus, downgrade it to a full BUSRDX so it fetches the updated block.
      if (action == BusAction.BUSUPGR && !origin.contains(target)) {
        action = BusAction.BUSRDX;
      }

      switch (action) {
        case BUSRD:
          bytesTransferred = CacheProperties.getBlockSize();
          boolean remoteHasCopy = Bus.remoteCacheContains(origin, target);
          if (Bus.remoteCacheFlushesToMemory(origin, target)) {
            cycleCountdown = new CycleCountdown(Bus.WRITE_TO_MEM_CYCLES);
          } else if (Bus.remoteCacheCanSupply(origin, target)) {
            cycleCountdown = new CycleCountdown(CacheProperties.getWordsPerBlock());
          } else {
            cycleCountdown = new CycleCountdown(Bus.READ_FROM_MEM_CYCLES);
          }
          Bus.broadcastRemoteRead(origin, target);
          if (dragonWriteMissRead) {
            if (!remoteHasCopy) {
              successor = null;
              finalStateEval = (local, a) -> CoherenceState.M;
              recordMissAccess = true;
            } else {
              finalStateEval = (local, a) -> CoherenceState.SC;
            }
          }
          break;
        case BUSRDX:
          bytesTransferred = CacheProperties.getBlockSize();
          if (Bus.remoteCacheFlushesToMemory(origin, target)) {
            cycleCountdown = new CycleCountdown(Bus.WRITE_TO_MEM_CYCLES);
          } else if (Bus.remoteCacheCanSupply(origin, target)) {
            cycleCountdown = new CycleCountdown(CacheProperties.getWordsPerBlock());
          } else {
            cycleCountdown = new CycleCountdown(Bus.READ_FROM_MEM_CYCLES);
          }
          Bus.broadcastRemoteWrite(origin, target);
          break;
        case BUSUPGR:
          bytesTransferred = 0;
          cycleCountdown = new CycleCountdown(Bus.READ_WORD_CYCLES);
          Bus.broadcastRemoteWrite(origin, target);
          break;
        case BUSUPD:
          bytesTransferred = CacheProperties.WORD_SIZE;
          cycleCountdown = new CycleCountdown(Bus.READ_WORD_CYCLES);
          Bus.broadcastRemoteUpdate(origin, target);
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
    return isFinished() && (!hasSuccessor() || successor.successorFinished());
  }

  private void onFinish() {
    if (isFinished()) {
      Bus.getStatistics().addBytesWritten(bytesTransferred);
      switch (action) {
        case EVICTLRU:
          origin.finishEvictionFor(target);
          break;
        case BUSRDX:
          origin.setState(target, finalStateEval.apply(origin, target));
          if (recordMissAccess) {
            origin.recordMissAccessFor(target);
          }
          Bus.getStatistics().incrementBusWrites();
          Bus.getStatistics().addWriteLatency(Bus.getCycle() - startedAtCycle + 1);
          break;
        case BUSUPGR:
          origin.setState(target, finalStateEval.apply(origin, target));
          if (recordMissAccess) {
            origin.recordMissAccessFor(target);
          }
          Bus.getStatistics().incrementBusUpgrades();
          Bus.getStatistics().addWriteLatency(Bus.getCycle() - startedAtCycle + 1);
          break;
        case BUSRD:
          origin.setState(target, finalStateEval.apply(origin, target));
          if (recordMissAccess) {
            origin.recordMissAccessFor(target);
          }
          Bus.getStatistics().incrementBusReads();
          if (dragonWriteMissRead && !hasSuccessor()) {
            Bus.getStatistics().addWriteLatency(Bus.getCycle() - startedAtCycle + 1);
          }
          break;
        case BUSUPD:
          origin.setState(target, finalStateEval.apply(origin, target));
          if (recordMissAccess) {
            origin.recordMissAccessFor(target);
          }
          Bus.getStatistics().incrementBusUpdates();
          Bus.getStatistics().addWriteLatency(Bus.getCycle() - startedAtCycle + 1);
          break;
        default:
          // All cases should be enumerated above, log the action:
          Logger.getLogger(getClass().getName())
              .log(Level.WARNING, "Did not handle bus job final case for " + action.toString());
          break;
      }
    }
  }

  public String toString() {
    return "From cache: " + origin.getId() + ", Job: " + action;
  }
}

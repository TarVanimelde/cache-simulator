import bus.Bus;
import cache.Cache;
import cache.CycleCountdown;
import cache.Instruction;
import cache.Address;
import statistics.ProcessorStatistics;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class Processor {
  private Cache l1; // The processor's cache.

  private CycleCountdown nonmemCountdown = new CycleCountdown(0); // A timer to wait out the cycles of an OTHER inst.
  private final Deque<Instruction> instructions; // The sequence of instructions to carry out.

  private final int id; // The unique processor ID.
  private static int idCounter = 0; // A processor ID counter.
  private final ProcessorStatistics stats;

  public Processor(List<Instruction> instructions) {
    this.id = idCounter; // The unique processor ID.
    stats = new ProcessorStatistics(id);
    idCounter++;

    l1 = new Cache(stats);
    Bus.add(l1);
    this.instructions = new ArrayDeque<>(instructions);
  }

  public static void reset() {
    idCounter = 0;
    Cache.reset();
  }


  public void tick() {
    if (!nonmemCountdown.isFinished()) {
      nonmemCountdown.tick();
      stats.incrementCycles();
      return;
    }

    if (!l1.isBlocking()) {
      while (hasInstructionsRemaining()) {
        Instruction head = instructions.peek();
        if (head.getType() == cache.InstructionType.INVALID
            || (head.getType() == cache.InstructionType.OTHER && head.getValue() <= 0)) {
          instructions.pop();
        } else {
          break;
        }
      }
    }

    if (hasInstructionsRemaining() && !l1.isBlocking()) {
      Instruction instr = instructions.peek();
      switch (instr.getType()) {
        case OTHER:
          nonmemCountdown = new CycleCountdown(instr.getValue() - 1);
          instructions.pop();
          break;
        case LOAD: {
          Address address = new Address((int) instr.getValue());
          if (!l1.contains(address) && !l1.hasBlockAvailableFor(address)) {
            // Need to evict a block before loading the address, do that now:
            l1.allocateBlockFor(address);
          }
          if (!l1.isBlocking()) {
            if (l1.contains(address)) {
              stats.incrementReadHit();
            } else {
              stats.incrementReadMiss();
            }
            l1.procRead(address);
            instructions.pop();
          }
          break;
        }
        case STORE: {
          Address address = new Address((int) instr.getValue());
          l1.markPendingWriteStartCycle(Bus.getCycle());
          if (!l1.contains(address) && !l1.hasBlockAvailableFor(address)) {
            // Need to evict a block before storing the address, do that now:
            l1.allocateBlockFor(address);
          }
          if (!l1.isBlocking()) {
            if (l1.contains(address)) {
              stats.incrementWriteHit();
            } else {
              stats.incrementWriteMiss();
            }
            l1.procWrite(address);
            l1.clearPendingWriteStartCycle();
            instructions.pop();
          }
          break;
        }
        default:
          instructions.pop();
          break;
      }
      stats.incrementCycles();
    } else if (l1.isBlocking()) {
      stats.incrementCycles();
    } else {
      // Done processing all instructions in the cache: do nothing.
    }

  }

  public boolean isFinished() {
    return !hasInstructionsRemaining()
        && !l1.isBlocking()
        && nonmemCountdown.isFinished();
  }

  private boolean hasInstructionsRemaining() {
    return !instructions.isEmpty();
  }

  public ProcessorStatistics getStatistics() {
    return stats;
  }

  @Override
  public String toString() {
    return String.join(", ",
        "Processor ID: " + id,
        "Current cycle: " + stats.getNumCycles(),
        "Finished? " + isFinished());
  }
}

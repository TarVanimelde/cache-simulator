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

  private CycleCountdown nonmemInstr = new CycleCountdown(0); // A timer to wait out the cycles of an OTHER inst.
  private final Deque<Instruction> instructions; // The sequence of instructions to carry out.

  private ProcessorStatistics stats = new ProcessorStatistics();

  public Processor(List<Instruction> instructions) {
    l1 = new Cache();
    Bus.add(l1);
    stats.attachCacheStats(l1.getStatistics());
    this.instructions = new ArrayDeque<>(instructions);
  }


  public void tick() {
    // Only do a cycle if there is work left:
    if (!nonmemInstr.isFinished()) {
      nonmemInstr.tick();
      stats.incrementCycles();
    } else if (hasInstructionsRemaining() && !l1.isBlocking()) {
      Instruction instr = instructions.peek();
      Address address = new Address((int)instr.getValue());
      switch (instr.getType()) {
        case OTHER:
          nonmemInstr = new CycleCountdown(instr.getValue());
          instructions.pop();
          break;
        case LOAD:
          if (!l1.contains(address) && !l1.hasBlockAvailableFor(address)) {
            // Need to startEvictionFor a block before loading the address, do that now:
            l1.allocateBlockFor(address);
          } else {
            if (l1.contains(address)) {
              stats.incrementReadHit();
            } else {
              stats.incrementReadMiss();
            }
            l1.procRead(address);
            instructions.pop();
          }

          break;
        case STORE:
          if (!l1.contains(address) && !l1.hasBlockAvailableFor(address)) {
            // Need to startEvictionFor a block before storing the address, do that now:
            l1.allocateBlockFor(address);
          } else {
            if (l1.contains(address)) {
              stats.incrementWriteHit();
            } else {
              stats.incrementWriteMiss();
            }
            l1.procWrite(address);
            instructions.pop();
          }
          break;
        default:
          // Do nothing.
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
        && nonmemInstr.isFinished();
  }

  public boolean hasInstructionsRemaining() {
    return !instructions.isEmpty();
  }

  public ProcessorStatistics getStatistics() {
    return stats;
  }
}

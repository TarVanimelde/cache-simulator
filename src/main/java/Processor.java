import bus.Bus;
import cache.Cache;
import cache.CycleCountdown;
import cache.Instruction;
import cache.Address;
import statistics.ProcessorStatistics;

import java.util.List;

public class Processor {

  private Cache l1; // The processor's cache.

  private final List<Instruction> instructions; // The sequence of instructions to carry out.
  private int instrCounter = -1; // The next instruction to process.

  private CycleCountdown nonmemInstr = new CycleCountdown(0); // A timer to wait out the cycles of an OTHER inst.

  private ProcessorStatistics stats = new ProcessorStatistics();

  public Processor(List<Instruction> instructions) {
    l1 = new Cache();
    Bus.add(l1);
    stats.attachCacheStats(l1.getStatistics());

    this.instructions = instructions;
  }


  public void tick() {
    // Only do a cycle if there is work left:
    if (!nonmemInstr.finished()) {
      nonmemInstr.tick();
      stats.incrementCycles();
    } else if (hasInstructionsRemaining() && !l1.isBlocking()) {
      instrCounter++;
      Instruction instr = instructions.get(instrCounter);
      Address address = new Address((int)instr.getValue());
      switch (instr.getType()) {
        case OTHER:
          nonmemInstr = new CycleCountdown(instr.getValue());
          break;
        case LOAD:
          if (l1.contains(address)) {
            stats.incrementReadHit();
          } else {
            stats.incrementReadMiss();
          }

          if (!l1.contains(address) && !l1.hasBlockAvailableFor(address)) {
            // Need to evict a block before loading the address, do that now:
            l1.procEvictBlockFor(address);
          } else {
            l1.procRead(address);
          }

          break;
        case STORE:
          if (l1.contains(address)) {
            stats.incrementWriteHit();
          } else {
            stats.incrementWriteMiss();
          }

          if (!l1.contains(address) && !l1.hasBlockAvailableFor(address)) {
            // Need to evict a block before storing the address, do that now:
            l1.procEvictBlockFor(address);
          } else {
            l1.procWrite(address);
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

  public boolean finished() {
    return !hasInstructionsRemaining()
        && !l1.isBlocking()
        && nonmemInstr.finished();
  }

  public boolean hasInstructionsRemaining() {
    return instrCounter + 1 < instructions.size();
  }

  public ProcessorStatistics getStatistics() {
    return stats;
  }
}
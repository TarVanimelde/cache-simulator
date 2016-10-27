import bus.Bus;
import cache.Instruction;
import statistics.ProcessorStatistics;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MultiProcessor {
  private List<Processor> procs = new ArrayList<>();

  public MultiProcessor() { }

  /**
   * Adds an instruction sequence to a processor in the multiprocessor.
   */
  public void addProcessorFor(List<Instruction> instructions) {
    Processor p = new Processor(instructions);
    procs.add(p);
  }

  public void simulateProgram() {
    boolean allProcsFinished = procs.stream().allMatch(Processor::finished);
    while (!allProcsFinished) {
      procs.forEach(Processor::tick);
      Bus.tick();
      allProcsFinished = procs.stream().allMatch(Processor::finished);
    }
  }

  public List<ProcessorStatistics> getStatistics() {
    return procs.stream()
        .map(Processor::getStatistics)
        .collect(Collectors.toCollection(ArrayList::new));
  }
}

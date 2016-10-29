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
    boolean allProcsFinished = procs.stream().allMatch(Processor::isFinished);
    while (!allProcsFinished) {
      procs.forEach(Processor::tick);
      Bus.tick();
      allProcsFinished = procs.stream().allMatch(Processor::isFinished);
    }
  }

  public List<ProcessorStatistics> getStatistics() {
    return procs.stream()
        .map(Processor::getStatistics)
        .collect(Collectors.toCollection(ArrayList::new));
  }
}

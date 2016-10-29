import bus.Bus;
import statistics.BusStatistics;
import cache.*;
import cache.coherence.CoherencePolicy;
import javafx.util.Pair;
import statistics.ProcessorStatistics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;


import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Program {
  // TODO: when finally done, flush it all to memory?

  public static void main(String[] args) throws IOException {
    //TODO: command line parseTrace
    // TODO: CLI
    Logger.getLogger(Program.class.getName()).log(Level.INFO, "Setting the cache configuration.");
    CoherencePolicy policy = CoherencePolicy.MSI;
    int cacheSize = 4096;
    int blockSize = 16;
    int associativity = 2;
    CacheProperties.setCoherencePolicy(policy);
    try {
      CacheProperties.setCacheSize(cacheSize);
      CacheProperties.setBlockSize(blockSize);
      CacheProperties.setAssociativity(associativity);
    } catch (Exception invalidCacheSetting) {
      invalidCacheSetting.printStackTrace();
    }

    //String base = "/Users/TarVanimelde1/Desktop/CS4223/A2/blackscholes/blackscholes_";
    String base = "/Users/TarVanimelde1/Desktop/CS4223/A2/bodytrack/bodytrack_";
    //String base = "/Users/TarVanimelde1/Desktop/CS4223/A2/fluidanimate/fluidanimate_";
    List<List<Instruction>> instructions = new ArrayList<>();
    for (int i = 0; i <= 3; i++) {
      String p = base + i + ".data";
      Logger.getLogger(Program.class.getName()).log(Level.INFO, "Parsing instructions from " + p);
      instructions.add(parseTrace(Paths.get(p)));
    }

    Logger.getLogger(Program.class.getName()).log(Level.INFO, "Running the cache simulation.");
    testInstructions(instructions);
  }

  public static void testInstructions(List<List<Instruction>> instructions) {
    testPolicy(instructions, CoherencePolicy.MSI);
    testPolicy(instructions, CoherencePolicy.MESI);
    testPolicy(instructions, CoherencePolicy.DRAGON);
  }

  public static void testPolicy(List<List<Instruction>> instructions, CoherencePolicy p) {
    System.out.println("Running with coherence policy " + p);
    CacheProperties.setCoherencePolicy(p);
    MultiProcessor multiProcessor = new MultiProcessor();
    instructions.forEach(multiProcessor::addProcessorFor);
    multiProcessor.simulateProgram();

    System.out.println("Bus statistics:");
    BusStatistics busStats = Bus.getStatistics();
    System.out.println(busStats.toString());
    List<ProcessorStatistics> procStats = multiProcessor.getStatistics();
    ProcessorStatistics summary = procStats.stream()
        .reduce(new ProcessorStatistics(),ProcessorStatistics::combine);

    System.out.println("System statistics:");
    System.out.println(summary.toString());

    // TODO: reset
    Bus.reset();
  }

  public static List<Instruction> parseTrace(Path trace) throws IOException {
    Pattern pattern = Pattern.compile("(\\d)\\s+0x([\\d a-f]+)\\s*");
    return Files.lines(trace)
        .map(instr -> {
          Matcher m = pattern.matcher(instr);
          if (m.matches()) {
            return new Pair<>(m.group(1), m.group(2));
          } else {
            Logger.getLogger(Program.class.getName())
                .log(Level.WARNING, "Could not parseTrace instruction: {}", instr);
            return new Pair<>("3", "-1");
          }
        })
        .map(instr -> {
          switch (instr.getKey()) {
            case "0":
              return new Instruction(InstructionType.LOAD, Long.parseLong(instr.getValue(), 16));
            case "1":
              return new Instruction(InstructionType.STORE, Long.parseLong(instr.getValue(), 16));
            case "2":
              return new Instruction(InstructionType.OTHER, Long.parseLong(instr.getValue(), 16));
            default:
              Logger.getLogger(Program.class.getName())
                  .log(Level.WARNING, "Invalid instruction: {}, cache.Address: {}",
                      new Object[] {instr.getKey(), instr.getValue()});
              return new Instruction(InstructionType.INVALID, Long.parseLong(instr.getValue(), 16));

          }
        })
        .collect(Collectors.toCollection(ArrayList::new));
  }
}

import bus.Bus;
import com.lexicalscope.jewel.cli.CliFactory;
import statistics.BusStatistics;
import cache.*;
import cache.coherence.CoherencePolicy;
import javafx.util.Pair;
import statistics.ProcessorStatistics;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.ArrayList;


import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Program {
  public static void main(String[] args) throws IOException {
    args = new String[] {"-f", "/Users/TarVanimelde1/Desktop/CS4223/A2/blackscholes"};
    CLIModel model = CliFactory.parseArguments(CLIModel.class, args);

    // Need at least one trace file to run the program:
    if (!model.isFiles()) {
      Logger.getLogger(Program.class.getName()).log(Level.SEVERE, "No input file traces, exiting.");
      System.exit(0);
    }

    // Get all the trace files matching the pattern(s) given:
    List<Path> traces = getMatchingFiles(model.getFiles());

    // If there are no matching trace files, exit:
    if (traces.isEmpty()) {
      Logger.getLogger(Program.class.getName())
          .log(Level.SEVERE, "No input matching traces found, exiting.");
      System.exit(0);
    }

    // Set the properties of the cache:
    Logger.getLogger(Program.class.getName()).log(Level.INFO, "Setting the cache configuration.");

    CacheProperties.setCoherencePolicy(model.getPolicy());
    try {
      CacheProperties.setCacheSize(model.getCacheSize());
      CacheProperties.setBlockSize(model.getBlockSize());
      CacheProperties.setAssociativity(model.getAssociativity());
    } catch (Exception invalidCacheSetting) {
      Logger.getLogger(Program.class.getName())
          .log(Level.SEVERE, "Invalid cache configuration, exiting.");
      invalidCacheSetting.printStackTrace();
      System.exit(0);
    }

    // Parse the trace files' instructions:
    List<List<Instruction>> instructions = new ArrayList<>(traces.size());
    for (Path trace : traces) {
      Logger.getLogger(Program.class.getName())
          .log(Level.INFO, "Parsing instructions from " + trace);
      instructions.add(parseTrace(trace));
    }

    Logger.getLogger(Program.class.getName()).log(Level.INFO, "Running the simulation.");

    // Run the simulation:
    testInstructions(instructions);
  }

  private static List<Path> getMatchingFiles(List<String> tracePatterns) {
    List<Path> traces = new ArrayList<>();

    for (String traceCandidate : tracePatterns) {
      Path p = Paths.get(traceCandidate);
      if (Files.isRegularFile(p)) {
        traces.add(p);
      } else if (Files.isDirectory(p)) {
        try {
          traces.addAll(Files.list(p)
              .filter(sub -> sub.toString().endsWith(".data"))
              .collect(Collectors.toCollection(ArrayList::new)));
        } catch (IOException e) {
          e.printStackTrace();
        }
      } else {
        Logger.getLogger(Program.class.getName())
            .log(Level.SEVERE, "Invalid trace input file or folder: " + traceCandidate);
      }
    }

    return traces;
  }

  private static void testInstructions(List<List<Instruction>> instructions) {
    testPolicy(instructions, CoherencePolicy.MSI);
    testPolicy(instructions, CoherencePolicy.MESI);
    testPolicy(instructions, CoherencePolicy.DRAGON);
  }

  private static void testPolicy(List<List<Instruction>> instructions, CoherencePolicy p) {
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

    Bus.reset();
  }

  private static List<Instruction> parseTrace(Path trace) throws IOException {
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

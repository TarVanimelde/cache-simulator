import bus.Bus;
import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CliFactory;
import com.lexicalscope.jewel.cli.HelpRequestedException;
import statistics.BusStatistics;
import cache.*;
import cache.coherence.CoherencePolicy;
import statistics.ProcessorStatistics;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Program {
  private static final Pattern TRACE_PATTERN =
      Pattern.compile("^\\s*(\\d+)\\s+0[xX]([0-9a-fA-F]+)\\s*$");

  public static void main(String[] args) throws IOException {
    CLIModel model = null;
    try {
      model = CliFactory.parseArguments(CLIModel.class, args);
    } catch (HelpRequestedException e) {
      System.out.println(e.getMessage());
      System.exit(0);
    } catch (ArgumentValidationException e) {
      System.out.println(e.getMessage());
      System.exit(1);
    }

    if (model.isSilent()) {
      Logger log = LogManager.getLogManager().getLogger("");
      for (Handler h : log.getHandlers()) {
        h.setLevel(Level.SEVERE);
      }
    }

    // Need at least one trace file to run the program:
    if (!model.isFiles()) {
      Logger.getLogger(Program.class.getName()).log(Level.SEVERE, "No input file traces, exiting.");
      System.exit(1);
    }

    // Get all the trace files matching the pattern(s) given:
    List<Path> traces = getMatchingFiles(model.getFiles());

    // If there are no matching trace files, exit:
    if (traces.isEmpty()) {
      Logger.getLogger(Program.class.getName())
          .log(Level.SEVERE, "No input matching traces found, exiting.");
      System.exit(1);
    }

    // Set the properties of the cache:
    Logger.getLogger(Program.class.getName()).log(Level.INFO, "Setting the cache configuration.");

    try {
      CacheProperties.configure(
          model.getCacheSize(),
          model.getBlockSize(),
          model.getAssociativity());
    } catch (Exception invalidCacheSetting) {
      Logger.getLogger(Program.class.getName())
          .log(Level.SEVERE, "Invalid cache configuration, exiting.");
      invalidCacheSetting.printStackTrace();
      System.exit(1);
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
    final boolean silent = model.isSilent();
    final String tracePath = String.join(":", model.getFiles());
    model.getPolicies().forEach(p -> simulate(instructions, p, silent, tracePath));
  }

  static List<Path> getMatchingFiles(List<String> tracePatterns) {
    List<Path> traces = new ArrayList<>();

    for (String traceCandidate : tracePatterns) {
      Path p = Paths.get(traceCandidate);
      if (Files.isRegularFile(p)) {
        traces.add(p);
      } else if (Files.isDirectory(p)) {
        try (Stream<Path> stream = Files.list(p)) {
          traces.addAll(stream
              .filter(Files::isRegularFile)
              .filter(sub -> sub.toString().endsWith(".data"))
              .sorted()
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

  private static void simulate(List<List<Instruction>> instructions,
                               CoherencePolicy p,
                               boolean silent,
                               String tracePath) {
    Bus.reset();
    Processor.reset();
    Logger.getLogger(Program.class.getName()).log(Level.INFO, "Running with coherence policy " + p);
    CacheProperties.setCoherencePolicy(p);
    MultiProcessor multiProcessor = new MultiProcessor();
    instructions.forEach(multiProcessor::addProcessorFor);
    multiProcessor.simulateProgram();

    BusStatistics busStats = Bus.getStatistics();
    List<ProcessorStatistics> procStats = multiProcessor.getStatistics();
    ProcessorStatistics summary = procStats.stream()
        .reduce(new ProcessorStatistics(), ProcessorStatistics::combine);

    if (!silent) {
      System.out.println(busStats.toString());
      System.out.println(summary.toString());
    } else {
      String output = String.join(" ",
          p.toString(),
          tracePath,
          Integer.toString(CacheProperties.getCacheSize()),
          Integer.toString(CacheProperties.getBlockSize()),
          Integer.toString(CacheProperties.getAssociativity()),
          Double.toString(summary.getDataMissRate()),
          Long.toString(busStats.getBytesWritten()),
          Integer.toString(busStats.getInvalidations()),
          Integer.toString(busStats.getBusUpdates()),
          Integer.toString(summary.getPrivateAccesses()),
          Integer.toString(summary.getSharedAccesses()),
          Long.toString(busStats.getAverageWriteLatency()),
          Long.toString(summary.getNumCycles())
      );
      System.out.println(output);
    }
  }

  static List<Instruction> parseTrace(Path trace) throws IOException {
    try (Stream<String> lines = Files.lines(trace)) {
      return lines
          .filter(line -> !line.trim().isEmpty())
          .map(instr -> {
            Matcher m = TRACE_PATTERN.matcher(instr);
            if (!m.matches()) {
              Logger.getLogger(Program.class.getName())
                  .log(Level.WARNING, "Could not parse trace instruction: {0}", instr);
              return new Instruction(InstructionType.INVALID, -1L);
            }
            long value = Long.parseLong(m.group(2), 16);
            switch (m.group(1)) {
              case "0":
                return new Instruction(InstructionType.LOAD, value);
              case "1":
                return new Instruction(InstructionType.STORE, value);
              case "2":
                return new Instruction(InstructionType.OTHER, value);
              default:
                Logger.getLogger(Program.class.getName())
                    .log(Level.WARNING, "Invalid instruction: {0}, cache.Address: {1}",
                        new Object[] {m.group(1), m.group(2)});
                return new Instruction(InstructionType.INVALID, value);
            }
          })
          .filter(instr -> instr.getType() != InstructionType.INVALID)
          .collect(Collectors.toCollection(ArrayList::new));
    }
  }
}

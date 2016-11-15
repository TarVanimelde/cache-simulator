import cache.coherence.CoherencePolicy;
import com.lexicalscope.jewel.cli.Option;

import java.util.List;

public interface CLIModel {
  @Option(
      shortName="f",
      longName="files",
      description="The traces or directory of traces to simulate."
  )
  public List<String> getFiles();

  public boolean isFiles();

  @Option(
      defaultValue="1024",
      longName = "cacheSize",
      shortName = "cs",
      description = "The size, in bytes, of the entire cache."
  )
  public int getCacheSize();

  @Option(
      defaultValue="16",
      longName = "blockSize",
      shortName = "bs",
      description = "The size, in bytes, of one cache block (cache line)."
  )
  public int getBlockSize();

  @Option(
      defaultValue="1",
      longName = "associativity",
      shortName = "a",
      description = "The set associativity of the cache."
  )
  public int getAssociativity();

  @Option(
      defaultValue="MSI",
      longName = "policy",
      shortName = "p",
      description = "The coherence strategies to sequentially simulate the traces with."
  )
  public List<CoherencePolicy> getPolicies();

  @Option(
      helpRequest = true,
      description = "Display help and exit.",
      shortName = "h")
  public boolean isHelp();


  @Option(
      longName = "silent",
      description = "Only prints severe logs and simulation results to console.",
      shortName = "s")
  public boolean isSilent();
}
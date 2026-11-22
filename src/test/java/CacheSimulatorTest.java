import bus.Bus;
import cache.*;
import cache.coherence.CoherencePolicy;
import com.lexicalscope.jewel.cli.CliFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import statistics.BusStatistics;
import statistics.ProcessorStatistics;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CacheSimulatorTest {

  @BeforeEach
  void resetSimulator() throws Exception {
    Bus.reset();
    Processor.reset();
    CacheProperties.configure(1024, 16, 1);
    CacheProperties.setCoherencePolicy(CoherencePolicy.MSI);
  }

  @Test
  void testEvictionCleanSkipsWriteBackAndDirtyWritesBack() throws Exception {
    CacheProperties.configure(16, 16, 1);
    CacheProperties.setCoherencePolicy(CoherencePolicy.MSI);

    // Clean eviction: LOAD 0x0 (-> S), then LOAD 0x10 (evicts clean S block without memory write-back)
    MultiProcessor mpClean = new MultiProcessor();
    mpClean.addProcessorFor(Arrays.asList(
        new Instruction(InstructionType.LOAD, 0x0),
        new Instruction(InstructionType.LOAD, 0x10)
    ));
    mpClean.simulateProgram();
    assertEquals(32L, Bus.getStatistics().getBytesWritten());
    assertEquals(200L, mpClean.getStatistics().get(0).getNumCycles());

    // Dirty eviction: STORE 0x0 (-> M), then STORE 0x10 (must write back 0x0 before fetching 0x10)
    Bus.reset();
    Processor.reset();
    MultiProcessor mpDirty = new MultiProcessor();
    mpDirty.addProcessorFor(Arrays.asList(
        new Instruction(InstructionType.STORE, 0x0),
        new Instruction(InstructionType.STORE, 0x10)
    ));
    mpDirty.simulateProgram();
    assertEquals(48L, Bus.getStatistics().getBytesWritten());
    assertEquals(300L, mpDirty.getStatistics().get(0).getNumCycles());
    // Write 1 = 100 cycles; Write 2 = 100 (evict) + 100 (BusRdX) = 200 cycles -> average = 150
    assertEquals(150L, Bus.getStatistics().getAverageWriteLatency());
  }

  @Test
  void testTraceParserUppercaseHexBlankLinesAndMalformedInput(@TempDir Path tempDir)
      throws Exception {
    Path trace = tempDir.resolve("core0.data");
    Files.write(trace, Arrays.asList(
        "0 0xFFF1A237",
        "  1 0Xabcdef10  ",
        "",
        "0 0x12 34",
        "2 0x5"
    ), StandardCharsets.UTF_8);

    List<Instruction> parsed = Program.parseTrace(trace);
    assertEquals(3, parsed.size());
    assertEquals(InstructionType.LOAD, parsed.get(0).getType());
    assertEquals(0xFFF1A237L, parsed.get(0).getValue());
    assertEquals(InstructionType.STORE, parsed.get(1).getType());
    assertEquals(0xABCDEF10L, parsed.get(1).getValue());
    assertEquals(InstructionType.OTHER, parsed.get(2).getType());
    assertEquals(5L, parsed.get(2).getValue());
  }

  @Test
  void testProcessorHandlesInvalidAndOtherExactCycles() {
    MultiProcessor mp = new MultiProcessor();
    mp.addProcessorFor(Arrays.asList(
        new Instruction(InstructionType.INVALID, -1),
        new Instruction(InstructionType.OTHER, 0),
        new Instruction(InstructionType.OTHER, 1),
        new Instruction(InstructionType.OTHER, 5)
    ));
    mp.simulateProgram();
    assertEquals(6L, mp.getStatistics().get(0).getNumCycles());
    assertEquals(0.0, mp.getStatistics().get(0).getDataMissRate());
    assertEquals(0L, Bus.getStatistics().getAverageWriteLatency());
  }

  @Test
  void testAddressEqualityIncludesIndexAndUnsignedHighBit() throws Exception {
    CacheProperties.configure(1024, 16, 1);
    Address set0 = new Address(0x00); // tag=0, index=0
    Address set1 = new Address(0x10); // tag=0, index=1
    Address set1Offset4 = new Address(0x14); // tag=0, index=1, offset=4
    assertNotEquals(set0, set1);
    assertEquals(set1, set1Offset4);
    assertEquals(set1.hashCode(), set1Offset4.hashCode());

    Address highBit = new Address((int) 0xFFF1A237L);
    assertTrue(highBit.getTag() > 0);
    assertTrue(highBit.getIndex() >= 0);
    assertEquals(7, highBit.getOffset());
  }

  @Test
  void testCachePropertiesValidationAndAtomicity() throws Exception {
    // Valid small cache that previously failed due to setter order against default blockSize=16
    CacheProperties.configure(8, 8, 1);
    assertEquals(8, CacheProperties.getCacheSize());
    assertEquals(8, CacheProperties.getBlockSize());
    assertEquals(1, CacheProperties.getNumSets());

    // Invalid configurations must throw and leave previous valid settings intact
    assertThrows(Exception.class, () -> CacheProperties.setCacheSize(0));
    assertThrows(Exception.class, () -> CacheProperties.setCacheSize(-64));
    assertThrows(Exception.class, () -> CacheProperties.configure(96, 16, 1));
    assertThrows(Exception.class, () -> CacheProperties.configure(48, 12, 1));

    assertEquals(8, CacheProperties.getCacheSize());
    assertEquals(8, CacheProperties.getBlockSize());
  }

  @Test
  void testReadOnlyStatisticsDoNotDivideByZero() {
    MultiProcessor mp = new MultiProcessor();
    mp.addProcessorFor(Collections.singletonList(new Instruction(InstructionType.LOAD, 0x100)));
    mp.simulateProgram();
    assertEquals(0L, Bus.getStatistics().getAverageWriteLatency());
    assertDoesNotThrow(() -> Bus.getStatistics().toString());
  }

  @Test
  void testMsiVsMusiVsMesiSequentialReadThenWrite() {
    List<Instruction> readThenWrite = Arrays.asList(
        new Instruction(InstructionType.LOAD, 0x100),
        new Instruction(InstructionType.STORE, 0x100)
    );

    // MSI: LOAD (100 cycles -> S), STORE (BusRdX = 100 cycles, 16B -> M)
    CacheProperties.setCoherencePolicy(CoherencePolicy.MSI);
    MultiProcessor msi = new MultiProcessor();
    msi.addProcessorFor(readThenWrite);
    msi.simulateProgram();
    assertEquals(200L, msi.getStatistics().get(0).getNumCycles());
    assertEquals(32L, Bus.getStatistics().getBytesWritten());

    // MUSI: LOAD (100 cycles -> S), STORE (BusUpgr = 1 cycle, 0B -> M)
    Bus.reset();
    Processor.reset();
    CacheProperties.setCoherencePolicy(CoherencePolicy.MUSI);
    MultiProcessor musi = new MultiProcessor();
    musi.addProcessorFor(readThenWrite);
    musi.simulateProgram();
    assertEquals(101L, musi.getStatistics().get(0).getNumCycles());
    assertEquals(16L, Bus.getStatistics().getBytesWritten());
    assertEquals(1, Bus.getStatistics().getBusUpgrades());

    // MESI: LOAD (100 cycles -> E), STORE (silent E -> M = 1 processor cycle, 0 bus transactions)
    Bus.reset();
    Processor.reset();
    CacheProperties.setCoherencePolicy(CoherencePolicy.MESI);
    MultiProcessor mesi = new MultiProcessor();
    mesi.addProcessorFor(readThenWrite);
    mesi.simulateProgram();
    assertEquals(101L, mesi.getStatistics().get(0).getNumCycles());
    assertEquals(16L, Bus.getStatistics().getBytesWritten());
    assertEquals(0, Bus.getStatistics().getBusUpgrades());
  }

  @Test
  void testCleanCacheToCacheTransferInMesiVsMsi() {
    // P0 loads 0x100 at cycle 0 (finishes cycle 100); P1 waits 105 cycles then loads 0x100
    List<Instruction> p0 = Collections.singletonList(new Instruction(InstructionType.LOAD, 0x100));
    List<Instruction> p1 = Arrays.asList(
        new Instruction(InstructionType.OTHER, 105),
        new Instruction(InstructionType.LOAD, 0x100)
    );

    // MSI: clean S does not supply cache-to-cache -> P1 reads from memory (100 cycles -> 205)
    CacheProperties.setCoherencePolicy(CoherencePolicy.MSI);
    MultiProcessor msi = new MultiProcessor();
    msi.addProcessorFor(p0);
    msi.addProcessorFor(p1);
    msi.simulateProgram();
    assertEquals(205L, msi.getStatistics().get(1).getNumCycles());

    // MESI: E supplies cache-to-cache in 4 words = 4 cycles -> P1 finishes at 109
    Bus.reset();
    Processor.reset();
    CacheProperties.setCoherencePolicy(CoherencePolicy.MESI);
    MultiProcessor mesi = new MultiProcessor();
    mesi.addProcessorFor(p0);
    mesi.addProcessorFor(p1);
    mesi.simulateProgram();
    assertEquals(109L, mesi.getStatistics().get(1).getNumCycles());
  }

  @Test
  void testConcurrentBusUpgrRaceDowngradesLoserToBusRdX() {
    // Both P0 and P1 load 0x100 into S, synchronize via OTHER, then both STORE 0x100 on same cycle!
    // P0 loads at 0..99 (100 cycles), waits 104 cycles -> cycle 204
    // P1 loads at 0 (waits 100 for P0 + 4 C2C in MESI = 104 cycles), waits 100 cycles -> cycle 204
    CacheProperties.setCoherencePolicy(CoherencePolicy.MESI);
    MultiProcessor mp = new MultiProcessor();
    mp.addProcessorFor(Arrays.asList(
        new Instruction(InstructionType.LOAD, 0x100),
        new Instruction(InstructionType.OTHER, 104),
        new Instruction(InstructionType.STORE, 0x100)
    ));
    mp.addProcessorFor(Arrays.asList(
        new Instruction(InstructionType.LOAD, 0x100),
        new Instruction(InstructionType.OTHER, 100),
        new Instruction(InstructionType.STORE, 0x100)
    ));
    mp.simulateProgram();

    BusStatistics busStats = Bus.getStatistics();
    // P0 wins with BUSUPGR (1 cycle, 0B, invalidates P1).
    // P1's queued BUSUPGR downgrades to BUSRDX, flushing P0's M block (100 cycles, 16B, 1 flush).
    assertEquals(1, busStats.getBusUpgrades());
    assertEquals(1, busStats.getBusWrites());
    assertEquals(1, busStats.getFlushes());
    assertEquals(2, busStats.getInvalidations());

    ProcessorStatistics summary = mp.getStatistics().stream()
        .reduce(new ProcessorStatistics(), ProcessorStatistics::combine);
    assertEquals(summary.getNumAccesses(),
        summary.getPrivateAccesses() + summary.getSharedAccesses());
  }

  @Test
  void testDragonSpecificationColdWriteMissAndDirtyCacheToCacheSharing() throws Exception {
    CacheProperties.configure(16, 16, 1);
    CacheProperties.setCoherencePolicy(CoherencePolicy.DRAGON);

    // 1. Cold write miss (~S): transitions directly I -> M with NO BusUpd
    MultiProcessor single = new MultiProcessor();
    single.addProcessorFor(Collections.singletonList(new Instruction(InstructionType.STORE, 0x100)));
    single.simulateProgram();
    assertEquals(1, Bus.getStatistics().getBusReads());
    assertEquals(0, Bus.getStatistics().getBusUpdates());
    assertEquals(16L, Bus.getStatistics().getBytesWritten());
    assertEquals(100L, single.getStatistics().get(0).getNumCycles());

    // 2. P0 writes 0x0 (-> M at cycle 100); P1 waits 105 cycles then reads 0x0:
    // Dragon supplies cache-to-cache (4 cycles, M -> Sm) WITHOUT a 100-cycle memory flush!
    // Then P0 evicts Sm by loading 0x10, which DOES write back to memory (100 cycles, +16B).
    Bus.reset();
    Processor.reset();
    MultiProcessor sharedDirty = new MultiProcessor();
    sharedDirty.addProcessorFor(Arrays.asList(
        new Instruction(InstructionType.STORE, 0x0),
        new Instruction(InstructionType.OTHER, 20),
        new Instruction(InstructionType.LOAD, 0x10)
    ));
    sharedDirty.addProcessorFor(Arrays.asList(
        new Instruction(InstructionType.OTHER, 105),
        new Instruction(InstructionType.LOAD, 0x0)
    ));
    sharedDirty.simulateProgram();

    assertEquals(0, Bus.getStatistics().getFlushes());
    // P1 finishes at 105 + 4 = 109 (no 100-cycle flush penalty!)
    assertEquals(109L, sharedDirty.getStatistics().get(1).getNumCycles());
    // Bytes: P0 STORE 0x0 (16B) + P1 C2C LOAD 0x0 (16B) + P0 Sm EVICTLRU (16B) + P0 LOAD 0x10 (16B) = 64B
    assertEquals(64L, Bus.getStatistics().getBytesWritten());

    ProcessorStatistics summary = sharedDirty.getStatistics().stream()
        .reduce(new ProcessorStatistics(), ProcessorStatistics::combine);
    assertEquals(summary.getNumAccesses(),
        summary.getPrivateAccesses() + summary.getSharedAccesses());
  }

  @Test
  void testCliShortFlagsAndSortedDirectoryListing(@TempDir Path tempDir) throws Exception {
    Path f2 = tempDir.resolve("trace_2.data");
    Path f0 = tempDir.resolve("trace_0.data");
    Path f1 = tempDir.resolve("trace_1.data");
    Files.write(f2, Collections.singletonList("0 0x0"), StandardCharsets.UTF_8);
    Files.write(f0, Collections.singletonList("0 0x0"), StandardCharsets.UTF_8);
    Files.write(f1, Collections.singletonList("0 0x0"), StandardCharsets.UTF_8);

    List<Path> matched = Program.getMatchingFiles(Collections.singletonList(tempDir.toString()));
    assertEquals(Arrays.asList(f0, f1, f2), matched);

    CLIModel cli = CliFactory.parseArguments(CLIModel.class,
        "-c", "2048", "-b", "32", "-a", "2", "-p", "MESI", "DRAGON", "-f", f0.toString(), "-s");
    assertEquals(2048, cli.getCacheSize());
    assertEquals(32, cli.getBlockSize());
    assertEquals(2, cli.getAssociativity());
    assertEquals(Arrays.asList(CoherencePolicy.MESI, CoherencePolicy.DRAGON), cli.getPolicies());
    assertTrue(cli.isSilent());
  }
}

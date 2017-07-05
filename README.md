# Coherent Cache Simulator

A configurable coherent cache simulator that accepts memory traces and cache settings. The MSI, MESI, and Dragon (Xerox) protocols are supported, and the simulator is designed to make it easy to extend the simulator to a new invalidation- or update-based protocol, by implementing the abstract class CacheBlock and adding the protocol to the list of protocols in CoherencePolicy. The command-line interface is implemented using the JewelCLI library.

* Apache 2.0 License

The command-line interface options are:

	[--associativity -a value] : The set associativity of the cache.
  
	[--blockSize -b value] : The size, in bytes, of one cache block (cache line).
  
	[--cacheSize -c value] : The size, in bytes, of the entire cache.
  
	[--files -f value...] : The traces or directory of traces to simulate. If a directory is provided, all the files ending with ".data" are assumed to be trace files.
  
	[--help -h] : Display help and exit.
  
	[--policy -p value...] : The coherence strategies to sequentially simulate the traces with.
  
	[--silent -s] : Only prints severe logs and simulation results to console.
  
The expected trace format is
  
	0 0xFFF1A237 : Load the block containing the address 0xFFF1A237 into the cache (read).
  
	1 0xFFF1A237 : Store the block containing the address 0xFFF1A237 (write).
  
	2 0xFFF1A237 : Block the processor for 0xFFF1A237 cycles, doing no work (to emulate non-memory operations).
  
The simulator treats each trace file as instructions for a processor, and adds a processor to handle each file's instructions (e.g., four trace files would be simulated as a four-core multiprocessor). Upon completion, various statistics from the simulation are outputted, including the number of cycles required to complete the simulation, the number of bytes passed through the bus, and average write latency (among others).

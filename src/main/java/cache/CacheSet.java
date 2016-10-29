package cache;

import bus.BusAction;
import bus.BusJob;
import cache.coherence.*;
import statistics.CacheStatistics;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CacheSet {
  private final Cache cache; // A reference to the cache that owns this set.
  private final Map<CacheBlock, Long> blocks = new HashMap<>(); // Mapping to recency of access.
  private final CacheStatistics stats; // The statistics aggregator to report read/write info to.

  public CacheSet(Cache cache, CacheStatistics stats) {
    this.cache = cache;
    for (int i = 1; i <= CacheProperties.getAssociativity(); i++) {
      this.blocks.put(CoherencePolicy.createStateMachine(cache), 0L);
    }
    this.stats = stats;
  }

  /**
   * Return whether the address is present in one of the set's blocks.
   */
  public boolean contains(Address address) {
    return blocks.keySet().stream().anyMatch(block -> block.contains(address));
  }

  /**
   * Optionally returns the block that contains the address, returning none if no block contains the
   * address.
   */
  private Optional<CacheBlock> getBlockContaining(Address address) {
    return blocks.keySet().stream()
        .filter(block -> block.contains(address))
        .findAny();
  }

  public boolean hasUnusedBlock() {
    return blocks.keySet().stream().anyMatch(CacheBlock::isInvalid);
  }

  private Optional<CacheBlock> getEmptyBlock() {
    return blocks.keySet().stream()
        .filter(CacheBlock::isInvalid)
        .findAny();
  }

  /*
   * Increments the time since last accessed for all blocks in the set.
   */
  private void incrementSetAge() {
    blocks.keySet().forEach(block -> blocks.merge(block, 1L, (oldValue, one) -> oldValue + one));
  }

  public void read(Address address) {
    incrementSetAge();
    stats.incrementReads();
    Optional<CacheBlock> bOpt = getBlockContaining(address);
    if (bOpt.isPresent()) {
      CacheBlock target = bOpt.get();
      target.readBlock(address);
      blocks.put(target, 0L); // Update the block to be the most recently used.
    } else {
      Optional<CacheBlock> empty = getEmptyBlock();
      if (empty.isPresent()) {
        empty.get().readBlock(address);
        blocks.put(empty.get(), 0L); // Update the block to be the most recently used.
        //if (!Bus.remoteCacheContains(cache, address)) {
        stats.incrementReadMisses();
        //}
      } else {
        Logger.getLogger(getClass().getName()).log(Level.SEVERE,
            "Could not find an empty block to read to.");
      }
    }
  }

  public void write(Address address) {
    incrementSetAge();
    stats.incrementWrites();
    Optional<CacheBlock> bOpt = getBlockContaining(address);
    if (bOpt.isPresent()) {
      CacheBlock target = bOpt.get();
      target.writeBlock(address);
      blocks.put(target, 0L); // Update the block to be the most recently used.
    } else {
      Optional<CacheBlock> empty = getEmptyBlock();
      if (empty.isPresent()) {
        empty.get().writeBlock(address);
        blocks.put(empty.get(), 0L); // Update the block to be the most recently used.
        //if (!Bus.remoteCacheContains(cache, address)) {
        stats.incrementWriteMisses();
        //}
      } else {
        Logger.getLogger(getClass().getName()).log(Level.SEVERE,
            "Could not find an empty block to write to.");
      }
    }
  }

  public void remoteWrite(Address address) {
    getBlockContaining(address).ifPresent(block -> block.remoteWrite(address));
  }

  public void remoteRead(Address address) {
    getBlockContaining(address).ifPresent(block -> block.remoteRead(address));
  }

  public void remoteUpdate(Address address) {
    getBlockContaining(address).ifPresent(block -> block.remoteUpdate(address));
  }

  public void setState(Address address, CoherenceState state) {
    Optional<CacheBlock> optBlock = getBlockContaining(address);
    if (optBlock.isPresent()) {
      optBlock.get().setState(state);
    } else {
      // Need to allocate a block. Assume one is available:
      Optional<CacheBlock> optEmpty = getEmptyBlock();
      if (optEmpty.isPresent()) {
        optEmpty.get().setAddress(address);
        optEmpty.get().setState(state);
      } else {
        Logger.getLogger(getClass().getName())
            .log(Level.SEVERE, "Attempted to set a new block, but none were available.");
      }
    }
  }

  public CacheBlock getLru() {
    return blocks.entrySet()
        .stream()
        .max(Map.Entry.comparingByValue())
        .get()
        .getKey();
  }

  /**
   * Invalidates the least recently used block and updates the lru information for all blocks.
   */
  public void finishLruEviction() {
    CacheBlock lru = getLru();
    lru.invalidate();
    incrementSetAge();
    blocks.put(lru, 0L);
  }

  public void startEvictionFor(Address address) {
    /*
     * True if there is no empty block and evicting the LRU block would require (by the coherence
     * protocol) the data to be flushed to main memory.
     */
    boolean evictionRequiresFlush = !hasUnusedBlock() && getLru().writeBackOnEvict();

    if (!evictionRequiresFlush) {
      BusJob job = new BusJob(cache, address, BusAction.EVICTLRU, (local, a) -> CoherenceState.I);
      cache.setJob(job);
    } else {
      finishLruEviction();
    }
  }
}

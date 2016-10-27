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
  private Map<CacheBlock, Integer> blocks; // A mapping of blocks in the set to how recently they've been used.
  private final CacheStatistics stats; // The statistics aggregator to report procRead/procWrite info to.

  public CacheSet(Cache cache, CacheStatistics stats) {
    this.cache = cache;
    this.blocks = new HashMap<>();
    for (int i = 0; i < CacheProperties.getAssociativity(); i++) {
      this.blocks.put(CoherencePolicy.createStateMachine(this.cache), 0);
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

  public boolean hasAvailableBlock() {
    return blocks.keySet().stream().anyMatch(CacheBlock::isInvalid);
  }

  private Optional<CacheBlock> getEmptyBlock() {
    return blocks.keySet().stream()
        .filter(CacheBlock::isInvalid)
        .findAny();
  }

  public CacheBlock getLru() {
    return blocks.entrySet()
        .stream()
        .max(Map.Entry.comparingByValue()).get().getKey();
  }

  public boolean evictionRequiresFlush() {
    if (!hasAvailableBlock()) {
      CacheBlock lru = getLru();
      return lru.writeBackOnEvict();
    }
    return false;
  }

  /**
   * Invalidates the least recently used block and updates the lru information for all blocks.
   */
  public void evictLru() {
    Optional<Map.Entry<CacheBlock, Integer>> lruOpt = blocks.entrySet()
        .stream()
        .max(Map.Entry.comparingByValue());
    if (lruOpt.isPresent()) {
      CacheBlock lru = lruOpt.get().getKey();
      lru.invalidate();
      incrementSetAge();
      blocks.put(lru, 0);
    } else {
      Logger.getLogger(getClass().getName())
          .log(Level.SEVERE, "Attempted to finishEvictionFor, but no blocks in the cache set.");
    }
  }

  /*
    Increments the time since last accessed for all blocks in the set.
   */
  private void incrementSetAge() {
    blocks.keySet().forEach(block -> blocks.merge(block, 1, (oldValue, one) -> oldValue + one));
  }

  public void read(Address address) {
    this.incrementSetAge();
    stats.incrementReads();
    if (this.contains(address)) {
      Optional<CacheBlock> bOpt = getBlockContaining(address);
      if (bOpt.isPresent()) {
        CacheBlock target = bOpt.get();
        target.readBlock(address);
        blocks.put(target, 0); // Update the block to be the most recently used.
      } else {
        // This shouldn't happen, since the contains method would otherwise return false.
        stats.incrementReadMisses();
        Logger.getLogger(getClass().getName()).log(Level.SEVERE,
            "Address contained but not present on read.");
      }
    } else {
      Optional<CacheBlock> target = getEmptyBlock();
      if (!target.isPresent()) {
        Logger.getLogger(getClass().getName()).log(Level.SEVERE,
            "Could not find an empty block to read to.");
      }
      target.get().readBlock(address);
      blocks.put(target.get(), 0); // Update the block to be the most recently used.
      stats.incrementReadMisses();
    }
  }

  public void write(Address address) {
    this.incrementSetAge();
    stats.incrementWrites();
    if (this.contains(address)) {
      Optional<CacheBlock> bOpt = getBlockContaining(address);
      if (bOpt.isPresent()) {
        CacheBlock target = bOpt.get();
        target.writeBlock(address);
        blocks.put(target, 0); // Update the block to be the most recently used.
      } else {
        // This shouldn't happen, since the contains method would otherwise return false/
        Logger.getLogger(getClass().getName()).log(Level.WARNING, "Address contained but not present on procWrite.");
      }
    } else {
      Optional<CacheBlock> target = getEmptyBlock();
      if (!target.isPresent()) {
        Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Could not find an empty block to procWrite to.");
      }
      target.get().writeBlock(address);
      blocks.put(target.get(), 0); // Update the block to be the most recently used.
      stats.incrementReadMisses();
    }
  }

  public void remoteReadExclusive(Address address) {
    Optional<CacheBlock> bOpt = getBlockContaining(address);
    if (bOpt.isPresent()) {
      bOpt.get().remoteReadExclusive(address);
    }
  }

  public void remoteRead(Address address) {
    Optional<CacheBlock> bOpt = getBlockContaining(address);
    if (bOpt.isPresent()) {
      bOpt.get().remoteRead(address);
    }
  }

  public void remoteUpdate(Address address) {
    Optional<CacheBlock> bOpt = getBlockContaining(address);
    if (bOpt.isPresent()) {
      bOpt.get().remoteUpdate(address);
    }
  }

  public void setState(Address address, CoherenceState state) {
    if (this.contains(address)) {
      Optional<CacheBlock> bOpt = getBlockContaining(address);
      if (bOpt.isPresent()) {
        bOpt.get().setState(state);
      } else {
        Logger.getLogger(getClass().getName())
            .log(Level.SEVERE, "Attempted to set the state of block that does not exist in the set:"
                + address.toString());
      }
    } else {
      // Need to allocate a block. Assume one is available:
      Optional<CacheBlock> e = getEmptyBlock();
      if (e.isPresent()) {
        e.get().setAddress(address);
        e.get().setState(state);
      } else {
        Logger.getLogger(getClass().getName())
            .log(Level.SEVERE, "Attempted to use a new block, but none were available.");
      }
    }
  }

  public void procEvict(Address address) {
    if (!evictionRequiresFlush()) {
      BusJob job = new BusJob(cache, address, BusAction.EVICT,
          (Cache local, Address a) -> CoherenceState.I);
      cache.putJob(job);
    } else {
      CacheBlock lru = this.getLru();
      lru.invalidate();
    }
  }


}

package cache.coherence;

import cache.Address;
import cache.Cache;
import cache.CacheProperties;

public enum CoherencePolicy {
  MSI,
  MESI,
  MESIF,
  DRAGON; // DRAGON is equivalent to MOESI?

  public static CacheBlock createStateMachine(Cache cache) {
    return createStateMachine(cache, new Address(-1));
  }

  public static CacheBlock createStateMachine(Cache cache, Address address) {
    switch (CacheProperties.getCoherencePolicy()) {
      case MSI:
        return new MsiCacheBlock(cache, address);
      case MESI:
        return new MesiCacheBlock(cache, address);
      case DRAGON:
        return new DragonCacheBlock(cache, address);
      default:
        return new MsiCacheBlock(cache, address);
    }
  }
}
package cache.coherence;

import cache.Address;
import cache.Cache;
import cache.CacheProperties;

public enum CoherencePolicy {
  MSI,
  MESI,
  MUSI,
  DRAGON;

  public static CacheBlock createBlock(Cache cache) {
    return createBlock(cache, new Address(-1));
  }

  public static CacheBlock createBlock(Cache cache, Address address) {
    switch (CacheProperties.getCoherencePolicy()) {
      case MSI:
        return new MsiCacheBlock(cache, address);
      case MUSI:
        return new MusiCacheBlock(cache, address);
      case MESI:
        return new MesiCacheBlock(cache, address);
      case DRAGON:
        return new DragonCacheBlock(cache, address);
      default:
        return new MsiCacheBlock(cache, address);
    }
  }
}

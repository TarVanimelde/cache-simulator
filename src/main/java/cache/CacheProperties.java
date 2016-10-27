package cache;

import cache.coherence.CoherencePolicy;

/**
 * A collection of related cache properties. It is important to set these properties before
 * generating any processors or caches, as otherwise the defaults (cache size 4KB, block size 16B,
 * direct-mapped) will be used.
 */
public class CacheProperties {
  /**
   * The macroscopic properties of the cache:
   */

  private static int blockSize = 16; // The number of bytes in a cache block.
  private static int cacheSize = 4096; // The number of bytes in a processor cache.
  private static int associativity = 1; // The number of cache blocks per cache set.
  private static CoherencePolicy policy = CoherencePolicy.MSI; // The cache's coherence policy.

  /**
   * An address is split ordered left to right for most significant to least significant bits is
   * split into three parts:
   * [ tag | index | offset]
   *
   * The number of bits in the index, i, is such that 2 to the power of i equals the number of
   * sets in the cache. The number of bits in the offsets, o, is such that 2 to the power of o
   * equals the number of bytes in a block. The number of bits in the tag, t, equals the number of
   * bits in a memory address minus (o + i). For this system, since a memory address is 32 bits,
   * this is equivalent to 32 - (o + i).
   *
   * These are used by the cache to sort memory addresses into the appropriate cache entries.
   */

  private static int tagSize = 20; // The number of the bits used for the tag of the address.
  private static int indexSize = 8; // The number of the bits used for the index of the address.
  private static int offsetSize = 4; // The number of the bits used for the offset of the address.

  public static final int WORD_SIZE = 4; // The number of bytes in a word of data.

  private CacheProperties() {}

  /**
   * Returns the number of words of data that can be held in a block.
   */
  public static int getWordsPerBlock() {
    return getBlockSize() / WORD_SIZE;
  }

  /**
   * Returns the number of blocks that can be contained in a cache.
   */
  public static int getNumBlocks() {
    return getCacheSize() / getBlockSize();
  }

  /**
   * Returns the number of sets that can be contained in a cache. For a direct-mapped cache
   * (associativity equals one), this equals the number of blocks that can be contained in a cache.
   */
  public static int getNumSets() {
    return getNumBlocks() / getAssociativity();
  }

  public static int getTagSize() {
    return tagSize;
  }
  public static int getIndexSize() {
    return indexSize;
  }

  public static int getOffsetSize() {
    return offsetSize;
  }

  public static int getBlockSize() {
    return blockSize;
  }

  public static int getCacheSize() {
    return cacheSize;
  }

  public static int getAssociativity() {
    return associativity;
  }

  public static CoherencePolicy getCoherencePolicy() {
    return policy;
  }

  public static void setCoherencePolicy(CoherencePolicy p) {
    policy = p;
  }

  /*
    Block size is assumed to be given in bytes and is set to max(WORD_SIZE, size). This is so that
    at least a word of data can be placed in the cache.
   */
  public static void setBlockSize(int size) throws Exception {
    blockSize = Math.max(WORD_SIZE, size);
    updateCacheAddressing();
  }

  /*
    Cache size is assumed to be given in bytes and is set to max(blockSize, size).
   */
  public static void setCacheSize(int size) throws Exception {
    if (cacheSize % 2 != 0) {
      throw new Exception("Cache size must be a power of two.");
    } else if (cacheSize % blockSize != 0) {
      throw new Exception("Cache size must be at least as large as the size of one cache block"
          + " and divisible by the size (in bytes) of a cache block.");
    } else if ((cacheSize / blockSize) % associativity != 0) {
      throw new Exception("Cache size must be at least as large as and divisible by the size of one"
          + " cache set, which is equal to the block size times the associativity of the cache.");
    }
    cacheSize = size;
    updateCacheAddressing();
  }

  /**
   * Associativity is set to the max(1, associativity).
   */
  public static void setAssociativity(int assoc) throws Exception {
    associativity = Math.max(1, assoc);
    updateCacheAddressing();
  }

  private static void updateCacheAddressing() throws Exception {
    if (cacheSize % 2 != 0
        || cacheSize % blockSize != 0
        || blockSize % WORD_SIZE != 0 // Need caches to be able to hold 32-bit data.
        || (cacheSize / blockSize) % associativity != 0) {
      String cacheConfig = String.format("Invalid cache configuration: Cache Size: %1$\n" +
          "Block Size: %2$\n" +
          "Associativity: %3$", cacheSize, blockSize, associativity);
      throw new Exception(cacheConfig);
    }

    int numBlocks = cacheSize / blockSize;
    indexSize = Integer.numberOfTrailingZeros(numBlocks / associativity);
    offsetSize = Integer.numberOfTrailingZeros(blockSize);
    tagSize = 32 - (offsetSize + indexSize);
  }
}

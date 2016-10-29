package bus;

import cache.Address;
import cache.Cache;
import cache.coherence.CoherenceState;

import java.util.function.BiFunction;

/**
 * Given the cache and address, returns the state of the address in that cache. Produces the state
 * the origin cache's block will be in upon completion given the origin and the address. This
 * allows external conditions (e.g., whether remote caches hold the same block) to be evaluated
 * upon completion of the job.
 */
public interface StateEvaluator extends BiFunction<Cache, Address, CoherenceState> {}


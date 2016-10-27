package cache.coherence;

public enum CoherenceState {
  // M(E)SI protocol states:
  M, E, S, I,
  // Extra states for use in the Dragon protocol:
  SC, SM
}

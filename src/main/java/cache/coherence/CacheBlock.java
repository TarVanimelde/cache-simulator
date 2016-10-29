package cache.coherence;

import cache.Address;
import cache.Cache;

public abstract class CacheBlock {
  protected Cache cache; // A reference to the cache that contains this block.
  protected CoherenceState state;
  protected Address address; // Contains the tag currently contained in the block.

  protected CacheBlock(Cache cache, Address address) {
    this.cache = cache;
    this.address = address;
    this.state = CoherenceState.I;
  }

  public abstract void readBlock(Address address);
  public abstract void writeBlock(Address address);

  /**
   * Signal that a BusRd operation has isFinished for the given address.
   */
  public abstract void remoteRead(Address address);

  /**
   * Signal that a BusRdX operation has isFinished for the given address.
   */
  public abstract void remoteWrite(Address address);

  public abstract void remoteUpdate(Address address);

  public boolean isInvalid() {
    return state == CoherenceState.I;
  }

  /**
   * Changes the state of the block to the specified state. Used for updating state after a bus job
   * is isFinished. Don't use this method except where it's already used. It performs no checks on the
   * state transition.
   */
  public void setState(CoherenceState state) {
    this.state = state;
  }
  /**
   * Returns whether the block is currently in a state that requires a write back to memory if the
   * block is evicted.
   */
  public abstract boolean writeBackOnEvict();

  public void invalidate() {
    setState(CoherenceState.I);
  }

  public void setAddress(Address address) {
    this.address = address;
  }

  public boolean contains(Address address) {
    return this.state != CoherenceState.I
        && address.getTag() == this.address.getTag();
  }
}

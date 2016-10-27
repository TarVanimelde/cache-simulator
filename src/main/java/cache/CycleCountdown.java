package cache;

import java.util.concurrent.atomic.AtomicLong;

public class CycleCountdown {
  private final AtomicLong cyclesRemaining;

  public CycleCountdown(long cycles) {
    cyclesRemaining = new AtomicLong(cycles);
  }

  /*
    Decrements and returns the number of cycles remaining.
   */
  public void tick() {
    if (cyclesRemaining.get() > 0) {
      cyclesRemaining.decrementAndGet();
    }
  }

  public boolean finished() {
    return cyclesRemaining.get() == 0;
  }
}

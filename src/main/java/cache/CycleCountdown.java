package cache;

import java.util.logging.Level;
import java.util.logging.Logger;

public class CycleCountdown {
  private long cyclesRemaining;

  public CycleCountdown(long cycles) {
    if (cycles < 0) {
      Logger.getLogger(getClass().getName())
          .log(Level.WARNING, "Cycle countdown less than 0 requested: " + cycles);
    }
    cyclesRemaining = cycles;
  }

  /*
   * Decrements and returns the number of cycles remaining.
   */
  public void tick() {
    if (cyclesRemaining > 0) {
      cyclesRemaining--;
    }
  }

  public long getCyclesRemaining() {
    return cyclesRemaining;
  }

  public boolean isFinished() {
    return cyclesRemaining <= 0L;
  }

  public String toString() {
    return "Cycles remaining: " + cyclesRemaining;
  }
}

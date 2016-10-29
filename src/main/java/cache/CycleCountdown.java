package cache;

public class CycleCountdown {
  private long cyclesRemaining;

  public CycleCountdown(long cycles) {
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

  public boolean isFinished() {
    return cyclesRemaining == 0L;
  }
}

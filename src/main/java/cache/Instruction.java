package cache;

public class Instruction {
  private final InstructionType type;
  private final long value;

  /*
    The instruction type and the value being used. If the type type is STORE or LOAD, then the value
    is the memory address. If the type type is "OTHER", then the value is the number of cycles
    required to process the instruction.
  */
  public Instruction(InstructionType type, long value) {
    this.type = type;
    this.value = value;
  }

  public InstructionType getType() {
        return this.type;
    }

  public long getValue() {
        return this.value;
    }

  @Override
  public String toString() {
    return String.join(", ", "Instruction type: " + type, "Value: " + value);
  }
}

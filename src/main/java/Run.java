import java.io.IOException;

/**
 * Will run the emulation
 */
public class Run {
    public static void main(String[] args) throws IOException {
        System.out.println("Welcom to Java gb");

        Memory memory = new Memory(new CPU(), "cpu_instrs.gb");
        memory.hexDumpRomContents();
    }
}
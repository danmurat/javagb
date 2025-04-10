import java.io.IOException;

/**
 * Will run the emulation
 */
public class Run {
    public static void main(String[] args) throws IOException {
        System.out.println("Welcom to Java gb");

        Memory memory = new Memory("cpu_instrs.gb");
        CPU cpu = new CPU(memory);
        memory.setCPU(cpu);
        memory.hexDumpRomContents();
    }
}
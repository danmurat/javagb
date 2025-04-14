import java.io.IOException;

/**
 * Will run the emulation
 */
public class Run {
    public static void main(String[] args) throws IOException {
        System.out.println("Welcom to Java gb");

        // -- SET UP
        Memory memory = new Memory("cpu_instrs.gb");
        CPU cpu = new CPU(memory);
        memory.setCPU(cpu);
        memory.hexDumpRomContents();


        run(cpu);
    }

    /**
     * Sets up and runs gameboy game
     */
    public static void run(CPU cpu) {

        /*
        Von nueman, fetch decode execute cycle.

        PC starts at 0x100. Reads memory to get instruction, executes, and PC subsequently updates
        as this runs. This is continuously

        TODO: we will need to set up debugging helpers here to see how correct our implementation is
        remember we kind of need to implement one of the I/O ports for debugging so we see outputs
         */

        while (true) {
            cpu.executeInstruction(); // handles the above comment
        }
    }

}
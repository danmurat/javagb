/**
 * Represents the game boy CPU.
 * <br>
 * Will hold ALL the instructions (bit manipulations), and registers from the unit.
 */
public class CPU {
    private Memory memory;

    // WHAT DO WE NEED?


    // Registers (cpu memory)

    /*
     These can be either accessed as 16 bit (AF) or 8 bit individually (just A)

     My first thoughts are just to keep these as 16bit (AF...). When accessing these
     through instructions, we should be able to access the correct register regardless (with correct address)

     I'm wondering however, should these be arrays or just numbers? Registers are literally memory inside the cpu,
     so they will have their own addresses right?

     https://electronics.stackexchange.com/questions/60176/microprocessors-microcontrollers-do-registers-have-addresses

     The link above describes some differences between registers. But typically in ARM (or those with SP, PC) they
     have NO addresses. So they just hold values.
     */
    private int AF; // A = high, F = lo (Lower 8 bits (F) hold Flag information)
    private int BC;
    private int DE;
    private int HL;

    private int SP;
    private int PC;

    // flags TODO: consider removing, because F register bits hold flag info (bit 7 = z flag, etc..)
    /**
     * bit = 7, name = zero flag
     * true if operation result = 0
     */
    private boolean z;
    /**
     * bit = 6, name = subtraction flag
     * used by DAA instruction only!
     * n indicates whether previous instruction has been a subtraction
     */
    private boolean n;
    /**
     * bit = 5, name = half carry flag
     * used by DAA instruction only!
     * h indicates carry for lower 4 bits of result
     */
    private boolean h;
    /**
     * bit = 4, name = carryFlag
     *
     * Is set in these cases:
     * When the result of an 8-bit addition is higher than $FF.
     * When the result of a 16-bit addition is higher than $FFFF.
     * When the result of a subtraction or comparison is lower than zero (like in Z80 and x86 CPUs, but unlike in 65XX and ARM CPUs).
     * When a rotate/shift operation shifts out a “1” bit.
     * Used by conditional jumps and instructions such as ADC, SBC, RL, RLA, etc.
     */
    private boolean c;


    /*
    Then we need to sort out how we access opcodes for instruction usage.
    In the past we tried a hash map that linked that opcode address to our instruction methods. We thought
    that we were being smart by having O(1) access to the instruction, but I'm thinking now that this was
    adding an unnecessary optimisation since we only really have 512 opcodes. So we saved int theory save negligible
    runtime from this access. With the overhead of hashmaps however, we probably caused the runtime to actually be
    slower than just using arrays.

    Not only that, we can get instant access from arrays anyway because an opcode will point to an index. The opcodes
    are known so there would be no searching, just instant accesses. I had no idea what I was doing back then...
     */

    // what types should these be? I want each to CALL an instruction..
    // we could make a static instruction class (or have cpu static) and assign each method to an index

    // we have 2 options, create an array of runnables (which we can use lambda expressions to call methods () -> ld_A()
    // or keep as ints and have another method use switch cases to call instructions

    /*
    We'll use an array of runnables because we can set it up once and all values are saved.
    Keeping it as an int[], every time we want to run an instruction we would call the switch case to see
    what the opcode is and run that method..
    Whereas with runnable[] we can set up each opcode with its method and it is saved already (i think?) So
    when we constantly run instructions we avoid having to lookup through switch statements.
    NOTE: JVM seems to optimise int switch cases to O(1) anyway

    If we just switch case, we don't need this array...
     */


    /*
    HANDLING CLOCK CYCLES:

    So the cpu has it's own clock which 'ticks' in its own time. 1hz = 1 cycle/tick per second.
    The GameBoy has a clock cycle of 4.194304Mhz, which is around 4.19 million cycles per second.

    Each cpu instruction 'lasts' for some number of cycles. Could be 1, 2, 3, ...
    So this question arises. When writing this, how do we ACTUALLY handle the cycles?

    Our code will run at it's own speeds (likely much faster than how the GameBoy should be), so we
    need to make sure that we cap ourselves, but how?
    My first thought was to have some real timer running as our program runs, and we calculate the times
    the gameboy should be running at (1 cycle = 0.000002 seconds for example). So when waiting to execute the
    next instruction, we make sure we wait until enough time passes, then call next, so it would be something
    like this:
    if time > cycleTime then runNextInstruction()

    TODO: do some research into cycle implementation first
    For now, just keep track of totalCycles...
     */
    private int totalMCycles;

    /**
     * Constructs..
     */
    public CPU(final Memory memory) {
        this.memory = memory;

        // initialise all to 0
        AF = 0;
        BC = 0;
        DE = 0;
        HL = 0;
        SP = 0;     // stack pointer
        PC = 0x100; // program counter

        // initalise all flags to false
        z = false;
        n = false;
        h = false;
        c = false;

        totalMCycles = 0;
    }

    public void executeInstruction() {
        // opcode is the value at the memory address specified by program counter
        // fetch
        short opcode = memory.readByte(PC);

        if (opcode == 0xCB) {
            // TODO: should we be incrememnting pc? or just access the next pc?
            short prefixOpcode = memory.readByte(PC+1);
            prefixedInstructionCall(prefixOpcode);
        } else {
            instructionCall(opcode);
        }
    }

    // opcode table below used from https://gbdev.io/gb-opcodes//optables/

    /**
     * A giant switch case statement to call each opcodes instruction (0x00 to 0xFF).
     * The DECODE and EXECUTE (as it tries to find the instruction to use and runs it).
     * <br>
     * Calls prefixedInstructionCall() if opcode starts with 0xCB
     * <br><br>
     * JVM will optimise the switch case into a lookup table in O(1), so we won't add any extra overhead trying
     * to optimise this ourselves. This should be very quick.
     * @param opcode the programme opcode
     */
    private void instructionCall(short opcode) {
        // private because we'll handle the execution in another method
        // huge switch case

        switch (opcode) {
            // --- ROW 0 ---
            case 0x00:
                PC += 1; // nop literally does nothing but this,
                totalMCycles += 1;
                break;
            case 0x01:
                ld_r16_n16("BC"); // TODO: in future check this string passing doesn't affect performance too much
                break;
            case 0x02:
                ld_pr16_a("BC");
                break;
            case 0x06:
                ld_r8_n8('B');
                break;
            case 0x0E:
                ld_r8_n8('C');
                break;

            // --- ROW 1 ---
            case 0x10:
                System.out.println("STOP INSTRUCTION");
                break;
            case 0x11:
                ld_r16_n16("DE");
                break;
            case 0x12:
                ld_pr16_a("DE");
                break;
            case 0x16:
                ld_r8_n8('D');
                break;
            case 0x1E:
                ld_r8_n8('E');
                break;

            //--- ROW 2 ---
            case 0x20:
                System.out.println("JR instruction");
                break;
            case 0x21:
                ld_r16_n16("HL");
                break;
            case 0x22:
                ld_pr16_a("HL+");
                break;
            case 0x26:
                ld_r8_n8('H');
                break;
            case 0x2E:
                ld_r8_n8('L');

            // --- ROW 3 ---
            case 0x30:
                System.out.println("JR instruction");
                break;
            case 0x31:
                ld_r16_n16("SP");
                break;
            case 0x32:
                ld_pr16_a("HL-");
                break;
            case 0x3E:
                ld_r8_n8('A');


            default:
                throw new RuntimeException("invalid opcode: " + opcode);
        }
    }

    /**
     * When opcode is prefixed with xCB
     * @param opcode the programme opcode
     */
    private void prefixedInstructionCall(short opcode) {
        switch (opcode) {
            case 0x00:
                System.out.println("implement the prefixed opcodes!");
                break;
            default:
                throw new RuntimeException("invalid opcode: " + opcode);
        }
    }


    // --- INSTRUCTIONS ---
    // with help from https://rgbds.gbdev.io/docs/v0.9.1/gbz80.7#INSTRUCTION_REFERENCE


    // --- LDs ---
    // r = register, so r8 = 8bit reg, r16 = 16bit. pr = the address a register points to
    // n = mem value at PC, n16 is the little endian word (handled in read/writeWord())
    // snake_casing for now since camelCase looks extremely bad for these names

    private void ld_r8_n8(char register) {
        short value = memory.readByte(register);

        if (register == 'A') {
            setr8('A', value);
        } else if (register == 'B') {
            setr8('B', value);
        } else if (register == 'C') {
            setr8('C', value);
        } else if (register == 'D') {
            setr8('D', value);
        } else if (register == 'E') {
            setr8('E', value);
        } else if (register == 'H') {
            setr8('H', value);
        } else if (register == 'L') {
            setr8('L', value);
        } else {
            throw new RuntimeException("invalid register: " + register);
        }

        totalMCycles += 2;
        PC += 2;
    }

    private void ld_r16_n16(String registers) {
        if (registers.equals("BC")) {
            BC = memory.readWord(PC);
        } else if (registers.equals("DE")) {
            DE = memory.readWord(PC);
        } else if (registers.equals("HL")) {
            HL = memory.readWord(PC);
        } else if (registers.equals("SP")) {
            SP = memory.readWord(PC);
        } else {
            throw new RuntimeException("invalid register: " + registers + " for LD r16,n16 instruction");
        }
        // for each instruction, we have length in bytes. This one = 3.
        // I think, since it reads a word, we get the pc and pc+1 (2 bytes of data), then the last byte is the
        // pc incrementation. So since we access pc, pc+1, the pc should end at pc + 2 (or 3??)
        totalMCycles += 3;
        PC += 3; // might actually be just 2
    }

    // this loads a into the "byte pointed to by r16"
    // i'm guessing we get the mem address from r16?? Yes! then write A to it..
    private void ld_pr16_a(String registers) {

        int address = 0;
        if (registers.equals("BC")) {
            address = memory.readWord(BC);
        } else if (registers.equals("DE")) {
            address = memory.readWord(DE);
        } else if (registers.equals("HL+")) { // HL+ HL- == HLI HLD which is Increment/decrement after
            address = memory.readWord(HL);
            HL++;
        } else if (registers.equals("HL-")) {
            address = memory.readWord(HL);
            HL--;
        } else {
            throw new RuntimeException("invalid register: " + registers + " for LD pr16,a");
        }
        // since we combine registers to 16bit AF instead of A then F, we need to bit shift for the values
        // TODO: consider changing to 8bit registers only?
        memory.writeByte(address, (short)(AF >> 8)); // A is high byte, so bit shift right to get byte for A

        totalMCycles += 2;
        PC += 1;
    }


    // ------ HELPER METHODS --------

    /**
     * 8 bit register writes (handles the bitwise operations)
     * @param register the register we want to write to
     * @param value the byte we want to write
     */
    private void setr8(char register, int value) {
        if (register == 'A') {
            AF = (0x00FF & AF) | (value << 8); // rewrites high byte
        } else if (register == 'B') {
            BC = (0x00FF & BC) | (value << 8);
        } else if (register == 'C') {
            BC = (0xFF00 & BC) | value; // rewrites low byte
        } else if (register == 'D') {
            DE = (0x00FF & DE) | (value << 8);
        } else if (register == 'E') {
            DE = (0xFF00 & DE) | value; // rewrites low byte
        } else if (register == 'H') {
            HL = (0x00FF & HL) | (value << 8);
        } else if (register == 'L') {
            HL = (0xFF00 & HL) | value;
        } else {
            throw new RuntimeException("invalid register: " + register + " or if F, SP, PC, we cannot set a value here.");
        }
    }

    /**
     * 8 bit register reads (handles the bitwise operations)
     * @param register the register we want to read
     * @return the byte value
     */
    private int getr8(char register) {
        if (register == 'A') {
            return AF >> 8; // removes low byte
        } else if (register == 'B') {
            return BC >> 8;
        } else if (register == 'C') {
            return 0x00FF & BC;
        } else if (register == 'D') {
            return DE >> 8;
        } else if (register == 'E') {
            return 0x00FF & DE;
        } else if (register == 'H') {
            return HL >> 8;
        } else if (register == 'L') {
            return 0x00FF & HL;
        } else {
            throw new RuntimeException("invalid register: " + register + " or if F, SP, PC, we cannot set a value here.");
        }
    }

}

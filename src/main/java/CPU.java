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
            short prefixOpcode = memory.readByte(PC+1); // access next pc for now
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
            case 0x03:
                inc_r16("BC");
                break;
            case 0x04:
                inc_r8('B');
                break;
            case 0x05:
                dec_r8('B');
                break;
            case 0x06:
                ld_r8_n8('B');
                break;
            case 0x0A:
                ld_a_pr16("BC");
                break;
            case 0x0B:
                dec_r16("BC");
                break;
            case 0x0C:
                inc_r8('C');
                break;
            case 0x0D:
                dec_r8('C');
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
            case 0x13:
                inc_r16("DE");
                break;
            case 0x14:
                inc_r8('D');
                break;
            case 0x15:
                dec_r8('D');
                break;
            case 0x16:
                ld_r8_n8('D');
                break;
            case 0x1A:
                ld_a_pr16("DE");
                break;
            case 0x1B:
                dec_r16("DE");
                break;
            case 0x1C:
                inc_r8('E');
                break;
            case 0x1D:
                dec_r8('E');
                break;
            case 0x1E:
                ld_r8_n8('E');
                break;

            //--- ROW 2 ---
            case 0x20:
                System.out.println("JR instruction"); // TODO
                break;
            case 0x21:
                ld_r16_n16("HL");
                break;
            case 0x22:
                ld_pr16_a("HL+");
                break;
            case 0x23:
                inc_r16("HL");
                break;
            case 0x24:
                inc_r8('H');
                break;
            case 0x25:
                dec_r8('H');
                break;
            case 0x26:
                ld_r8_n8('H');
                break;
            case 0x2A:
                ld_a_pr16("HL+");
                break;
            case 0x2B:
                dec_r16("HL");
                break;
            case 0x2C:
                inc_r8('L');
                break;
            case 0x2D:
                dec_r8('L');
                break;
            case 0x2E:
                ld_r8_n8('L');
                break;

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
            case 0x33:
                inc_r16("SP");
                break;
            case 0x34:
                inc_phl();
                break;
            case 0x35:
                dec_phl();
                break;
            case 0x36:
                ld_phl_n8();
                break;
            case 0x3A:
                ld_a_pr16("HL-");
                break;
            case 0x3B:
                dec_r16("SP");
                break;
            case 0x3C:
                inc_r8('A');
                break;
            case 0x3D:
                dec_r8('A');
                break;
            case 0x3E:
                ld_r8_n8('A');
                break;

                // TODO: ALL 0x..A up to 3A

            // --- ROW 4 ---
            case 0x40:
                ld_r8_r8('B', 'B');
                break;
            case 0x41:
                ld_r8_r8('B', 'C');
                break;
            case 0x42:
                ld_r8_r8('B', 'D');
                break;
            case 0x43:
                ld_r8_r8('B', 'E');
                break;
            case 0x44:
                ld_r8_r8('B', 'H');
                break;
            case 0x45:
                ld_r8_r8('B', 'L');
                break;
            case 0x46:
                ld_r8_phl('B');
                break;
            case 0x47:
                ld_r8_r8('B', 'A');
                break;
            case 0x48:
                ld_r8_r8('C', 'B');
                break;
            case 0x49:
                ld_r8_r8('C', 'C');
                break;
            case 0x4A:
                ld_r8_r8('C', 'D');
                break;
            case 0x4B:
                ld_r8_r8('C', 'E');
                break;
            case 0x4C:
                ld_r8_r8('C', 'H');
                break;
            case 0x4D:
                ld_r8_r8('C', 'L');
                break;
            case 0x4E:
                ld_r8_phl('C');
                break;
            case 0x4F:
                ld_r8_r8('C', 'A');
                break;

            // --- ROW 5 ---
            case 0x50:
                ld_r8_r8('D', 'B');
                break;
            case 0x51:
                ld_r8_r8('D', 'C');
                break;
            case 0x52:
                ld_r8_r8('D', 'D');
                break;
            case 0x53:
                ld_r8_r8('D', 'E');
                break;
            case 0x54:
                ld_r8_r8('D', 'H');
                break;
            case 0x55:
                ld_r8_r8('D', 'L');
                break;
            case 0x56:
                ld_r8_phl('D');
                break;
            case 0x57:
                ld_r8_r8('D', 'A');
                break;
            case 0x58:
                ld_r8_r8('E', 'B');
                break;
            case 0x59:
                ld_r8_r8('E', 'C');
                break;
            case 0x5A:
                ld_r8_r8('E', 'D');
                break;
            case 0x5B:
                ld_r8_r8('E', 'E');
                break;
            case 0x5C:
                ld_r8_r8('E', 'H');
                break;
            case 0x5D:
                ld_r8_r8('E', 'L');
                break;
            case 0x5E:
                ld_r8_phl('E');
                break;
            case 0x5F:
                ld_r8_r8('E', 'A');
                break;

            // --- ROW 6 ---
            case 0x60:
                ld_r8_r8('H', 'B');
                break;
            case 0x61:
                ld_r8_r8('H', 'C');
                break;
            case 0x62:
                ld_r8_r8('H', 'D');
                break;
            case 0x63:
                ld_r8_r8('H', 'E');
                break;
            case 0x64:
                ld_r8_r8('H', 'H');
                break;
            case 0x65:
                ld_r8_r8('H', 'L');
                break;
            case 0x66:
                ld_r8_phl('H');
                break;
            case 0x67:
                ld_r8_r8('H', 'A');
                break;
            case 0x68:
                ld_r8_r8('L', 'B');
                break;
            case 0x69:
                ld_r8_r8('L', 'C');
                break;
            case 0x6A:
                ld_r8_r8('L', 'D');
                break;
            case 0x6B:
                ld_r8_r8('L', 'E');
                break;
            case 0x6C:
                ld_r8_r8('L', 'H');
                break;
            case 0x6D:
                ld_r8_r8('L', 'L');
                break;
            case 0x6E:
                ld_r8_phl('L');
                break;
            case 0x6F:
                ld_r8_r8('L', 'A');
                break;

            // --- ROW 7 ---
            case 0x70:
                ld_phl_r8('B');
                break;
            case 0x71:
                ld_phl_r8('C');
                break;
            case 0x72:
                ld_phl_r8('D');
                break;
            case 0x73:
                ld_phl_r8('E');
                break;
            case 0x74:
                ld_phl_r8('H');
                break;
            case 0x75:
                ld_phl_r8('L');
                break;
            case 0x76:
                throw new RuntimeException("Implement HALT");
            case 0x77:
                ld_phl_r8('A');
                break;
            case 0x78:
                ld_r8_r8('A', 'B');
                break;
            case 0x79:
                ld_r8_r8('A', 'C');
                break;
            case 0x7A:
                ld_r8_r8('A', 'D');
                break;
            case 0x7B:
                ld_r8_r8('A', 'E');
                break;
            case 0x7C:
                ld_r8_r8('A', 'H');
                break;
            case 0x7D:
                ld_r8_r8('A', 'L');
                break;
            case 0x7E:
                ld_r8_phl('A');
                break;
            case 0x7F:
                ld_r8_r8('A', 'A');
                break;



            // --- ROW E ---
            case 0xE0:
                ldh_pn16_a();
                break;
            case 0xE2:
                ldh_pc_a();
                break;
            case 0xEA:
                ld_pn16_a();
                break;




            // --- ROW F ---
            case 0xF0:
                ldh_a_pn16();
                break;
            case 0xF2:
                ldh_a_pc();
                break;
            case 0xFA:
                ld_a_pn16();
                break;



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


    // --- LD 's ---
    // r = register, so r8 = 8bit reg, r16 = 16bit. pr = the address a register points to
    // n = mem value at PC, n16 is the little endian word (handled in read/writeWord())
    // snake_casing for now since camelCase looks extremely bad for these names

    // has to handle each register with every other register (n^2)
    private void ld_r8_r8(char toRegister, char fromRegister) {
        if (toRegister == 'A') {
            switch (fromRegister) {
                case 'A':
                    System.out.println("LD A,A. We do nothing for now");
                    break;
                case 'B':
                    setr8('A', getr8('B'));
                    break;
                case 'C':
                    setr8('A', getr8('C'));
                    break;
                case 'D':
                    setr8('A', getr8('D'));
                    break;
                case 'E':
                    setr8('A', getr8('E'));
                    break;
                case 'H':
                    setr8('A', getr8('H'));
                    break;
                case 'L':
                    setr8('A', getr8('L'));
                    break;
                default:
                    throw new RuntimeException("load from register is incorrect: " + fromRegister);
            }
        } else if (toRegister == 'B') {
             switch (fromRegister) {
                case 'A':
                    setr8('B', getr8('A'));
                    break;
                case 'B':
                    System.out.println("LD B,B. We do nothing for now");
                    break;
                case 'C':
                    setr8('B', getr8('C'));
                    break;
                case 'D':
                    setr8('B', getr8('D'));
                    break;
                case 'E':
                    setr8('B', getr8('E'));
                    break;
                case 'H':
                    setr8('B', getr8('H'));
                    break;
                case 'L':
                    setr8('B', getr8('L'));
                    break;
                default:
                    throw new RuntimeException("load from register is incorrect: " + fromRegister);
            }
        } else if (toRegister == 'C') {
             switch (fromRegister) {
                case 'A':
                    setr8('C', getr8('A'));
                    break;
                case 'B':
                    setr8('C', getr8('B'));
                    break;
                case 'C':
                    System.out.println("LD C,C. We do nothing for now");
                    break;
                case 'D':
                    setr8('C', getr8('D'));
                    break;
                case 'E':
                    setr8('C', getr8('E'));
                    break;
                case 'H':
                    setr8('C', getr8('H'));
                    break;
                case 'L':
                    setr8('C', getr8('L'));
                    break;
                default:
                    throw new RuntimeException("load from register is incorrect: " + fromRegister);
            }
        } else if (toRegister == 'D') {
             switch (fromRegister) {
                case 'A':
                    setr8('D', getr8('A'));
                    break;
                case 'B':
                    setr8('D', getr8('B'));
                    break;
                case 'C':
                    setr8('D', getr8('C'));
                    break;
                case 'D':
                    System.out.println("LD D,D. We do nothing for now");
                    break;
                case 'E':
                    setr8('D', getr8('E'));
                    break;
                case 'H':
                    setr8('D', getr8('H'));
                    break;
                case 'L':
                    setr8('D', getr8('L'));
                    break;
                default:
                    throw new RuntimeException("load from register is incorrect: " + fromRegister);
            }
        } else if (toRegister == 'E') {
             switch (fromRegister) {
                case 'A':
                    setr8('E', getr8('A'));
                    break;
                case 'B':
                    setr8('E', getr8('B'));
                    break;
                case 'C':
                    setr8('E', getr8('C'));
                    break;
                case 'D':
                    setr8('E', getr8('D'));
                    break;
                case 'E':
                    System.out.println("LD E,E. We do nothing for now");
                    break;
                case 'H':
                    setr8('E', getr8('H'));
                    break;
                case 'L':
                    setr8('E', getr8('L'));
                    break;
                default:
                    throw new RuntimeException("load from register is incorrect: " + fromRegister);
            }
        } else if (toRegister == 'H') {
             switch (fromRegister) {
                case 'A':
                    setr8('H', getr8('A'));
                    break;
                case 'B':
                    setr8('H', getr8('B'));
                    break;
                case 'C':
                    setr8('H', getr8('C'));
                    break;
                case 'D':
                    setr8('H', getr8('D'));
                    break;
                case 'E':
                    setr8('H', getr8('E'));
                    break;
                case 'H':
                    System.out.println("LD H,H. We do nothing for now");
                    break;
                case 'L':
                    setr8('H', getr8('L'));
                    break;
                default:
                    throw new RuntimeException("load from register is incorrect: " + fromRegister);
            }
        } else if (toRegister == 'L') {
             switch (fromRegister) {
                case 'A':
                    setr8('L', getr8('A'));
                    break;
                case 'B':
                    setr8('L', getr8('B'));
                    break;
                case 'C':
                    setr8('L', getr8('C'));
                    break;
                case 'D':
                    setr8('L', getr8('D'));
                    break;
                case 'E':
                    setr8('L', getr8('E'));
                    break;
                case 'H':
                    setr8('L', getr8('H'));
                    break;
                case 'L':
                    System.out.println("LD L,L. We do nothing for now");
                    break;
                default:
                    throw new RuntimeException("load from register is incorrect: " + fromRegister);
            }
        }

        totalMCycles += 1;
        PC += 1;
    }

    private void ld_r8_n8(char register) {
        short value = memory.readByte(PC);

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

    private void ld_phl_r8(char register) {
        if (register == 'A') {
            memory.writeByte(HL, (short)getr8('A'));
        } else if (register == 'B') {
            memory.writeByte(HL, (short)getr8('B'));
        } else if (register == 'C') {
            memory.writeByte(HL, (short)getr8('C'));
        } else if (register == 'D') {
            memory.writeByte(HL, (short)getr8('D'));
        } else if (register == 'E') {
            memory.writeByte(HL, (short)getr8('E'));
        } else if (register == 'H') {
            memory.writeByte(HL, (short)getr8('H'));
        } else if (register == 'L') {
            memory.writeByte(HL, (short)getr8('L'));
        } else {
            throw new RuntimeException("invalid register: " + register);
        }

        totalMCycles += 2;
        PC += 1;
    }

    private void ld_r8_phl(char register) {
        int hlAddressValue = memory.readByte(HL);

        if (register == 'A') {
            setr8('A', hlAddressValue);
        } else if (register == 'B') {
            setr8('B', hlAddressValue);
        } else if (register == 'C') {
            setr8('C', hlAddressValue);
        } else if (register == 'D') {
            setr8('D', hlAddressValue);
        } else if (register == 'E') {
            setr8('E', hlAddressValue);
        } else if (register == 'H') {
            setr8('H', hlAddressValue);
        } else if (register == 'L') {
            setr8('L', hlAddressValue);
        } else {
            throw new RuntimeException("invalid register: " + register);
        }

        totalMCycles += 2;
        PC += 1;
    }

    private void ld_phl_n8() {
        short value = memory.readByte(PC);
        memory.writeByte(HL, value);

        totalMCycles += 3;
        PC += 2;
    }

    private void ldh_pc_a() {
        short regCValue = (short) getr8('C');
        int address = 0xFF00 | regCValue;
        memory.writeByte(address, (short)getr8('A'));

        totalMCycles += 2;
       PC += 1;
    }

    private void ldh_a_pc() {
        short regCValue = (short) getr8('C'); // TODO: consider changing return type to short (on getr8)
        int address = 0xFF00 | regCValue;
        setr8('A', memory.readByte(address));

        totalMCycles += 2;
        PC += 1;
    }

    private void ldh_pn16_a() {
        if (0xFF00 <= PC && PC <= 0xFFFF) {
            ld_pn16_a();
        }
        totalMCycles += 3; // TODO: should these be inside the if?? does the cycles/pc still increment if PC is not in a suitable place?
        PC += 2;
    }

    private void ldh_a_pn16() {
        if (0xFF00 <= PC && PC <= 0xFFFF) {
            ld_a_pn16();
        }
        totalMCycles += 3;
        PC += 2;
    }
    // [n] means we go to the address of the address that PC points to
    private void ld_pn16_a() {
        int address = memory.readWord(PC);
        memory.writeByte(address, (short) getr8('A'));
        totalMCycles += 4;
        PC += 3;
    }

    private void ld_a_pn16() {
        int address = memory.readWord(PC);
        setr8('A', memory.readByte(address));
        totalMCycles += 4;
        PC += 3;
    }


    private void ld_r16_n16(String registers) {
        if (registers.equals("BC")) {
            BC = memory.readWord(PC); // n16 is value where PC points to. [n16] is again address of n16 (so 2 memory addresses)
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
    // i'm guessing we get the mem address of r16's value?? Yes! then write A to it..
    private void ld_pr16_a(String registers) {
        short regAValue = (short)getr8('A');
        if (registers.equals("BC")) {
            memory.writeByte(BC, regAValue);
        } else if (registers.equals("DE")) {
            memory.writeByte(DE, regAValue);
        } else if (registers.equals("HL+")) { // HL+ HL- == HLI HLD which is Increment/decrement after
            memory.writeByte(HL, regAValue);
            HL++;
        } else if (registers.equals("HL-")) {
            memory.writeByte(HL, regAValue);
            HL--;
        } else {
            throw new RuntimeException("invalid register: " + registers + " for LD pr16,a");
        }

        totalMCycles += 2;
        PC += 1;
    }

    private void ld_a_pr16(String registers) {
        if (registers.equals("BC")) {
            setr8('A', memory.readByte(BC));
        } else if (registers.equals("DE")) {
            setr8('A', memory.readByte(DE));
        } else if (registers.equals("HL+")) { // HL+ HL- == HLI HLD which is Increment/decrement after
            setr8('A', memory.readByte(HL));
            HL++;
        } else if (registers.equals("HL-")) {
            setr8('A', memory.readByte(HL));
            HL--;
        } else {
            throw new RuntimeException("invalid register: " + registers + " for LD pr16,a");
        }

        totalMCycles += 2;
        PC += 1;
    }


    // --- INC/DEC ---

    // inc/dec_r8 0x04/05/0C/0D to 0x34/35/3C/3D
    private void inc_r8(char register) {
        if (register == 'A') {
            short value = (short) getr8('A');
            value++;
            setr8('A', value);

            setNFlag(false); // 0

            if (value == 0) {
                setZFlag(true);
            } else if (value >= 0xF) { // overflow from bit 3 (so more or equal to 4 bits?
                setHFlag(true); // keep as else if (since we can't have a 0 and an overflow can we ??)
            }
        } else if (register == 'B') {
            short value = (short) getr8('B');
            value++;
            setr8('B', value);

            setNFlag(false); // 0

            if (value == 0) {
                setZFlag(true);
            } else if (value >= 0xF) {
                setHFlag(true);
            }
        } else if (register == 'C') {
            short value = (short) getr8('C');
            value++;
            setr8('C', value);

            setNFlag(false); // 0

            if (value == 0) {
                setZFlag(true);
            } else if (value >= 0xF) {
                setHFlag(true);
            }
        } else if (register == 'D') {
            short value = (short) getr8('D');
            value++;
            setr8('D', value);

            setNFlag(false); // 0

            if (value == 0) {
                setZFlag(true);
            } else if (value >= 0xF) {
                setHFlag(true);
            }
        } else if (register == 'E') {
            short value = (short) getr8('E');
            value++;
            setr8('E', value);

            setNFlag(false); // 0

            if (value == 0) {
                setZFlag(true);
            } else if (value >= 0xF) {
                setHFlag(true);
            }
        } else if (register == 'H') {
            short value = (short) getr8('H');
            value++;
            setr8('H', value);

            setNFlag(false); // 0

            if (value == 0) {
                setZFlag(true);
            } else if (value >= 0xF) {
                setHFlag(true);
            }
        } else if (register == 'L') {
            short value = (short) getr8('L');
            value++;
            setr8('L', value);

            setNFlag(false); // 0

            if (value == 0) {
                setZFlag(true);
            } else if (value >= 0xF) {
                setHFlag(true);
            }
        }

        totalMCycles += 1;
        PC += 1;
    }

    private void dec_r8(char register) {
        if (register == 'A') {
            short value = (short) getr8('A');
            value--;
            setr8('A', value);

            setNFlag(true); // 1

            if (value == 0) {
                setZFlag(true);
            } else if (value <= 0xF) { // set if borrow from bit 4 TODO: not sure what this means? keep as <= 0xF for now
                setHFlag(true);
            }
        } else if (register == 'B') {
            short value = (short) getr8('B');
            value--;
            setr8('B', value);

            setNFlag(true);

            if (value == 0) {
                setZFlag(true);
            } else if (value <= 0xF) {
                setHFlag(true);
            }
        } else if (register == 'C') {
            short value = (short) getr8('C');
            value--;
            setr8('C', value);

            setNFlag(true);

            if (value == 0) {
                setZFlag(true);
            } else if (value <= 0xF) {
                setHFlag(true);
            }
        } else if (register == 'D') {
            short value = (short) getr8('D');
            value--;
            setr8('D', value);

            setNFlag(true);

            if (value == 0) {
                setZFlag(true);
            } else if (value <= 0xF) {
                setHFlag(true);
            }
        } else if (register == 'E') {
            short value = (short) getr8('E');
            value--;
            setr8('E', value);

            setNFlag(true);

            if (value == 0) {
                setZFlag(true);
            } else if (value <= 0xF) {
                setHFlag(true);
            }
        } else if (register == 'H') {
            short value = (short) getr8('H');
            value--;
            setr8('H', value);

            setNFlag(true);

            if (value == 0) {
                setZFlag(true);
            } else if (value <= 0xF) {
                setHFlag(true);
            }
        } else if (register == 'L') {
            short value = (short) getr8('L');
            value--;
            setr8('L', value);

            setNFlag(true);

            if (value == 0) {
                setZFlag(true);
            } else if (value <= 0xF) {
                setHFlag(true);
            }
        }

        totalMCycles += 1;
        PC += 1;
    }

    private void inc_phl() {
        short addressValue = memory.readByte(HL);
        addressValue++;
        memory.writeByte(HL, addressValue);

        setNFlag(false);

        if (addressValue == 0) {
            setZFlag(true);
        } else if (addressValue >= 0xF) {
            setHFlag(true);
        }

        totalMCycles += 3;
        PC += 1;
    }

    private void dec_phl() {
        short addressValue = memory.readByte(HL);
        addressValue--;
        memory.writeByte(HL, addressValue);

        setNFlag(true);

        if (addressValue == 0) {
            setZFlag(true);
        } else if (addressValue <= 0xF) { // set if borrow from bit 4. Not sure what this means, we'll keep as <= 0xF for now
            setHFlag(true);
        }

        totalMCycles += 3;
        PC += 1;
    }

    // inc_r16 /dec, 0x03/0x0B ... 0x33/3B
    private void inc_r16(String registers) {
        if (registers.equals("BC")) {
            BC++;
        } else if (registers.equals("DE")) {
            DE++;
        } else if (registers.equals("HL")) {
            HL++;
        } else if (registers.equals("SP")) {
            SP++;
        } else {
            throw new RuntimeException("invalid register pair: " + registers + " for INC r16");
        }

        totalMCycles += 2;
        PC += 1;
    }

    private void dec_r16(String registers) {
        if (registers.equals("BC")) {
            BC--;
        } else if (registers.equals("DE")) {
            DE--;
        } else if (registers.equals("HL")) {
            HL--;
        } else if (registers.equals("SP")) {
            SP--;
        } else {
            throw new RuntimeException("invalid register pair: " + registers + " for DEC r16");
        }

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
        } else if (register == 'F') {
            AF = (0xFF00 & AF) | value; // rewrites low byte
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
        } else if (register == 'F') {
            return 0x00FF & AF;
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

    /**
     * Zero Flag, Accesses the 7th bit in F register (from AF) and sets it to 1 or 0
     */
    private void setZFlag(boolean toOne) {
        short regFValue = (short) getr8('F');
        int zFlagSet = regFValue | 0b10000000; // keeps all other bits same but makes sure 7th is set
        if (!toOne) {
            zFlagSet = zFlagSet & 0b01111111; // otherwise set 7th bit to off (0)
        }
        setr8('F', zFlagSet);
    }

    /**
     * Subtraction Flag, Accesses the 6th bit in F register (from AF) and sets it to 1 or 0
     */
    private void setNFlag(boolean toOne) {
        short regFValue = (short) getr8('F');
        int zFlagSet = regFValue | 0b01000000;
        if (!toOne) {
            zFlagSet = zFlagSet & 0b10111111;
        }
        setr8('F', zFlagSet);
    }

    /**
     * Half Carry Flag, Accesses the 5th bit in F register (from AF) and sets it to 1 or 0
     */
    private void setHFlag(boolean toOne) {
        short regFValue = (short) getr8('F');
        int zFlagSet = regFValue | 0b00100000;
        if (!toOne) {
            zFlagSet = zFlagSet & 0b11011111;
        }
        setr8('F', zFlagSet);
    }

    /**
     * CarryFlag, Accesses the 4th bit in F register (from AF) and sets it to 1 or 0
     */
    private void setCFlag(boolean toOne) {
        short regFValue = (short) getr8('F');
        int zFlagSet = regFValue | 0b00010000;
        if (!toOne) {
            zFlagSet = zFlagSet & 0b11101111;
        }
        setr8('F', zFlagSet);
    }


}

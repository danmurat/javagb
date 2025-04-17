/**
 * Represents the game boy CPU.
 * <br>
 * Will hold ALL the instructions (bit manipulations), and registers from the unit.
 */
public class CPU {
    private Memory memory;

    private int AF; // A = high, F = lo (Lower 8 bits (F) hold Flag information)
    private int BC;
    private int DE;
    private int HL;

    private int SP;
    private int PC;
    private int pcAccess = PC + 1;

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

        totalMCycles = 0;
    }

    /**
     * Main cpu execution method.
     * Reads opcode, finds and runs correct instruction.
     */
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
    private void instructionCall(final short opcode) {
        // private because we'll handle the execution in another method
        // huge switch case

        switch (opcode) {
            // --- ROW 0 ---
            case 0x00 -> { // nop literally does nothing but this,
                totalMCycles += 1;
                PC += 1;
            }
            case 0x01 -> ld_r16_n16("BC"); // TODO: in future check this string passing doesn't affect performance too much
            case 0x02 -> ld_pr16_a("BC");
            case 0x03 -> inc_r16("BC");
            case 0x04 -> inc_r8('B');
            case 0x05 -> dec_r8('B');
            case 0x06 -> ld_r8_n8('B');
            case 0x09 -> add_hl_r16("BC");
            case 0x0A -> ld_a_pr16("BC");
            case 0x0B -> dec_r16("BC");
            case 0x0C -> inc_r8('C');
            case 0x0D -> dec_r8('C');
            case 0x0E -> ld_r8_n8('C');

            // --- ROW 1 ---
            case 0x10 -> System.out.println("STOP INSTRUCTION");
            case 0x11 -> ld_r16_n16("DE");
            case 0x12 -> ld_pr16_a("DE");
            case 0x13 -> inc_r16("DE");
            case 0x14 -> inc_r8('D');
            case 0x15 -> dec_r8('D');
            case 0x16 -> ld_r8_n8('D');
            case 0x19 -> add_hl_r16("DE");
            case 0x1A -> ld_a_pr16("DE");
            case 0x1B -> dec_r16("DE");
            case 0x1C -> inc_r8('E');
            case 0x1D -> dec_r8('E');
            case 0x1E -> ld_r8_n8('E');

            //--- ROW 2 ---
            case 0x20 -> System.out.println("JR instruction"); // TODO
            case 0x21 -> ld_r16_n16("HL");
            case 0x22 -> ld_pr16_a("HL+");
            case 0x23 -> inc_r16("HL");
            case 0x24 -> inc_r8('H');
            case 0x25 -> dec_r8('H');
            case 0x26 -> ld_r8_n8('H');
            case 0x29 -> add_hl_r16("HL");
            case 0x2A -> ld_a_pr16("HL+");
            case 0x2B -> dec_r16("HL");
            case 0x2C -> inc_r8('L');
            case 0x2D -> dec_r8('L');
            case 0x2E -> ld_r8_n8('L');

            // --- ROW 3 ---
            case 0x30 -> System.out.println("JR instruction");
            case 0x31 -> ld_r16_n16("SP");
            case 0x32 -> ld_pr16_a("HL-");
            case 0x33 -> inc_r16("SP");
            case 0x34 -> inc_phl();
            case 0x35 -> dec_phl();
            case 0x36 -> ld_phl_n8();
            case 0x39 -> add_hl_r16("SP");
            case 0x3A -> ld_a_pr16("HL-");
            case 0x3B -> dec_r16("SP");
            case 0x3C -> inc_r8('A');
            case 0x3D -> dec_r8('A');
            case 0x3E -> ld_r8_n8('A');

                // TODO: ALL 0x..A up to 3A

            // --- ROW 4 ---
            case 0x40 -> ld_r8_r8('B', 'B');
            case 0x41 -> ld_r8_r8('B', 'C');
            case 0x42 -> ld_r8_r8('B', 'D');
            case 0x43 -> ld_r8_r8('B', 'E');
            case 0x44 -> ld_r8_r8('B', 'H');
            case 0x45 -> ld_r8_r8('B', 'L');
            case 0x46 -> ld_r8_phl('B');
            case 0x47 -> ld_r8_r8('B', 'A');
            case 0x48 -> ld_r8_r8('C', 'B');
            case 0x49 -> ld_r8_r8('C', 'C');
            case 0x4A -> ld_r8_r8('C', 'D');
            case 0x4B -> ld_r8_r8('C', 'E');
            case 0x4C -> ld_r8_r8('C', 'H');
            case 0x4D -> ld_r8_r8('C', 'L');
            case 0x4E -> ld_r8_phl('C');
            case 0x4F -> ld_r8_r8('C', 'A');

            // --- ROW 5 ---
            case 0x50 -> ld_r8_r8('D', 'B');
            case 0x51 -> ld_r8_r8('D', 'C');
            case 0x52 -> ld_r8_r8('D', 'D');
            case 0x53 -> ld_r8_r8('D', 'E');
            case 0x54 -> ld_r8_r8('D', 'H');
            case 0x55 -> ld_r8_r8('D', 'L');
            case 0x56 -> ld_r8_phl('D');
            case 0x57 -> ld_r8_r8('D', 'A');
            case 0x58 -> ld_r8_r8('E', 'B');
            case 0x59 -> ld_r8_r8('E', 'C');
            case 0x5A -> ld_r8_r8('E', 'D');
            case 0x5B -> ld_r8_r8('E', 'E');
            case 0x5C -> ld_r8_r8('E', 'H');
            case 0x5D -> ld_r8_r8('E', 'L');
            case 0x5E -> ld_r8_phl('E');
            case 0x5F -> ld_r8_r8('E', 'A');

            // --- ROW 6 ---
            case 0x60 -> ld_r8_r8('H', 'B');
            case 0x61 -> ld_r8_r8('H', 'C');
            case 0x62 -> ld_r8_r8('H', 'D');
            case 0x63 -> ld_r8_r8('H', 'E');
            case 0x64 -> ld_r8_r8('H', 'H');
            case 0x65 -> ld_r8_r8('H', 'L');
            case 0x66 -> ld_r8_phl('H');
            case 0x67 -> ld_r8_r8('H', 'A');
            case 0x68 -> ld_r8_r8('L', 'B');
            case 0x69 -> ld_r8_r8('L', 'C');
            case 0x6A -> ld_r8_r8('L', 'D');
            case 0x6B -> ld_r8_r8('L', 'E');
            case 0x6C -> ld_r8_r8('L', 'H');
            case 0x6D -> ld_r8_r8('L', 'L');
            case 0x6E -> ld_r8_phl('L');
            case 0x6F -> ld_r8_r8('L', 'A');

            // --- ROW 7 ---
            case 0x70 -> ld_phl_r8('B');
            case 0x71 -> ld_phl_r8('C');
            case 0x72 -> ld_phl_r8('D');
            case 0x73 -> ld_phl_r8('E');
            case 0x74 -> ld_phl_r8('H');
            case 0x75 -> ld_phl_r8('L');
            case 0x76 -> throw new RuntimeException("Implement HALT");
            case 0x77 -> ld_phl_r8('A');
            case 0x78 -> ld_r8_r8('A', 'B');
            case 0x79 -> ld_r8_r8('A', 'C');
            case 0x7A -> ld_r8_r8('A', 'D');
            case 0x7B -> ld_r8_r8('A', 'E');
            case 0x7C -> ld_r8_r8('A', 'H');
            case 0x7D -> ld_r8_r8('A', 'L');
            case 0x7E -> ld_r8_phl('A');
            case 0x7F -> ld_r8_r8('A', 'A');

            // --- ROW 8 ---
            case 0x80 -> add_a_r8('B');
            case 0x81 -> add_a_r8('C');
            case 0x82 -> add_a_r8('D');
            case 0x83 -> add_a_r8('E');
            case 0x84 -> add_a_r8('H');
            case 0x85 -> add_a_r8('L');
            case 0x86 -> add_a_phl();
            case 0x87 -> add_a_r8('A');
            case 0x88 -> adc_a_r8('B');
            case 0x89 -> adc_a_r8('C');
            case 0x8A -> adc_a_r8('D');
            case 0x8B -> adc_a_r8('E');
            case 0x8C -> adc_a_r8('H');
            case 0x8D -> adc_a_r8('L');
            case 0x8E -> adc_a_phl();
            case 0x8F -> adc_a_r8('A');

            // --- ROW 9 ---
            case 0x90 -> sub_a_r8('B');
            case 0x91 -> sub_a_r8('C');
            case 0x92 -> sub_a_r8('D');
            case 0x93 -> sub_a_r8('E');
            case 0x94 -> sub_a_r8('H');
            case 0x95 -> sub_a_r8('L');
            case 0x96 -> sub_a_phl();
            case 0x97 -> sub_a_r8('A');
            case 0x98 -> sbc_a_r8('B');
            case 0x99 -> sbc_a_r8('C');
            case 0x9A -> sbc_a_r8('D');
            case 0x9B -> sbc_a_r8('E');
            case 0x9C -> sbc_a_r8('H');
            case 0x9D -> sbc_a_r8('L');
            case 0x9E -> sbc_a_phl();
            case 0x9F -> sbc_a_r8('A');

            // --- ROW C ---
            case 0xC6 -> add_a_n8();
            case 0xCE -> adc_a_n8();

            // --- ROW D ---
            case 0xD6 -> sub_a_n8();
            case 0xDE -> sbc_a_n8();

            // --- ROW E ---
            case 0xE0 -> ldh_pn16_a();
            case 0xE2 -> ldh_pc_a();
            case 0xE8 -> add_sp_e8();
            case 0xEA -> ld_pn16_a();

            // --- ROW F ---
            case 0xF0 -> ldh_a_pn16();
            case 0xF2 -> ldh_a_pc();
            case 0xFA -> ld_a_pn16();

            default -> throw new RuntimeException("invalid opcode: " + opcode);
        }
    }

    /**
     * When opcode is prefixed with xCB
     * @param opcode the programme opcode
     */
    private void prefixedInstructionCall(final short opcode) {
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
    // r = register, so r8 = 8bit reg, r16 = 16bit. pr = the address a register points to
    // n = mem value at PC, n16 is the little endian word (handled in read/writeWord())
    // snake_casing for now since camelCase looks extremely bad for these names


    // --- LD 's ---

    private void ld_r8_r8(final char toRegister, final char fromRegister) {
        setr8(toRegister, getr8(fromRegister));

        totalMCycles += 1;
        PC += 1;
    }

    private void ld_r8_n8(final char register) {
        final short value = memory.readByte(pcAccess);
        setr8(register, value); // are we making sure we're not setting F?

        totalMCycles += 2;
        PC += 2;
    }

    private void ld_phl_r8(final char register) {
        memory.writeByte(HL, (short) getr8(register));

        totalMCycles += 2;
        PC += 1;
    }

    private void ld_r8_phl(final char register) {
        final int hlAddressValue = memory.readByte(HL);
        setr8(register, hlAddressValue);

        totalMCycles += 2;
        PC += 1;
    }

    private void ld_phl_n8() {
        final short value = memory.readByte(pcAccess);
        memory.writeByte(HL, value);

        totalMCycles += 3;
        PC += 2;
    }

    private void ldh_pc_a() {
        final short regCValue = (short) getr8('C');
        final int address = 0xFF00 | regCValue;
        memory.writeByte(address, (short)getr8('A'));

        totalMCycles += 2;
        PC += 1;
    }

    private void ldh_a_pc() {
        final short regCValue = (short) getr8('C'); // TODO: consider changing return type to short (on getr8)
        final int address = 0xFF00 | regCValue;
        setr8('A', memory.readByte(address));

        totalMCycles += 2;
        PC += 1;
    }

    private void ldh_pn16_a() {
        if (0xFF00 <= pcAccess && pcAccess <= 0xFFFF) {
            ld_pn16_a();
        }
        totalMCycles += 3; // TODO: should these be inside the if?? does the cycles/pc still increment if PC is not in a suitable place?
        PC += 2;
    }

    private void ldh_a_pn16() {
        if (0xFF00 <= pcAccess && pcAccess <= 0xFFFF) {
            ld_a_pn16();
        }
        totalMCycles += 3;
        PC += 2;
    }
    // [n] means we go to the address of the address that PC points to
    private void ld_pn16_a() {
        final int address = memory.readWord(pcAccess);
        memory.writeByte(address, (short) getr8('A'));
        totalMCycles += 4;
        PC += 3;
    }

    private void ld_a_pn16() {
        final int address = memory.readWord(pcAccess);
        setr8('A', memory.readByte(address));
        totalMCycles += 4;
        PC += 3;
    }


    private void ld_r16_n16(final String registers) {
        setr16(registers, memory.readWord(pcAccess));

        totalMCycles += 3;
        PC += 3;
    }

    // this loads a into the "byte pointed to by r16"
    // i'm guessing we get the mem address of r16's value?? Yes! then write A to it..
    private void ld_pr16_a(final String registers) {
        final short regAValue = (short)getr8('A');
        switch (registers) {
            case "BC" -> memory.writeByte(BC, regAValue);
            case "DE" -> memory.writeByte(DE, regAValue);
            case "HL+" -> {
                memory.writeByte(HL, regAValue);
                HL++;
            }
            case "HL-" -> {
                memory.writeByte(HL, regAValue);
                HL--;
            }
            default -> throw new RuntimeException("invalid register: " + registers + " for LD pr16,a");
        }
        totalMCycles += 2;
        PC += 1;
    }

    private void ld_a_pr16(final String registers) {
        switch (registers) {
            case "BC" -> setr8('A', memory.readByte(BC));
            case "DE" -> setr8('A', memory.readByte(DE));
            case "HL+" -> {
                setr8('A', memory.readByte(HL));
                HL++;
            }
            case "HL-" -> {
                setr8('A', memory.readByte(HL));
                HL--;
            }
            default -> throw new RuntimeException("invalid register: " + registers + " for LD a,pr16");
        }
        totalMCycles += 2;
        PC += 1;
    }


    // --- INC/DEC ---

    // inc/dec_r8 0x04/05/0C/0D to 0x34/35/3C/3D
    private void inc_r8(final char register) {
        short value = (short) getr8(register); // getr8 will throw if invalid register
        value++;
        setr8(register, value);

        setNFlag(false); // 0
        zFlagHFlag_8bit_overflow(value);

        totalMCycles += 1;
        PC += 1;
    }

    private void dec_r8(final char register) {
        short value = (short) getr8(register);
        value--;
        setr8(register, value);

        setNFlag(true); // 1
        zFlagHFlag_8bit_borrow(value);

        totalMCycles += 1;
        PC += 1;
    }

    private void inc_phl() {
        short addressValue = memory.readByte(HL);
        addressValue++;
        memory.writeByte(HL, addressValue);

        setNFlag(false);
        zFlagHFlag_8bit_overflow(addressValue);

        totalMCycles += 3;
        PC += 1;
    }

    private void dec_phl() {
        short addressValue = memory.readByte(HL);
        addressValue--;
        memory.writeByte(HL, addressValue);

        setNFlag(true);
        zFlagHFlag_8bit_borrow(addressValue);

        totalMCycles += 3;
        PC += 1;
    }

    // inc_r16 /dec, 0x03/0x0B ... 0x33/3B
    private void inc_r16(final String registers) {
        int registerValue = getr16(registers);
        registerValue++;
        setr16(registers, registerValue);

        totalMCycles += 2;
        PC += 1;
    }

    private void dec_r16(final String registers) {
        int registerValue = getr16(registers);
        registerValue--;
        setr16(registers, registerValue);

        totalMCycles += 2;
        PC += 1;
    }

    // --- ADD instructions ---

    private void add_a_r8(final char register) {
        final int aValue = getr8('A');
        final int r8Value = getr8(register);
        final int addedValue = aValue + r8Value;
        setr8('A', addedValue);
        zFlagHFlagCFlag_8bit_overflow('A', addedValue);
        setNFlag(false);

        totalMCycles += 1;
        PC += 1;
    }

    private void add_a_n8() {
        final short addressValue = memory.readByte(pcAccess);
        final short aValue = (short) getr8('A');
        final int addedValue = aValue + addressValue;
        setr8('A', addedValue);

        setNFlag(false);
        zFlagHFlagCFlag_8bit_overflow('A', addedValue);

        totalMCycles += 2;
        PC += 2;
    }

    private void add_a_phl() {
        final short addressValue = memory.readByte(HL); // TODO: basically dupelicate with add a,n8
        final short aValue = (short) getr8('A');
        final int addedValue = aValue + addressValue;
        setr8('A', addedValue);

        setNFlag(false);
        zFlagHFlagCFlag_8bit_overflow('A', addedValue);

        totalMCycles += 2;
        PC += 1;
    }

    private void add_sp_e8() {
        final short addressValue = memory.readByte(pcAccess); // TODO: e8 is signed!! not unsigned (not sure what we need to do)
        SP += addressValue;

        setZFlag(false);
        setNFlag(false);
        hFlagCFlag_8bit_overflow(SP);

        totalMCycles += 4;
        PC += 2;
    }

    private void add_hl_r16(final String registers) {
        switch (registers) {
            case "BC" -> {
                HL += BC;
                hFlagCFlag_16bit_overflow("HL", HL);
            }
            case "DE" -> {
                HL += DE;
                hFlagCFlag_16bit_overflow("HL", HL);
            }
            case "HL" -> {
                HL += HL;
                hFlagCFlag_16bit_overflow("HL", HL);
            }
            case "SP" -> {
                HL += SP;
                hFlagCFlag_16bit_overflow("HL", HL);
            }
            default -> throw new RuntimeException("invalid register pair: " + registers + " for ADD HL,r16");
        }
        setNFlag(false);
        totalMCycles += 2;
        PC += 1;
    }

    // ADC instructions

    private void adc_a_r8(final char register) {
        // carry flag simply holds the extra bit that was carried over. e.g xFF + 1 goes to x00, and carry
        // flag holds that extra bit. So when it's set, we need to add it back
        setr8('A', getr8('A') + 1); // 1 is the carry bit
        setCFlag(false);    // just used, so make it false
        add_a_r8(register); // then do the remaining addition (which does flag checks again + cycle/pc increments)
    }

    private void adc_a_phl() {
        setr8('A', getr8('A') + 1);
        setCFlag(false);
        add_a_phl();
    }

    private void adc_a_n8() {
        setr8('A', getr8('A') + 1);
        setCFlag(false);
        add_a_n8();
    }

    // SUB instructions


    private void sub_a_r8(final char register) { // for a case, z flag should always be true, and h/c flags to false
        final short aValue = (short) getr8('A'); // below code should still work correct for a case regardless
        final short r8Value = (short) getr8(register);
        final int subValue = aValue - r8Value;
        setr8('A', subValue);

        zFlagHFlag_8bit_borrow(subValue); // use this, since we can check c flag in this method
        setNFlag(true);
        if (r8Value > aValue) {
            setCFlag(true);
        }

        totalMCycles += 1;
        PC += 1;
    }

    private void sub_a_phl() {
        final short aValue = (short) getr8('A');
        final short addressValue = memory.readByte(HL);
        final int subValue = aValue - addressValue;
        setr8('A', subValue);

        zFlagHFlag_8bit_borrow(subValue);
        setNFlag(true);
        if (addressValue > aValue) {
            setCFlag(true);
        }

        totalMCycles += 2;
        PC += 1;
    }

    private void sub_a_n8() {
        final short aValue = (short) getr8('A');
        final short addressValue = memory.readByte(pcAccess);
        final int subValue = aValue - addressValue;
        setr8('A', subValue);

        zFlagHFlag_8bit_borrow(subValue);
        setNFlag(true);
        if (addressValue > aValue) {
            setCFlag(true);
        }

        totalMCycles += 2;
        PC += 2;
    }


    // SBC instructions

    private void sbc_a_r8(final char register) {
        setr8('A', getr8('A') - 1); // carry flag use
        setCFlag(false);
        sub_a_r8(register);
    }

    private void sbc_a_phl() {
        setr8('A', getr8('A') - 1); // carry flag use
        setCFlag(false);
        sub_a_phl();
    }

    private void sbc_a_n8() {
        setr8('A', getr8('A') - 1); // carry flag use
        setCFlag(false);
        sub_a_n8();
    }

    // ------ HELPER METHODS --------

    /**
     * 8 bit register writes (handles the bitwise operations)
     * @param register the register we want to write to
     * @param value the byte we want to write
     */
    private void setr8(final char register, final int value) {
        switch (register) {
            case 'A' -> AF = (0x00FF & AF) | (value << 8); // rewrites high byte
            case 'B' -> BC = (0x00FF & BC) | (value << 8);
            case 'C' -> BC = (0xFF00 & BC) | value; // rewrites low byte
            case 'D' -> DE = (0x00FF & DE) | (value << 8);
            case 'E' -> DE = (0xFF00 & DE) | value;
            case 'H' -> HL = (0x00FF & HL) | (value << 8);
            case 'L' -> HL = (0xFF00 & HL) | value;
            default -> throw new RuntimeException("invalid register: " + register + " or if F, SP, PC, we cannot set a value here.");
        }
    }

    /**
     * 8 bit register reads (handles the bitwise operations)
     * @param register the register we want to read
     * @return the byte value
     */
    private int getr8(final char register) {
        return switch (register) {
            case 'A' -> AF >> 8; // removes low byte
            case 'B' -> BC >> 8;
            case 'C' -> 0x00FF & BC;
            case 'D' -> DE >> 8;
            case 'E' -> 0x00FF & DE;
            case 'H' -> HL >> 8;
            case 'L' -> 0x00FF & HL;
            default -> throw new RuntimeException("invalid register: " + register + " or if F, SP, PC, we cannot set a value here.");
        };
    }

    // these are for F flag only (to avoid incorrectly setting/getting f's where we don't want to)
    private void setF(final int value) {
        AF = (0xFF00 & AF) | value; // rewrites low byte
    }

    private int getF() {
        return 0x00FF & AF;
    }

    /**
     * Setter for the 16bit register pairs.
     * Will reduce switch cases in code.
     * @param registers the register pair
     */
    private void setr16(final String registers, final int value) {
        switch (registers) {
            case "BC" -> BC = value;
            case "DE" -> DE = value;
            case "HL" -> HL = value;
            case "SP" -> SP = value;
            default -> throw new RuntimeException("invalid register pair: " + registers + " for R16");
        }
    }

    /**
     * Getter for 16bit register pairs.
     * Reduces switch cases in our code
     * @param registers the register pairs
     * @return the value of the selected valid pair
     */
    private int getr16(final String registers) {
        return switch(registers) {
            case "BC" -> BC;
            case "DE" -> DE;
            case "HL" -> HL;
            case "SP" -> SP;
            default -> throw new RuntimeException("invalid register pair: " + registers + " for R16");
        };
    }

    /**
     * Zero Flag, Accesses the 7th bit in F register (from AF) and sets it to 1 or 0
     */
    private void setZFlag(final boolean toOne) {
        final short regFValue = (short) getF();
        int zFlagSet = regFValue | 0b10000000; // keeps all other bits same but makes sure 7th is set
        if (!toOne) {
            zFlagSet = zFlagSet & 0b01111111; // otherwise set 7th bit to off (0)
        }
        setF(zFlagSet);
    }

    /**
     * Subtraction Flag, Accesses the 6th bit in F register (from AF) and sets it to 1 or 0
     */
    private void setNFlag(final boolean toOne) {
        final short regFValue = (short) getF();
        int nFlagSet = regFValue | 0b01000000;
        if (!toOne) {
            nFlagSet = nFlagSet & 0b10111111;
        }
        setF(nFlagSet);
    }

    /**
     * Half Carry Flag, Accesses the 5th bit in F register (from AF) and sets it to 1 or 0
     */
    private void setHFlag(final boolean toOne) {
        final short regFValue = (short) getF();
        int hFlagSet = regFValue | 0b00100000;
        if (!toOne) {
            hFlagSet = hFlagSet & 0b11011111;
        }
        setF(hFlagSet);
    }

    /**
     * CarryFlag, Accesses the 4th bit in F register (from AF) and sets it to 1 or 0
     */
    private void setCFlag(final boolean toOne) {
        final short regFValue = (short) getF();
        int cFlagSet = regFValue | 0b00010000;
        if (!toOne) {
            cFlagSet = cFlagSet & 0b11101111;
        }
        setF(cFlagSet);
    }

    // Flag set cases

    private void zFlagHFlagCFlag_8bit_overflow(final char register, final int value) {
        if (value == 0) {
            setZFlag(true);
        } else if (value > 0xFF) {
            setCFlag(true);
            setr8(register, 0); // reset to 0 so value remains correct
        } else if (value > 0xF) {
            setHFlag(true);
        }
    }

    private void zFlagHFlag_8bit_overflow(final int value) {
        if (value == 0) {
            setZFlag(true);
        } else if (value > 0xF) { // set if overflow from bit 3
            setHFlag(true);
        }
    }

    private void zFlagHFlag_8bit_borrow(final int value) {
        if (value == 0) {
            setZFlag(true);
        } else if (value <= 0xF) {
            setHFlag(true); // set if borrow from bit 4
        }                   // although <= 0xF could be wrong, but from 10000 - 1 (binary) we do borrow 4th bit
                            // == 1111 == 0xF
    }

    // this is only used by SP for now (which is 16 bits so doesn't make much sense and can't overflow?)
    private void hFlagCFlag_8bit_overflow(final int value) {
        if (value > 0xFF) {
            setCFlag(true);
        } else if (value > 0xF) {
            setHFlag(true);
        }
    }

    private void hFlagCFlag_16bit_overflow(final String registers, final int value) {
        if (value > 0xFFFF) {
            setCFlag(true);
            setr16(registers, 0); // reset since we reach unsigned 16bit limit
        } else if (value > 0xFFF) {
            setHFlag(true);
        }
    }

}
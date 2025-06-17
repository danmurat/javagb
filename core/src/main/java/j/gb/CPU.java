package j.gb;

/**
 * Represents the game boy CPU.
 * <br>
 * Will hold ALL the instructions (bit manipulations), and registers from the unit.
 */
public class CPU {
    // bit switches for the F register flags (7=z, 6=n, 5=h, 4=c)
    private static final int BIT_7_ON = 0b10000000;
    private static final int BIT_7_OFF = 0b01111111;
    private static final int BIT_6_ON = 0b01000000;
    private static final int BIT_6_OFF = 0b10111111;
    private static final int BIT_5_ON = 0b00100000;
    private static final int BIT_5_OFF = 0b11011111;
    private static final int BIT_4_ON = 0b00010000;
    private static final int BIT_4_OFF = 0b11101111;

    private static final int V_BLANK_SOURCE_ADDRESS = 0x40;
    private static final int STAT_SOURCE_ADDRESS = 0x48;
    private static final int TIMER_SOURCE_ADDRESS = 0x50;
    private static final int SERIAL_SOURCE_ADDRESS = 0x58;
    private static final int JOYPAD_SOURCE_ADDRESS = 0x60;

    private Memory memory;

    private int AF; // A = high, F = lo (Lower 8 bits (F) hold Flag information)
    private int BC;
    private int DE;
    private int HL;

    private int SP;
    private int PC;

    private boolean IME; // interrupt master enable flag (write only)
    // effect of IE is delayed by 1 instruction. So we use "save" the intention, and turn IME = true after next instr
    private boolean eiTurnImeOn;

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

    // DEBUGGING ONLY
    private int currentOpcode = 0;

    /**
     * Constructs..
     */
    public CPU(final Memory memory) {
        this.memory = memory;

        // addresses after boot rom
        AF = 0x01B0;
        BC = 0x0013;
        DE = 0x00D8;
        HL = 0x014D;
        SP = 0xFFFE;
        PC = 0x100;

        IME = false;
        eiTurnImeOn = false;

        totalMCycles = 0;
    }

    // FOR TESTING
    public void printSP() {
        System.out.println(Integer.toHexString(SP));
    }
    public void printPC() {
        System.out.print("PC=" + Integer.toHexString(PC));
    }
    public void printOP() {
        System.out.print(" OPCODE=" + Integer.toHexString(currentOpcode));
    }

    // GETTERS FOR TESTING
    public int getOP() {
        return currentOpcode;
    }
    public int get8bitReg(char reg) {
        return getr8(reg);
    }
    public int getFreg() {
        return getF();
    }
    public int getSP() {
        return SP;
    }
    public int getPC() {
        return PC;
    }
    public int[] getPCmem() { // returns pc,pc+1,pc+2 and pc+3
        int[] mem = new int[4];
        for (int i = 0; i < 4; i++) {
            mem[i] = memory.readByte(PC + i);
        }
        return mem;
    }
    public int getTotalMCycles() {
        return totalMCycles;
    }
    // once we've finished 1 second, the idea is we render the screen, then reset M cycles, so that we know when to stop
    // again on next frame
    public void resetTotalMCycles() {
        totalMCycles = 0;
    }

    /**
     * Main cpu execution method.
     * Reads opcode, finds and runs correct instruction.
     */
    public void executeInstruction() {
        // opcode is the value at the memory address specified by program counter
        // fetch
        if (!eiTurnImeOn) {
            if (IME) {
                interruptHandle(); // attempt to handle (redirects PC to handle event)
            }
            final short opcode = memory.readByte(PC);
            currentOpcode = opcode; // DEBUG STATEMENT (a global opcode)
            instructionCall(opcode);
        } else {
            /*if (IME) {
                interruptHandle(); // this shouldn't execute...
            }*/
            final short opcode = memory.readByte(PC);
            instructionCall(opcode);
            // this makes ei turn IME on AFTER the next instruction is finished
            IME = true;
            eiTurnImeOn = false;
        }

        /* TODO:
        Interrupts is a signal to the cpu to stop it's next execution. This is so that it can handle an
        event that has just occured (a keypress (for the game pad) in our case) before the next instruction.

        We will need to to a interrupt check in this method before (or after) we execute an instruction
         */
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
        switch (opcode) {
            case 0x00 -> nop();
            case 0x01 -> ld_r16_n16("BC"); // TODO: in future check this string passing doesn't affect performance too much
            case 0x02 -> ld_pr16_a("BC");
            case 0x03 -> inc_r16("BC");
            case 0x04 -> inc_r8('B');
            case 0x05 -> dec_r8('B');
            case 0x06 -> ld_r8_n8('B');
            case 0x07 -> rlca();
            case 0x08 -> ld_pn16_sp();
            case 0x09 -> add_hl_r16("BC");
            case 0x0A -> ld_a_pr16("BC");
            case 0x0B -> dec_r16("BC");
            case 0x0C -> inc_r8('C');
            case 0x0D -> dec_r8('C');
            case 0x0E -> ld_r8_n8('C');
            case 0x0F -> rrca();

            case 0x10 -> stop(); // TODO: implement this fully
            case 0x11 -> ld_r16_n16("DE");
            case 0x12 -> ld_pr16_a("DE");
            case 0x13 -> inc_r16("DE");
            case 0x14 -> inc_r8('D');
            case 0x15 -> dec_r8('D');
            case 0x16 -> ld_r8_n8('D');
            case 0x17 -> rla();
            case 0x18 -> jr_e8();
            case 0x19 -> add_hl_r16("DE");
            case 0x1A -> ld_a_pr16("DE");
            case 0x1B -> dec_r16("DE");
            case 0x1C -> inc_r8('E');
            case 0x1D -> dec_r8('E');
            case 0x1E -> ld_r8_n8('E');
            case 0x1F -> rra();

            case 0x20 -> jr_nz_e8();
            case 0x21 -> ld_r16_n16("HL");
            case 0x22 -> ld_pr16_a("HL+");
            case 0x23 -> inc_r16("HL");
            case 0x24 -> inc_r8('H');
            case 0x25 -> dec_r8('H');
            case 0x26 -> ld_r8_n8('H');
            case 0x27 -> daa();
            case 0x28 -> jr_z_e8();
            case 0x29 -> add_hl_r16("HL");
            case 0x2A -> ld_a_pr16("HL+");
            case 0x2B -> dec_r16("HL");
            case 0x2C -> inc_r8('L');
            case 0x2D -> dec_r8('L');
            case 0x2E -> ld_r8_n8('L');
            case 0x2F -> cpl();

            case 0x30 -> jr_nc_e8();
            case 0x31 -> ld_r16_n16("SP");
            case 0x32 -> ld_pr16_a("HL-");
            case 0x33 -> inc_r16("SP");
            case 0x34 -> inc_phl();
            case 0x35 -> dec_phl();
            case 0x36 -> ld_phl_n8();
            case 0x37 -> scf();
            case 0x38 -> jr_c_e8();
            case 0x39 -> add_hl_r16("SP");
            case 0x3A -> ld_a_pr16("HL-");
            case 0x3B -> dec_r16("SP");
            case 0x3C -> inc_r8('A');
            case 0x3D -> dec_r8('A');
            case 0x3E -> ld_r8_n8('A');
            case 0x3F -> ccf();

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

            case 0x70 -> ld_phl_r8('B');
            case 0x71 -> ld_phl_r8('C');
            case 0x72 -> ld_phl_r8('D');
            case 0x73 -> ld_phl_r8('E');
            case 0x74 -> ld_phl_r8('H');
            case 0x75 -> ld_phl_r8('L');
            case 0x76 -> halt();
            case 0x77 -> ld_phl_r8('A');
            case 0x78 -> ld_r8_r8('A', 'B');
            case 0x79 -> ld_r8_r8('A', 'C');
            case 0x7A -> ld_r8_r8('A', 'D');
            case 0x7B -> ld_r8_r8('A', 'E');
            case 0x7C -> ld_r8_r8('A', 'H');
            case 0x7D -> ld_r8_r8('A', 'L');
            case 0x7E -> ld_r8_phl('A');
            case 0x7F -> ld_r8_r8('A', 'A');

            case 0x80 -> add_a_r8('B', 0);
            case 0x81 -> add_a_r8('C', 0);
            case 0x82 -> add_a_r8('D', 0);
            case 0x83 -> add_a_r8('E', 0);
            case 0x84 -> add_a_r8('H', 0);
            case 0x85 -> add_a_r8('L', 0);
            case 0x86 -> add_a_phl(0);
            case 0x87 -> add_a_r8('A', 0);
            case 0x88 -> adc_a_r8('B');
            case 0x89 -> adc_a_r8('C');
            case 0x8A -> adc_a_r8('D');
            case 0x8B -> adc_a_r8('E');
            case 0x8C -> adc_a_r8('H');
            case 0x8D -> adc_a_r8('L');
            case 0x8E -> adc_a_phl();
            case 0x8F -> adc_a_r8('A');

            case 0x90 -> sub_a_r8('B', 0);
            case 0x91 -> sub_a_r8('C', 0);
            case 0x92 -> sub_a_r8('D', 0);
            case 0x93 -> sub_a_r8('E', 0);
            case 0x94 -> sub_a_r8('H', 0);
            case 0x95 -> sub_a_r8('L', 0);
            case 0x96 -> sub_a_phl(0);
            case 0x97 -> sub_a_r8('A', 0);
            case 0x98 -> sbc_a_r8('B');
            case 0x99 -> sbc_a_r8('C');
            case 0x9A -> sbc_a_r8('D');
            case 0x9B -> sbc_a_r8('E');
            case 0x9C -> sbc_a_r8('H');
            case 0x9D -> sbc_a_r8('L');
            case 0x9E -> sbc_a_phl();
            case 0x9F -> sbc_a_r8('A');

            case 0xA0 -> and_a_r8('B');
            case 0xA1 -> and_a_r8('C');
            case 0xA2 -> and_a_r8('D');
            case 0xA3 -> and_a_r8('E');
            case 0xA4 -> and_a_r8('H');
            case 0xA5 -> and_a_r8('L');
            case 0xA6 -> and_a_phl();
            case 0xA7 -> and_a_r8('A');
            case 0xA8 -> xor_a_r8('B');
            case 0xA9 -> xor_a_r8('C');
            case 0xAA -> xor_a_r8('D');
            case 0xAB -> xor_a_r8('E');
            case 0xAC -> xor_a_r8('H');
            case 0xAD -> xor_a_r8('L');
            case 0xAE -> xor_a_phl();
            case 0xAF -> xor_a_r8('A');

            case 0xB0 -> or_a_r8('B');
            case 0xB1 -> or_a_r8('C');
            case 0xB2 -> or_a_r8('D');
            case 0xB3 -> or_a_r8('E');
            case 0xB4 -> or_a_r8('H');
            case 0xB5 -> or_a_r8('L');
            case 0xB6 -> or_a_phl();
            case 0xB7 -> or_a_r8('A');
            case 0xB8 -> cp_a_r8('B');
            case 0xB9 -> cp_a_r8('C');
            case 0xBA -> cp_a_r8('D');
            case 0xBB -> cp_a_r8('E');
            case 0xBC -> cp_a_r8('H');
            case 0xBD -> cp_a_r8('L');
            case 0xBE -> cp_a_phl();
            case 0xBF -> cp_a_r8('A');

            case 0xC0 -> ret_nz();
            case 0xC1 -> pop_r16('B', 'C');
            case 0xC2 -> jp_nz_n16();
            case 0xC3 -> jp_n16();
            case 0xC4 -> call_nz_n16();
            case 0xC5 -> push_r16('B', 'C');
            case 0xC6 -> add_a_n8(0);
            case 0xC7 -> rst(0x00);
            case 0xC8 -> ret_z();
            case 0xC9 -> ret();
            case 0xCA -> jp_z_n16();
            case 0xCB -> prefixedInstructionCall(); // will execute the prefixed opcodes
            case 0xCC -> call_z_n16();
            case 0xCD -> call_n16();
            case 0xCE -> adc_a_n8();
            case 0xCF -> rst(0x08);

            case 0xD0 -> ret_nc();
            case 0xD1 -> pop_r16('D', 'E');
            case 0xD2 -> jp_nc_n16();
            case 0xD4 -> call_nc_n16();
            case 0xD5 -> push_r16('D', 'E');
            case 0xD6 -> sub_a_n8(0);
            case 0xD7 -> rst(0x10);
            case 0xD8 -> ret_c();
            case 0xD9 -> reti();
            case 0xDA -> jp_c_n16();
            case 0xDC -> call_c_n16();
            case 0xDE -> sbc_a_n8();
            case 0xDF -> rst(0x18);

            case 0xE0 -> ldh_pn16_a();
            case 0xE1 -> pop_r16('H', 'L');
            case 0xE2 -> ldh_pc_a();
            case 0xE5 -> push_r16('H', 'L');
            case 0xE6 -> and_a_n8();
            case 0xE7 -> rst(0x20);
            case 0xE8 -> add_sp_e8();
            case 0xE9 -> jp_hl();
            case 0xEA -> ld_pn16_a();
            case 0xEE -> xor_a_n8();
            case 0xEF -> rst(0x28);

            case 0xF0 -> ldh_a_pn16();
            case 0xF1 -> pop_af();
            case 0xF2 -> ldh_a_pc();
            case 0xF3 -> di();
            case 0xF5 -> push_r16('A', 'F');
            case 0xF6 -> or_a_n8();
            case 0xF7 -> rst(0x30);
            case 0xF8 -> ld_hl_spe8();
            case 0xF9 -> ld_sp_hl();
            case 0xFA -> ld_a_pn16();
            case 0xFB -> ei();
            case 0xFE -> cp_a_n8();
            case 0xFF -> rst(0x38);

            default -> throw new RuntimeException("invalid opcode: " + opcode);
        }
    }


    // TODO: shorten this!! lot of repeats
    private void interruptHandle() {
        totalMCycles += 2; // 2 cycles doing nothing
        // this instruction only runs if the IME is on
        // check which interrupt we are handling (hierarchical, we check vBlank first, and so on)
        if (memory.vBlankRequest() && memory.vBlankEnable()) {
            memory.vBlankRequestReset();
            IME = false;
            pushPCToStack();
            /*
            I wonder if our implementation is incorrect, because we are pushing the current PC and not the
            PC of the next instruction (the same issue we had with CALL).
             */
            PC = V_BLANK_SOURCE_ADDRESS;
        } else if (memory.lcdRequest() && memory.lcdEnable()) {
            memory.lcdRequestReset();
            IME = false;
            pushPCToStack();
            PC = STAT_SOURCE_ADDRESS;
        } else if (memory.timerRequest() && memory.timerEnable()) {
            memory.timerRequestReset();
            IME = false;
            pushPCToStack();
            PC = TIMER_SOURCE_ADDRESS;
        } else if (memory.serialRequest() && memory.serialEnable()) {
            memory.serialRequestReset();
            IME = false;
            pushPCToStack();
            PC = SERIAL_SOURCE_ADDRESS;
        } else if (memory.joypadRequest() && memory.joypadEnable()) {
            memory.joypadRequestReset();
            IME = false;
            pushPCToStack();
            PC = JOYPAD_SOURCE_ADDRESS;
        }
        /*
        So whatever the interrupt is, the cpu begins executing code programmed by the game when we
        jump to 0x60 for example. Once the interrupt is handled (as intended by the game) there will be
        a reti() instruction which brings us back to original exection.
         */
    }


    // --- INSTRUCTIONS ---
    // with help from https://rgbds.gbdev.io/docs/v0.9.1/gbz80.7#INSTRUCTION_REFERENCE
    // r = register, so r8 = 8bit reg, r16 = 16bit. pr = the address a register points to
    // n = mem value at PC, n16 is the little endian word (handled in read/writeWord())

    // --- LD 's ---

    private void ld_r8_r8(final char toRegister, final char fromRegister) {
        setr8(toRegister, getr8(fromRegister));

        totalMCycles += 1;
        PC += 1;
    }

    private void ld_r8_n8(final char register) {
        final short value = memory.readByte(PC + 1);
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
        final short value = memory.readByte(PC + 1);
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
        // this might be wrong
        // 'provided address is between ...
        // apparanlty, this isn't a case that we need to satisfy?? it should be written at xff00 + A value
        // (which won't be more than xff...)
        // essentially, this is the same as above (ldh [c] ..) but we get byte at PC+1 + 0xFF00
       /* if (0xFF00 <= (PC + 1) && (PC + 1) <= 0xFFFF) {
            ld_pn16_a();
        }*/
        final int address = 0xFF00 | memory.readByte(PC + 1);
        memory.writeByte(address, (short) getr8('A'));

        totalMCycles += 3; // TODO: should these be inside the if?? does the cycles/pc still increment if PC is not in a suitable place?
        PC += 2;
    }

    private void ldh_a_pn16() {
        /*if (0xFF00 <= (PC + 1) && (PC + 1) <= 0xFFFF) {
            ld_a_pn16();
        }*/
        final int address = 0xFF00 | memory.readByte(PC + 1);
        final short value = memory.readByte(address);
        setr8('A', value);

        totalMCycles += 3;
        PC += 2;
    }
    // [n] means we go to the address of the address that PC points to
    private void ld_pn16_a() {
        final int address = memory.readWord(PC + 1);
        memory.writeByte(address, (short) getr8('A'));
        totalMCycles += 4;
        PC += 3;
    }

    private void ld_a_pn16() {
        final int address = memory.readWord(PC + 1);
        setr8('A', memory.readByte(address));
        totalMCycles += 4;
        PC += 3;
    }


    private void ld_r16_n16(final String registers) {
        setr16(registers, memory.readWord(PC + 1));

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
                int value = HL + 1;
                if (value > 0xFFFF) { // overflow wrap around
                    value = value - 0x1000;
                }
                HL = value; // docs say no flags affected
            }
            case "HL-" -> {
                memory.writeByte(HL, regAValue);
                int value = HL - 1;
                if (value < 0) { // underflow wrap
                    value = value + 0x10000;
                }
                HL = value;
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
                int value = HL + 1;
                if (value > 0xFFFF) { // overflow wrap around
                    value = value - 0x1000;
                }
                HL = value; // docs say no flags affected
            }
            case "HL-" -> {
                setr8('A', memory.readByte(HL));
                int value = HL - 1;
                if (value < 0) { // underflow wrap
                    value = value + 0x10000;
                }
                HL = value;
            }
            default -> throw new RuntimeException("invalid register: " + registers + " for LD a,pr16");
        }
        totalMCycles += 2;
        PC += 1;
    }

    private void ld_pn16_sp() {
        final int address = memory.readWord(PC + 1);
        memory.writeWord(address, SP); // handles the low/high bytes

        totalMCycles += 5;
        PC += 3;
    }

    private void ld_sp_hl() {
        SP = HL;

        totalMCycles += 2;
        PC += 1;
    }

    // ADD the signed imm value to SP, then load to hl
    private void ld_hl_spe8() {
        final int immValue = memory.readByte(PC + 1);
        final int signedImmValue = memory.getTwosCompliment(immValue);
        // are we supposed to throw away the result??
        int result = SP + signedImmValue;

        setZFlag(false);
        setNFlag(false);
        spHFlagOverflow(SP, signedImmValue);
        spCFlagOverflow(SP, signedImmValue);

        // check for both 16bit over/underflow
        result = checkAndSetOverflowVal16bit(result);
        result = checkAndSetUnderflowVal16bit(result);
        setr16("HL", result);

        totalMCycles += 3;
        PC += 2;
    }


    // --- INC/DEC ---

    // inc/dec_r8 0x04/05/0C/0D to 0x34/35/3C/3D
    private void inc_r8(final char register) {
        final short value = (short) getr8(register); // getr8 will throw if invalid register
        final int incrementValue = 1;
        short result = (short) (value + incrementValue);
        setNFlag(false); // 0
        hFlag_8bit_overflow(value, incrementValue);
        // handle overflow (wrap around)
        result = (short) checkAndSetOverflowVal8bit(result);
        zFlag_8bit_overflow_or_borrow(result); // must be done after result is checked for overflow (applied in below methods too)

        setr8(register, result);

        totalMCycles += 1;
        PC += 1;
    }

    private void dec_r8(final char register) {
        final short value = (short) getr8(register);
        final int decValue = 1;
        short result = (short) (value - decValue);
        setNFlag(true); // 1
        hFlag_8bit_borrow(value, decValue);
        // handle underflow (wrap around)
        result = (short) checkAndSetUnderflowVal8bit(result);
        zFlag_8bit_overflow_or_borrow(result);

        setr8(register, result);

        totalMCycles += 1;
        PC += 1;
    }

    private void inc_phl() {
        final short addressValue = memory.readByte(HL);
        final int incrementValue = 1;
        int result = addressValue + incrementValue;
        setNFlag(false);
        hFlag_8bit_overflow(addressValue, incrementValue);
        // handle overflow
        result = checkAndSetOverflowVal8bit(result);
        zFlag_8bit_overflow_or_borrow(result);

        memory.writeByte(HL, (short) result);

        totalMCycles += 3;
        PC += 1;
    }

    private void dec_phl() {
        final short addressValue = memory.readByte(HL);
        final int decValue = 1;
        short result = (short) (addressValue - decValue);
        setNFlag(true);
        hFlag_8bit_borrow(addressValue, decValue);
        // handle underflow
        result = (short) checkAndSetUnderflowVal8bit(result);
        zFlag_8bit_overflow_or_borrow(result);

        memory.writeByte(HL, result);

        totalMCycles += 3;
        PC += 1;
    }

    // inc_r16 /dec, 0x03/0x0B ... 0x33/3B
    private void inc_r16(final String registers) {
        int registerValue = getr16(registers);
        registerValue++;
        registerValue = checkAndSetOverflowVal16bit(registerValue);
        setr16(registers, registerValue);

        totalMCycles += 2;
        PC += 1;
    }

    private void dec_r16(final String registers) {
        int registerValue = getr16(registers);
        registerValue--;
        registerValue = checkAndSetUnderflowVal16bit(registerValue);
        setr16(registers, registerValue);

        totalMCycles += 2;
        PC += 1;
    }

    // --- ADD instructions ---

    // NOTE: the carryFlag here is for when ADC calls and includes a carryFlag on. When we don't care about the carryFlag
    // (like in a regular ADD), it's just set to 0 so it doesn't interfere with the result. ADC will call this method
    // with carryFlag = 1 if it's on, and will include the correct calculations.
    private void add_a_r8(final char register, final int carryFlag) {
        final int aValue = getr8('A');
        final int r8Value = getr8(register);
        int result = aValue + r8Value + carryFlag;

        setNFlag(false);
        if (carryFlag == 0) {
            hFlag_8bit_overflow(aValue, r8Value);
        } else {
            hFlag_8bit_overflow(aValue, r8Value);
            if (!hFlagOn()) hFlag_8bit_overflow(aValue + r8Value, carryFlag);
        }
        cFlag_8bit_overflow(result);
        // handle carry (above just set flags..)
        result = (short) checkAndSetOverflowVal8bit(result);
        zFlag_8bit_overflow_or_borrow(result);

        setr8('A', result);

        totalMCycles += 1;
        PC += 1;
    }

    private void add_a_n8(final int carryFlag) {
        final short addressValue = memory.readByte(PC + 1);
        final short aValue = (short) getr8('A');
        int result = aValue + addressValue + carryFlag;

        setNFlag(false);
        if (carryFlag == 0) {
            hFlag_8bit_overflow(aValue, addressValue);
        } else {
            // we need to check again, because the cpu considers the addition 1 by 1.
            // so we need to check if either calculation by themselves cause a half carry
            hFlag_8bit_overflow(aValue, addressValue);
            // if h is still not on, do the last check (needed since the checks can reset to false)
            if (!hFlagOn()) hFlag_8bit_overflow(aValue + addressValue, carryFlag);
        }
        cFlag_8bit_overflow(result);

        result = (short) checkAndSetOverflowVal8bit(result);
        zFlag_8bit_overflow_or_borrow(result);

        setr8('A', result);

        totalMCycles += 2;
        PC += 2;
    }

    private void add_a_phl(final int carryFlag) {
        final short addressValue = memory.readByte(HL); // TODO: basically dupelicate with add a,n8
        final short aValue = (short) getr8('A');
        int result = aValue + addressValue + carryFlag;

        setNFlag(false);
        if (carryFlag == 0) {
            hFlag_8bit_overflow(aValue, addressValue);
        } else {
            hFlag_8bit_overflow(aValue, addressValue);
            if (!hFlagOn()) hFlag_8bit_overflow(aValue + addressValue, carryFlag);
        }
        cFlag_8bit_overflow(result);

        result = (short) checkAndSetOverflowVal8bit(result);
        zFlag_8bit_overflow_or_borrow(result);

        setr8('A', result);

        totalMCycles += 2;
        PC += 1;
    }

    private void add_sp_e8() {
        final int originalSPVal = SP;
        final short addressValue = memory.readByte(PC + 1);
        // two's compliment, like in JR Z instr
        final short signedVal = (short) memory.getTwosCompliment(addressValue);
        final int result = SP + signedVal;

        SP = result;

        setZFlag(false);
        setNFlag(false);
        spHFlagOverflow(originalSPVal, signedVal);
        spCFlagOverflow(originalSPVal, signedVal);

        totalMCycles += 4;
        PC += 2;
    }

    private void add_hl_r16(final String registers) {
        final int initialHLval = HL;
        switch (registers) {
            case "BC" -> {
                HL += BC;
                hFlag_16bit_overflow(initialHLval, BC);
                if (cFlag_16bit_overflow(HL)) {
                    HL = checkAndSetOverflowVal16bit(HL);
                }
            }
            case "DE" -> {
                HL += DE;
                hFlag_16bit_overflow(initialHLval, DE);
                if (cFlag_16bit_overflow(HL)) {
                    HL = checkAndSetOverflowVal16bit(HL);
                }
            }
            case "HL" -> {
                HL += HL;
                hFlag_16bit_overflow(initialHLval, initialHLval);
                if (cFlag_16bit_overflow(HL)) {
                    HL = checkAndSetOverflowVal16bit(HL);
                }
            }
            case "SP" -> {
                HL += SP;
                hFlag_16bit_overflow(initialHLval, SP);
                if (cFlag_16bit_overflow(HL)) {
                    HL = checkAndSetOverflowVal16bit(HL);
                }
            }
            default -> throw new RuntimeException("invalid register pair: " + registers + " for ADD HL,r16");
        }
        setNFlag(false);
        totalMCycles += 2;
        PC += 1;
    }

    // ADC instructions (include carry flag addition, then just calls original add's to finish)

    private void adc_a_r8(final char register) {
        final int carryFlagValue = cFlagOn() ? 1 : 0;
        add_a_r8(register, carryFlagValue); // then do the remaining addition (which does flag checks again + cycle/pc increments)
    }

    private void adc_a_phl() {
        final int carryFlagValue = cFlagOn() ? 1 : 0;
        add_a_phl(carryFlagValue);
    }

    private void adc_a_n8() {
        final int carryFlagValue = cFlagOn() ? 1 : 0;
        add_a_n8(carryFlagValue);
    }

    // SUB instructions

    // the same carryFlag reason in ADD and ADC applies here also. Will be 0 for SUB, unaffecting the result, but
    // 1 if we call SBC and carryFlag is on
    private void sub_a_r8(final char register, final int carryFlag) { // for a case, z flag should always be true, and h/c flags to false
        final short aValue = (short) getr8('A'); // below code should still work correct for a case regardless
        final short r8Value = (short) getr8(register);
        int result = aValue - r8Value - carryFlag;

        setNFlag(true);
        if (carryFlag == 0) {
            hFlag_8bit_borrow(aValue, r8Value);
        } else {
            hFlag_8bit_borrow(aValue, r8Value);
            if (!hFlagOn()) hFlag_8bit_borrow(aValue - r8Value, carryFlag);
        }
        cFlag_8bit_borrow(result);

        result = checkAndSetUnderflowVal8bit(result);
        zFlag_8bit_overflow_or_borrow(result);

        setr8('A', result);

        totalMCycles += 1;
        PC += 1;
    }

    private void sub_a_phl(final int carryFlag) {
        final short aValue = (short) getr8('A');
        final short addressValue = memory.readByte(HL);
        int result = aValue - addressValue - carryFlag;

        setNFlag(true);
        if (carryFlag == 0) {
            hFlag_8bit_borrow(aValue, addressValue);
        } else {
            hFlag_8bit_borrow(aValue, addressValue);
            if (!hFlagOn()) hFlag_8bit_borrow(aValue - addressValue, carryFlag);
        }
        cFlag_8bit_borrow(result);

        result = checkAndSetUnderflowVal8bit(result);
        zFlag_8bit_overflow_or_borrow(result);

        setr8('A', result);

        totalMCycles += 2;
        PC += 1;
    }

    private void sub_a_n8(final int carryFlag) {
        final short aValue = (short) getr8('A');
        final short addressValue = memory.readByte(PC + 1);
        int result = aValue - addressValue - carryFlag;

        setNFlag(true);
        if (carryFlag == 0) {
            hFlag_8bit_borrow(aValue, addressValue);
        } else {
            hFlag_8bit_borrow(aValue, addressValue);
            if (!hFlagOn()) hFlag_8bit_borrow(aValue - addressValue, carryFlag);
        }
        cFlag_8bit_borrow(result);

        result = checkAndSetUnderflowVal8bit(result);
        zFlag_8bit_overflow_or_borrow(result);

        setr8('A', result);

        totalMCycles += 2;
        PC += 2;
    }


    // SBC instructions

    private void sbc_a_r8(final char register) {
        final int carryFlagValue = cFlagOn() ? 1 : 0;
        sub_a_r8(register, carryFlagValue);
    }

    private void sbc_a_phl() {
        final int carryFlagValue = cFlagOn() ? 1 : 0;
        sub_a_phl(carryFlagValue);
    }

    private void sbc_a_n8() {
        final int carryFlagValue = cFlagOn() ? 1 : 0;
        sub_a_n8(carryFlagValue);
    }

    // Bitwise instructions (AND, OR, XOR, CPL)

    // is it worth it to reduce these methods to 1 (since they are almost identical)?
    private void and_a_r8(final char register) {
        final short aValue = (short) getr8('A');
        final short r8Value = (short) getr8(register);
        final int andResult = aValue & r8Value;
        setr8('A', andResult);

        andFlagSets(andResult);

        totalMCycles += 1;
        PC += 1;
    }

    private void and_a_phl() {
        final short aValue = (short) getr8('A');
        final short addressValue = memory.readByte(HL);
        final int andResult = aValue & addressValue;
        setr8('A', andResult);

        andFlagSets(andResult);

        totalMCycles += 2;
        PC += 1;
    }

    private void and_a_n8() {
        final short aValue = (short) getr8('A');
        final short addressValue = memory.readByte(PC + 1);
        final int andResult = aValue & addressValue;
        setr8('A', andResult);

        andFlagSets(andResult);

        totalMCycles += 2;
        PC += 2;
    }

    private void xor_a_r8(final char register) {
        final short aValue = (short) getr8('A');
        final short r8Value = (short) getr8(register);
        final int xorResult = aValue ^ r8Value;
        setr8('A', xorResult);

        xorFlagSets(xorResult);

        totalMCycles += 1;
        PC += 1;
    }

    private void xor_a_phl() {
        final short aValue = (short) getr8('A');
        final short addressValue = memory.readByte(HL);
        final int xorResult = aValue ^ addressValue;
        setr8('A', xorResult);

        xorFlagSets(xorResult);

        totalMCycles += 2;
        PC += 1;
    }

    private void xor_a_n8() {
        final short aValue = (short) getr8('A');
        final short addressValue = memory.readByte(PC + 1);
        final int xorResult = aValue ^ addressValue;
        setr8('A', xorResult);

        xorFlagSets(xorResult);

        totalMCycles += 2;
        PC += 2;
    }

    private void or_a_r8(final char register) {
        final short aValue = (short) getr8('A');
        final short r8Value = (short) getr8(register);
        final int orResult = aValue | r8Value;
        setr8('A', orResult);

        // uses the same flags
        xorFlagSets(orResult);

        totalMCycles += 1;
        PC += 1;
    }

    private void or_a_phl() {
        final short aValue = (short) getr8('A');
        final short addressValue = memory.readByte(HL);
        final int orResult = aValue | addressValue;
        setr8('A', orResult);

        xorFlagSets(orResult);

        totalMCycles += 2;
        PC += 1;
    }

    private void or_a_n8() {
        final short aValue = (short) getr8('A');
        final short addressValue = memory.readByte(PC + 1);
        final int orResult = aValue | addressValue;
        setr8('A', orResult);

        xorFlagSets(orResult);

        totalMCycles += 2;
        PC += 2;
    }

    // complement accumulator (NOT A)
    private void cpl() {
        final int notARegValue = ~getr8('A');
        setr8('A', notARegValue);

        setNFlag(true);
        setHFlag(true);

        totalMCycles += 1;
        PC += 1;
    }


    // CP instructions

    // does the exact same thing as SUB but doesn't save the result
    private void cp_a_r8(final char register) {
        final short aValue = (short) getr8('A');
        final short r8Value = (short) getr8(register);
        final int result = aValue - r8Value;

        zFlag_8bit_overflow_or_borrow(result);
        setNFlag(true);
        hFlag_8bit_borrow(aValue, r8Value);
        setCFlag(r8Value > aValue);

        totalMCycles += 1;
        PC += 1;
    }

    private void cp_a_phl() {
        final short aValue = (short) getr8('A');
        final short addressValue = memory.readByte(HL);
        final int result = aValue - addressValue;

        zFlag_8bit_overflow_or_borrow(result);
        setNFlag(true);
        hFlag_8bit_borrow(aValue, addressValue);
        setCFlag(addressValue > aValue);

        totalMCycles += 2;
        PC += 1;
    }

    private void cp_a_n8() {
        final short aValue = (short) getr8('A');
        final short addressValue = memory.readByte(PC + 1);
        final int result = aValue - addressValue;

        zFlag_8bit_overflow_or_borrow(result);
        setNFlag(true);
        hFlag_8bit_borrow(aValue, addressValue);
        setCFlag(addressValue > aValue);

        totalMCycles += 2;
        PC += 2;
    }


    // PUSH/POP instructions

    // we already have flags set inside AF, so no need to do anything else for PUSH AF
    private void push_r16(final char highRegister, final char lowRegister) {
        SP--;
        memory.writeByte(SP, (short) getr8(highRegister));
        SP--;
        if (lowRegister == 'F') {
            memory.writeByte(SP, (short) getF());
        } else {
            memory.writeByte(SP, (short) getr8(lowRegister));
        }

        totalMCycles += 4;
        PC += 1;
    }

    private void pop_r16(final char highRegister, final char lowRegister) {
        final short firstSPAddressValue = memory.readByte(SP);
        if (lowRegister == 'F') {
            setF(firstSPAddressValue);
        } else {
            setr8(lowRegister, firstSPAddressValue);
        }
        SP++;

        final short secondSPAddressValue = memory.readByte(SP);
        setr8(highRegister, secondSPAddressValue);
        SP++;

        totalMCycles += 3;
        PC += 1;
    }

    // tbh this can be kept in pop_r16...
    private void pop_af() {
        pop_r16('A', 'F');
        setF(getF() & 0b11110000); // throw away lower meaningless values (we only care about flags held in upper nibble)
    }

    // JP instructions (jump)

    private void jp_n16() {
        PC = memory.readWord(PC + 1); // pc becomes the value of the immediate 2 address' values

        totalMCycles += 4; // no pc increments
    }

    private void jp_z_n16() {
        if (zFlagOn()) {
            jp_n16();
        } else {
            totalMCycles += 3; // 1 less cycle when condition not met
            PC += 3;
        }
    }

    private void jp_c_n16() {
        if (cFlagOn()) {
            jp_n16();
        } else {
            totalMCycles += 3; // 1 less cycle when condition not met
            PC += 3;
        }
    }

    private void jp_nz_n16() {
        if (!zFlagOn()) {
            jp_n16();
        } else {
            totalMCycles += 3;
            PC += 3;
        }
    }

    private void jp_nc_n16() {
        if (!cFlagOn()) {
            jp_n16();
        } else {
            totalMCycles += 3;
            PC += 3;
        }
    }

    private void jp_hl() {
        PC = HL;

        totalMCycles += 1;
        //PC += 1; // should we be doing this?
    }

    // JR instructions (Relative jump)

    // e8 is signed (so we can jump forwards 128 or backwards 127).
    private void jr_e8() {
        final int followingAddr = PC + 2; // addr we end up if offset is 0 anyway (next instr)
        final int offsetVal = memory.readByte(PC + 1);

        // twos compliment offset
        final int offset = memory.getTwosCompliment(offsetVal);

        PC = followingAddr + offset;

        totalMCycles += 3;
        // comment out for now, because we already change PC to + 2 regardless
        //PC += 2; // without incrementing, we get into inf loop
    }

    private void jr_z_e8() {
        if (zFlagOn()) {
            jr_e8();
        } else {
            totalMCycles += 2;
            PC += 2;
        }
    }

    private void jr_c_e8() {
        if (cFlagOn()) {
            jr_e8();
        } else {
            totalMCycles += 2;
            PC += 2;
        }
    }

    // NZ means if Z is NOT set... not n and z are set
    private void jr_nz_e8() {
        if (!zFlagOn()) {
            jr_e8();
        } else {
            totalMCycles += 2;
            PC += 2;
        }
    }

    private void jr_nc_e8() {
        if (!cFlagOn()) {
            jr_e8();
        } else {
            totalMCycles += 2;
            PC += 2;
        }
    }

    // RET instructions

    // return from subroutine (basically a pop PC)
    private void ret() {
        final short firstSPAddressValue = memory.readByte(SP);
        final int pcLowByte = firstSPAddressValue;
        SP++; // guess we only need to increment once?

        final short secondSPAddressValue = memory.readByte(SP);
        final int pcHighByte = secondSPAddressValue;
        SP++;

        PC = (pcHighByte << 8) | pcLowByte;

        totalMCycles += 4;
        // no PC increment since we return to original pc
    }

    private void ret_z() {
        if (zFlagOn()) {
            ret();
            totalMCycles += 1; // an extra cycle is set (so 5 in total)
        } else {
            totalMCycles += 2;
            PC += 1;
        }
    }

    private void ret_c() {
        if (cFlagOn()) {
            ret();
            totalMCycles += 1;
        } else {
            totalMCycles += 2;
            PC += 1;
        }
    }

    private void ret_nz() {
        if (!zFlagOn()) {
            ret();
            totalMCycles += 1;
        } else {
            totalMCycles += 2;
            PC += 1;
        }
    }

    private void ret_nc() {
        if (!cFlagOn()) {
            ret();
            totalMCycles += 1;
        } else {
            totalMCycles += 2;
            PC += 1;
        }
    }

    public void reti() {
        IME = true;
        ret();
    }


    // CALL instructions

    /**
     * CALL pushes the next instruction's PC to the stack for us to return to later (with RET).
     * Once pushed, we jump to the address held in PC+1 (next to us)
     */
    private void call_n16() {
        final int nextInstrAddr = PC + 3; // CALL is 3 bytes long
        final int highByte = nextInstrAddr >> 8;
        final int lowByte = nextInstrAddr & 0xFF;

        SP--;
        memory.writeByte(SP, (short) highByte);
        SP--;
        memory.writeByte(SP, (short) lowByte);

        PC = memory.readWord(PC + 1); // then implicit jp n16

        totalMCycles += 6; // no PC increment
    }

    private void call_z_n16() {
        if (zFlagOn()) {
            call_n16();
        } else {
            totalMCycles += 3;
            PC += 3;
        }
    }

    private void call_c_n16() {
        if (cFlagOn()) {
            call_n16();
        } else {
            totalMCycles += 3;
            PC += 3;
        }
    }

    private void call_nz_n16() {
        if (!zFlagOn()) {
            call_n16();
        } else {
            totalMCycles += 3;
            PC += 3;
        }
    }

    private void call_nc_n16() {
        if (!cFlagOn()) {
            call_n16();
        } else {
            totalMCycles += 3;
            PC += 3;
        }
    }

    // RST instruction

    // shorter + faster equivalent to CALL.
    // push return addr (pc+1 in this case)
    // destination = imm word (pc+1 & pc+2) + VEC addr
    private void rst(final int vecAddr) {
        final int immAddr = PC + 1;
        final int pcHighByte = immAddr >> 8;
        final int pcLowByte = immAddr & 0xFF;
        SP--;
        memory.writeByte(SP, (short) pcHighByte);
        SP--;
        memory.writeByte(SP, (short) pcLowByte);

        final int destAddr = memory.readWord(PC + 1) + vecAddr;
        PC = destAddr;

        totalMCycles += 4;
    }

    // Rotation instructions

    private void rla() { // this is through the carry flag, so whatever is shifted out comes back
        final int aRegValue = getr8('A');
        final int rotatedValue = rotateLeftThroughC(aRegValue);

        final int cFlagValue = (rotatedValue & 0b100000000) >> 8; // 9th bit
        final int newARegValue = rotatedValue & 0xFF; // first 8 bits

        setr8('A', newARegValue);
        rotationAFlagSets(cFlagValue);

        totalMCycles += 1;
        PC += 1;
    }

    private void rra() { // through carry
        final int aRegValue = getr8('A');
        final int rotatedValue = rotateRightThroughC(aRegValue);

        final int cFlagValue = rotatedValue & 0x01; // first bit
        final int newARegValue = rotatedValue >> 1;

        setr8('A', newARegValue);
        rotationAFlagSets(cFlagValue);

        totalMCycles += 1;
        PC += 1;
    }

    private void rlca() { // rotate left (not through carry)
        final int aRegValue = getr8('A');
        final int leftRotateResult = rotateLeft(aRegValue);

        final int cFlagValue = leftRotateResult >> 8; // keep 9th bit (the c flag value) as a single bit
        final int newARegValue = leftRotateResult & 0xFF; // keep only first 8 bits
        setr8('A', newARegValue);

        rotationAFlagSets(cFlagValue);

        totalMCycles += 1;
        PC += 1;
    }

    private void rrca() { // rotate right
        final int aRegValue = getr8('A');
        final int rightRotateResult = rotateRight(aRegValue);

        final int cFlagValue = rightRotateResult & 0x01; // keep 1st bit
        final int newARegValue = rightRotateResult >> 1; // removes the cValue so we just keep aReg
        setr8('A', newARegValue);

        rotationAFlagSets(cFlagValue);

        totalMCycles += 1;
        PC += 1;
    }

    // carry flag instructions

    private void scf() {
        setNFlag(false);
        setHFlag(false);
        setCFlag(true); // simply set to true

        totalMCycles += 1;
        PC += 1;
    }

    private void ccf() {
        setNFlag(false);
        setHFlag(false);
        // compliment, so we invert whatever the current result is
        if (cFlagOn()) {
            setCFlag(false);
        } else {
            setCFlag(true);
        }

        totalMCycles += 1;
        PC += 1;
    }


    // Interrupt related instructions

    // TODO
    private void halt() {
        boolean pendingInterrupts = (memory.getIF() & memory.getIE()) != 0;
        // Enters low power state until interrupt occurs
        if (IME) {
            interruptHandle(); // TODO: not 100% sure this is correct
        } else if (!IME && !pendingInterrupts) {
            // the low power state (we wait until interrupt happens)
            while (!pendingInterrupts) {}
        } else if (!IME && pendingInterrupts) {
            // TODO
            // potentially implement the HALT bug here? PC byte is read twice (since bug doesn't increment PC)
        }

        PC += 1;
    }

    // disable interrupts
    private void di() {
        IME = false;

        totalMCycles += 1;
        PC += 1;
    }

    // enable interrupts
    private void ei() {
        eiTurnImeOn = true;

        totalMCycles += 1;
        PC += 1;
    }

    // Miscellaneous instructions

    /**
     * Bigger comment here since this instruction is a bit tricky.
     * DAA (Decimal Adjust Acumulator) uses BCD (Binary Coded Decimal) arithmetic. BCD treats each nibble as a decimal,
     * 0-9 (some binary value that represents 0-9). Any nibble representing 10,11, etc is INVALID.
     * So BCD would represent 59 with upperNibble=5 and lowerNibble=9.
     * When we do arithmetic, the result can change the values of those nibbles and could fall out of range (> 10),
     * and so we need to adjust as shown below. 0x6/0x60 will adjust the value to skip over the 6 invalid states
     * in binary/hex 0xA-0xF (so it works just like decimal, 9 + 1 = 9 becomes 0, carry next value to left = 10.
     * <br><br>
     * Example:
     * 0111 0101 = 117 (as regular binary value)
     * BCD treats these as 2 seperate nibbles and counts 0-9 for them
     * 0111 = 7, 0101 = 5 => 0111 0101 = 75 (in BCD)
     * <br><br>
     * DAA adjusts the RESULT of some BCD operation that has happened! Sometimes these operations can result in values
     * not being in BCD range (0-99), and thus needs adjusting by DAA.
     * <br>
     * 0x20 - 0x13 = 0x0D. This is not a BCD because the first digit > 9 (not decimal), and we had to borrow from the
     * second digit. So we subtract 6 from it to get the valid BCD value = 0x07. Because 20-13 = 7!
     * <br>
     * The same applies for addition. 0x92 + 0x13 = 0xA5, but 0xA5 is not BCD. Since result > 0x99, we add upper nibble
     * by 6 to give = 0x05 (92 + 13 = 05 when wrapping the overflow around). This in turn sets the carry flag on because
     * we've overflowed.
     * <br>
     * This also works for when a previous addition has actually overflown. Take 0x99 + 0x90 = 0x129. This > 0xFF so has
     * overflown and we wrap this around to 0x29 (with a carry flag on). 99 + 90 = 189, but > 99 so = 89 with a carry.
     * Since the carry flag is on, we add 0x6 to the upper nibble, 0x29 becomes 0x89 (just like the decimal).
     * <br><br>
     * I had a huge issue with figuring out how to set the carry flag here. https://rgbds.gbdev.io/docs/v0.9.1/gbz80.7#DAA
     * indicated to set or reset C depending on the operation. It turns out however, you don't reset.
     * <br>
     * Because the result of the previous operation should still stand, and we are just converting, we don't turn the
     * carry flag off.
     * <br>
     * When subtracting, there's no need to set the carry to on at all, because the applying the adjustment will never
     * result in an underflow. If a previous BCD subtraction resulted in an underflow (c flag on) then - 0x60 never underflows
     * again since the wrapped value will always be > 0x60. (0x00 - 0x90) "bcd won't go > 9", the 9 wraps back to 0x70.
     * <br>
     * You might think, well what about the h flag? The same concept as above applies. The result always at least 0x7.
     * Take 0x10 - 0x09 = 0x07. We've borrowed 1 from 0x10. So 9 - 0 = F all the way to 7. Since we have a half carry,
     * 0x7 - 0x6 = 0x1. This doesn't underflow. 9 is the max and 0 is the min, it's not possible to underflow.
     * <br><br>
     * When adding, there's only 1 case where we should set carry on. That's if the result was > 0x99. If A is more than this,
     * we know we need to add that value by 0x60 to get the BCD result. This will overflow A, so we must set it.
     */
    private void daa() { // decimal adjust accumulator
        int adjustment = 0;
        if (nFlagOn()) {
            if (hFlagOn()) adjustment += 0x6;  // h flag is for nibble. If on, then the result is over 0x9 (dec) we must sub by 0x6
            if (cFlagOn()) adjustment += 0x60; // same reasoning applies here
            final int aRegValue = getr8('A');
            int newAValue = aRegValue - adjustment;
            newAValue = checkAndSetUnderflowVal8bit(newAValue);
            setr8('A', newAValue);
        } else {
            final int aRegValue = getr8('A');
            if (hFlagOn() || (aRegValue & 0xF) > 0x9) adjustment += 0x6;
            if (cFlagOn() || aRegValue > 0x99) {
                adjustment += 0x60;
                // if cFlag was not on, make sure we turn on since the 0x60 will cause the overflow
                setCFlag(true);
            }
            int newAValue = aRegValue + adjustment;
            newAValue = checkAndSetOverflowVal8bit(newAValue);
            setr8('A', newAValue);
        }

        setZFlag(getr8('A') == 0);
        setHFlag(false); // only DAA uses half carry flag, so just sets to false (nothing else needs it)
        // c flag should already be set in elif

        totalMCycles += 1;
        PC += 1;
    }

    private void nop() { // no operation
        totalMCycles += 1;
        PC += 1;
    }

    private void stop() {
        // TODO: implement this when we want to add GameBoy colour support. (no rom uses this on GB)
        /*Enter CPU very low power mode. Also used to switch between GBC double speed and normal speed CPU modes.

          The exact behavior of this instruction is fragile and may interpret its second byte as a separate
          instruction (see the Pan Docs), which is why rgbasm(1) allows explicitly specifying the second byte
          (STOP n8) to override the default of $00 (a NOP instruction).*/
    }

    // --- CB PREFIXED INSTRUCTIONS ---

    /**
     * Run different set of instructions when opcode is prefixed with xCB.
     * All of these are instructions for 8bit shift/rotate/bitwise..
     */
    private void prefixedInstructionCall() {
        // need to access next byte (first was xCB)
        final short opcode = memory.readByte(PC + 1);

        switch (opcode) {
            case 0x00 -> rlc_r8('B');
            case 0x01 -> rlc_r8('C');
            case 0x02 -> rlc_r8('D');
            case 0x03 -> rlc_r8('E');
            case 0x04 -> rlc_r8('H');
            case 0x05 -> rlc_r8('L');
            case 0x06 -> rlc_phl();
            case 0x07 -> rlc_r8('A');
            case 0x08 -> rrc_r8('B');
            case 0x09 -> rrc_r8('C');
            case 0x0A -> rrc_r8('D');
            case 0x0B -> rrc_r8('E');
            case 0x0C -> rrc_r8('H');
            case 0x0D -> rrc_r8('L');
            case 0x0E -> rrc_phl();
            case 0x0F -> rrc_r8('A');

            case 0x10 -> rl_r8('B');
            case 0x11 -> rl_r8('C');
            case 0x12 -> rl_r8('D');
            case 0x13 -> rl_r8('E');
            case 0x14 -> rl_r8('H');
            case 0x15 -> rl_r8('L');
            case 0x16 -> rl_phl();
            case 0x17 -> rl_r8('A');
            case 0x18 -> rr_r8('B');
            case 0x19 -> rr_r8('C');
            case 0x1A -> rr_r8('D');
            case 0x1B -> rr_r8('E');
            case 0x1C -> rr_r8('H');
            case 0x1D -> rr_r8('L');
            case 0x1E -> rr_phl();
            case 0x1F -> rr_r8('A');

            case 0x20 -> sla_r8('B');
            case 0x21 -> sla_r8('C');
            case 0x22 -> sla_r8('D');
            case 0x23 -> sla_r8('E');
            case 0x24 -> sla_r8('H');
            case 0x25 -> sla_r8('L');
            case 0x26 -> sla_phl();
            case 0x27 -> sla_r8('A');
            case 0x28 -> sra_r8('B');
            case 0x29 -> sra_r8('C');
            case 0x2A -> sra_r8('D');
            case 0x2B -> sra_r8('E');
            case 0x2C -> sra_r8('H');
            case 0x2D -> sra_r8('L');
            case 0x2E -> sra_phl();
            case 0x2F -> sra_r8('A');

            case 0x30 -> swap_r8('B');
            case 0x31 -> swap_r8('C');
            case 0x32 -> swap_r8('D');
            case 0x33 -> swap_r8('E');
            case 0x34 -> swap_r8('H');
            case 0x35 -> swap_r8('L');
            case 0x36 -> swap_phl();
            case 0x37 -> swap_r8('A');
            case 0x38 -> srl_r8('B');
            case 0x39 -> srl_r8('C');
            case 0x3A -> srl_r8('D');
            case 0x3B -> srl_r8('E');
            case 0x3C -> srl_r8('H');
            case 0x3D -> srl_r8('L');
            case 0x3E -> srl_phl();
            case 0x3F -> srl_r8('A');

            case 0x40 -> bit_u3_r8(0, 'B');
            case 0x41 -> bit_u3_r8(0, 'C');
            case 0x42 -> bit_u3_r8(0, 'D');
            case 0x43 -> bit_u3_r8(0, 'E');
            case 0x44 -> bit_u3_r8(0, 'H');
            case 0x45 -> bit_u3_r8(0, 'L');
            case 0x46 -> bit_u3_phl(0);
            case 0x47 -> bit_u3_r8(0, 'A');
            case 0x48 -> bit_u3_r8(1, 'B');
            case 0x49 -> bit_u3_r8(1, 'C');
            case 0x4A -> bit_u3_r8(1, 'D');
            case 0x4B -> bit_u3_r8(1, 'E');
            case 0x4C -> bit_u3_r8(1, 'H');
            case 0x4D -> bit_u3_r8(1, 'L');
            case 0x4E -> bit_u3_phl(1);
            case 0x4F -> bit_u3_r8(1, 'A');

            case 0x50 -> bit_u3_r8(2, 'B');
            case 0x51 -> bit_u3_r8(2, 'C');
            case 0x52 -> bit_u3_r8(2, 'D');
            case 0x53 -> bit_u3_r8(2, 'E');
            case 0x54 -> bit_u3_r8(2, 'H');
            case 0x55 -> bit_u3_r8(2, 'L');
            case 0x56 -> bit_u3_phl(2);
            case 0x57 -> bit_u3_r8(2, 'A');
            case 0x58 -> bit_u3_r8(3, 'B');
            case 0x59 -> bit_u3_r8(3, 'C');
            case 0x5A -> bit_u3_r8(3, 'D');
            case 0x5B -> bit_u3_r8(3, 'E');
            case 0x5C -> bit_u3_r8(3, 'H');
            case 0x5D -> bit_u3_r8(3, 'L');
            case 0x5E -> bit_u3_phl(3);
            case 0x5F -> bit_u3_r8(3, 'A');

            case 0x60 -> bit_u3_r8(4, 'B');
            case 0x61 -> bit_u3_r8(4, 'C');
            case 0x62 -> bit_u3_r8(4, 'D');
            case 0x63 -> bit_u3_r8(4, 'E');
            case 0x64 -> bit_u3_r8(4, 'H');
            case 0x65 -> bit_u3_r8(4, 'L');
            case 0x66 -> bit_u3_phl(4);
            case 0x67 -> bit_u3_r8(4, 'A');
            case 0x68 -> bit_u3_r8(5, 'B');
            case 0x69 -> bit_u3_r8(5, 'C');
            case 0x6A -> bit_u3_r8(5, 'D');
            case 0x6B -> bit_u3_r8(5, 'E');
            case 0x6C -> bit_u3_r8(5, 'H');
            case 0x6D -> bit_u3_r8(5, 'L');
            case 0x6E -> bit_u3_phl(5);
            case 0x6F -> bit_u3_r8(5, 'A');

            case 0x70 -> bit_u3_r8(6, 'B');
            case 0x71 -> bit_u3_r8(6, 'C');
            case 0x72 -> bit_u3_r8(6, 'D');
            case 0x73 -> bit_u3_r8(6, 'E');
            case 0x74 -> bit_u3_r8(6, 'H');
            case 0x75 -> bit_u3_r8(6, 'L');
            case 0x76 -> bit_u3_phl(6);
            case 0x77 -> bit_u3_r8(6, 'A');
            case 0x78 -> bit_u3_r8(7, 'B');
            case 0x79 -> bit_u3_r8(7, 'C');
            case 0x7A -> bit_u3_r8(7, 'D');
            case 0x7B -> bit_u3_r8(7, 'E');
            case 0x7C -> bit_u3_r8(7, 'H');
            case 0x7D -> bit_u3_r8(7, 'L');
            case 0x7E -> bit_u3_phl(7);
            case 0x7F -> bit_u3_r8(7, 'A');

            case 0x80 -> res_u3_r8(0, 'B');
            case 0x81 -> res_u3_r8(0, 'C');
            case 0x82 -> res_u3_r8(0, 'D');
            case 0x83 -> res_u3_r8(0, 'E');
            case 0x84 -> res_u3_r8(0, 'H');
            case 0x85 -> res_u3_r8(0, 'L');
            case 0x86 -> res_u3_phl(0);
            case 0x87 -> res_u3_r8(0, 'A');
            case 0x88 -> res_u3_r8(1, 'B');
            case 0x89 -> res_u3_r8(1, 'C');
            case 0x8A -> res_u3_r8(1, 'D');
            case 0x8B -> res_u3_r8(1, 'E');
            case 0x8C -> res_u3_r8(1, 'H');
            case 0x8D -> res_u3_r8(1, 'L');
            case 0x8E -> res_u3_phl(1);
            case 0x8F -> res_u3_r8(1, 'A');

            case 0x90 -> res_u3_r8(2, 'B');
            case 0x91 -> res_u3_r8(2, 'C');
            case 0x92 -> res_u3_r8(2, 'D');
            case 0x93 -> res_u3_r8(2, 'E');
            case 0x94 -> res_u3_r8(2, 'H');
            case 0x95 -> res_u3_r8(2, 'L');
            case 0x96 -> res_u3_phl(2);
            case 0x97 -> res_u3_r8(2, 'A');
            case 0x98 -> res_u3_r8(3, 'B');
            case 0x99 -> res_u3_r8(3, 'C');
            case 0x9A -> res_u3_r8(3, 'D');
            case 0x9B -> res_u3_r8(3, 'E');
            case 0x9C -> res_u3_r8(3, 'H');
            case 0x9D -> res_u3_r8(3, 'L');
            case 0x9E -> res_u3_phl(3);
            case 0x9F -> res_u3_r8(3, 'A');

            case 0xA0 -> res_u3_r8(4, 'B');
            case 0xA1 -> res_u3_r8(4, 'C');
            case 0xA2 -> res_u3_r8(4, 'D');
            case 0xA3 -> res_u3_r8(4, 'E');
            case 0xA4 -> res_u3_r8(4, 'H');
            case 0xA5 -> res_u3_r8(4, 'L');
            case 0xA6 -> res_u3_phl(4);
            case 0xA7 -> res_u3_r8(4, 'A');
            case 0xA8 -> res_u3_r8(5, 'B');
            case 0xA9 -> res_u3_r8(5, 'C');
            case 0xAA -> res_u3_r8(5, 'D');
            case 0xAB -> res_u3_r8(5, 'E');
            case 0xAC -> res_u3_r8(5, 'H');
            case 0xAD -> res_u3_r8(5, 'L');
            case 0xAE -> res_u3_phl(5);
            case 0xAF -> res_u3_r8(5, 'A');

            case 0xB0 -> res_u3_r8(6, 'B');
            case 0xB1 -> res_u3_r8(6, 'C');
            case 0xB2 -> res_u3_r8(6, 'D');
            case 0xB3 -> res_u3_r8(6, 'E');
            case 0xB4 -> res_u3_r8(6, 'H');
            case 0xB5 -> res_u3_r8(6, 'L');
            case 0xB6 -> res_u3_phl(6);
            case 0xB7 -> res_u3_r8(6, 'A');
            case 0xB8 -> res_u3_r8(7, 'B');
            case 0xB9 -> res_u3_r8(7, 'C');
            case 0xBA -> res_u3_r8(7, 'D');
            case 0xBB -> res_u3_r8(7, 'E');
            case 0xBC -> res_u3_r8(7, 'H');
            case 0xBD -> res_u3_r8(7, 'L');
            case 0xBE -> res_u3_phl(7);
            case 0xBF -> res_u3_r8(7, 'A');

            case 0xC0 -> set_u3_r8(0, 'B');
            case 0xC1 -> set_u3_r8(0, 'C');
            case 0xC2 -> set_u3_r8(0, 'D');
            case 0xC3 -> set_u3_r8(0, 'E');
            case 0xC4 -> set_u3_r8(0, 'H');
            case 0xC5 -> set_u3_r8(0, 'L');
            case 0xC6 -> set_u3_phl(0);
            case 0xC7 -> set_u3_r8(0, 'A');
            case 0xC8 -> set_u3_r8(1, 'B');
            case 0xC9 -> set_u3_r8(1, 'C');
            case 0xCA -> set_u3_r8(1, 'D');
            case 0xCB -> set_u3_r8(1, 'E');
            case 0xCC -> set_u3_r8(1, 'H');
            case 0xCD -> set_u3_r8(1, 'L');
            case 0xCE -> set_u3_phl(1);
            case 0xCF -> set_u3_r8(1, 'A');

            case 0xD0 -> set_u3_r8(2, 'B');
            case 0xD1 -> set_u3_r8(2, 'C');
            case 0xD2 -> set_u3_r8(2, 'D');
            case 0xD3 -> set_u3_r8(2, 'E');
            case 0xD4 -> set_u3_r8(2, 'H');
            case 0xD5 -> set_u3_r8(2, 'L');
            case 0xD6 -> set_u3_phl(2);
            case 0xD7 -> set_u3_r8(2, 'A');
            case 0xD8 -> set_u3_r8(3, 'B');
            case 0xD9 -> set_u3_r8(3, 'C');
            case 0xDA -> set_u3_r8(3, 'D');
            case 0xDB -> set_u3_r8(3, 'E');
            case 0xDC -> set_u3_r8(3, 'H');
            case 0xDD -> set_u3_r8(3, 'L');
            case 0xDE -> set_u3_phl(3);
            case 0xDF -> set_u3_r8(3, 'A');

            case 0xE0 -> set_u3_r8(4, 'B');
            case 0xE1 -> set_u3_r8(4, 'C');
            case 0xE2 -> set_u3_r8(4, 'D');
            case 0xE3 -> set_u3_r8(4, 'E');
            case 0xE4 -> set_u3_r8(4, 'H');
            case 0xE5 -> set_u3_r8(4, 'L');
            case 0xE6 -> set_u3_phl(4);
            case 0xE7 -> set_u3_r8(4, 'A');
            case 0xE8 -> set_u3_r8(5, 'B');
            case 0xE9 -> set_u3_r8(5, 'C');
            case 0xEA -> set_u3_r8(5, 'D');
            case 0xEB -> set_u3_r8(5, 'E');
            case 0xEC -> set_u3_r8(5, 'H');
            case 0xED -> set_u3_r8(5, 'L');
            case 0xEE -> set_u3_phl(5);
            case 0xEF -> set_u3_r8(5, 'A');

            case 0xF0 -> set_u3_r8(6, 'B');
            case 0xF1 -> set_u3_r8(6, 'C');
            case 0xF2 -> set_u3_r8(6, 'D');
            case 0xF3 -> set_u3_r8(6, 'E');
            case 0xF4 -> set_u3_r8(6, 'H');
            case 0xF5 -> set_u3_r8(6, 'L');
            case 0xF6 -> set_u3_phl(6);
            case 0xF7 -> set_u3_r8(6, 'A');
            case 0xF8 -> set_u3_r8(7, 'B');
            case 0xF9 -> set_u3_r8(7, 'C');
            case 0xFA -> set_u3_r8(7, 'D');
            case 0xFB -> set_u3_r8(7, 'E');
            case 0xFC -> set_u3_r8(7, 'H');
            case 0xFD -> set_u3_r8(7, 'L');
            case 0xFE -> set_u3_phl(7);
            case 0xFF -> set_u3_r8(7, 'A');

            default -> throw new RuntimeException("invalid prefixed opcode: " + opcode);
        }
    }

    // BELOW CONTAIN ALL IMPLEMENTATIONS FOR PREFIXED CALLS

    // RLC/RRC

    private void rlc_r8(char register) {
        final int regValue = getr8(register);
        final int leftRotateResult = rotateLeft(regValue);

        final int cFlagValue = leftRotateResult >> 8; // keep 9th bit (the c flag value) as a single bit
        final int newRegValue = leftRotateResult & 0xFF; // keep only first 8 bits
        setr8(register, newRegValue);

        rotationFlagSets(cFlagValue, newRegValue);

        totalMCycles += 2;
        PC += 2;
    }

    private void rlc_phl() {
        final int hlByteValue = memory.readByte(HL);
        final int leftRotateResult = rotateLeft(hlByteValue);

        final int cFlagValue = leftRotateResult >> 8;
        final int newHlByteValue = leftRotateResult & 0xFF;

        memory.writeByte(HL, (short) newHlByteValue);
        rotationFlagSets(cFlagValue, newHlByteValue);

        totalMCycles += 4;
        PC += 2;
    }

    private void rrc_r8(char register) {
        final int regValue = getr8(register);
        final int rightRotateResult = rotateRight(regValue);

        final int cFlagValue = rightRotateResult & 0x01; // keep 1st bit
        final int newRegValue = rightRotateResult >> 1; // removes the cValue so we just keep reg
        setr8(register, newRegValue);

        rotationFlagSets(cFlagValue, newRegValue);

        totalMCycles += 2;
        PC += 2;
    }

    private void rrc_phl() {
        final int hlByteValue = memory.readByte(HL);
        final int rightRotateResult = rotateRight(hlByteValue);

        final int cFlagValue = rightRotateResult & 0x01; // keep 1st bit
        final int newHlByteValue = rightRotateResult >> 1; // removes the cValue

        memory.writeByte(HL, (short) newHlByteValue);
        rotationFlagSets(cFlagValue, newHlByteValue);

        totalMCycles += 4;
        PC += 2;
    }


    // RL/RR (rotate through the carry flag)

    private void rl_r8(char register) {
        final int regValue = getr8(register);
        final int rotatedValue = rotateLeftThroughC(regValue);

        final int cFlagValue = rotatedValue >> 8; // 9th bit
        final int newRegValue = rotatedValue & 0xFF; // first 8 bits

        setr8(register, newRegValue);
        rotationFlagSets(cFlagValue, newRegValue);

        totalMCycles += 2;
        PC += 2;
    }

    private void rl_phl() {
        final int hlByteValue = memory.readByte(HL);
        final int rotatedValue = rotateLeftThroughC(hlByteValue);

        final int cFlagValue = rotatedValue >> 8;
        final int newHlByteValue = rotatedValue & 0xFF;

        memory.writeByte(HL, (short) newHlByteValue);
        rotationFlagSets(cFlagValue, newHlByteValue);

        totalMCycles += 4;
        PC += 2;
    }

    private void rr_r8(char register) {
        final int regValue = getr8(register);
        final int rotatedValue = rotateRightThroughC(regValue);

        final int cFlagValue = rotatedValue & 0x01; // first bit
        final int newRegValue = rotatedValue >> 1;

        setr8(register, newRegValue);
        rotationFlagSets(cFlagValue, newRegValue);

        totalMCycles += 2;
        PC += 2;
    }

    private void rr_phl() {
        final int hlByteValue = memory.readByte(HL);
        final int rotatedValue = rotateRightThroughC(hlByteValue);

        final int cFlagValue = rotatedValue & 0x01;
        final int newHlByteValue = rotatedValue >> 1;

        memory.writeByte(HL, (short) newHlByteValue);
        rotationFlagSets(cFlagValue, newHlByteValue);

        totalMCycles += 4;
        PC += 2;
    }

    // SLA/SRA (Shift Left/Right Arithmetics)
    // These are shifts that preserve the sign bit (most significant)
    /*
    These are failing the 09 test.
    http://marc.rawer.de/Gameboy/Docs/GBCPUman.pdf

    This says that SLA must have the least significant bit set to 0.

    BUG: we were using rotateLeft/Right() methods in the sla/sra instead of the shiftLeft/Right()
     */

    private void sla_r8(char register) {
        final int regValue = getr8(register);
        final int leftShiftValue = shiftLeft(regValue);

        // bit 9 now holds the sign value of original regValue
        final int cFlagValue = leftShiftValue >> 8;
        // when shifting left, the least sig bit should be 0 anyway
        final int newRegValue = leftShiftValue & 0xFF;

        setr8(register, newRegValue);
        rotationFlagSets(cFlagValue, newRegValue);

        totalMCycles += 2;
        PC += 2;
    }

    // same as above but accesses HL byte val
    private void sla_phl() {
        final int hlByteValue = memory.readByte(HL);
        final int leftShiftValue = shiftLeft(hlByteValue);

        final int cFlagValue = leftShiftValue >> 8;
        final int newHlByteValue = leftShiftValue & 0xFF;

        memory.writeByte(HL, (short) newHlByteValue);
        rotationFlagSets(cFlagValue, newHlByteValue);

        totalMCycles += 4;
        PC += 2;
    }

    private void sra_r8(char register) {
        final int regValue = getr8(register);
        final int rightShiftValue = shiftRight(regValue);

        // out of 9bits, bit 1 holds cFlag and bit 8 holds previous sign value, copy 8 into 9
        final int cFlagValue = rightShiftValue & 0x01;
        final int signValue = (rightShiftValue & 0xFF) >> 7;
        int newRegValue;
        if (signValue == 1) {
            // "or". Place saved sign into 9th bit, then shift everything right 1 to keep byte value of it
            newRegValue = (rightShiftValue | (signValue << 8)) >> 1;
        } else {
            // if sign is 0, 9th bit is 0 anyway, so we don't need to change anything. (just bit shift 1)
            newRegValue = rightShiftValue >> 1;
        }

        setr8(register, newRegValue);
        rotationFlagSets(cFlagValue, newRegValue);

        totalMCycles += 2;
        PC += 2;
    }

    // same as above for [HL]
    private void sra_phl() {
        final int hlByteValue = memory.readByte(HL);
        final int rightShiftValue = shiftRight(hlByteValue);

        final int cFlagValue = rightShiftValue & 0x01;
        final int signValue = (rightShiftValue & 0xFF) >> 7;
        int newHlByteValue;
        if (signValue == 1) {
            newHlByteValue = (rightShiftValue | (signValue << 8)) >> 1;
        } else {
            newHlByteValue = rightShiftValue >> 1;
        }

        memory.writeByte(HL, (short) newHlByteValue);
        rotationFlagSets(cFlagValue, newHlByteValue);

        totalMCycles += 4;
        PC += 2;
    }

    // SWAP instrs (just swaps upper 4 with lower 4 bits)

    private void swap_r8(char register) {
        final int regValue = getr8(register);
        final int swappedValue = swapUpperLowerNibble(regValue);

        setr8(register, swappedValue);
        xorFlagSets(swappedValue); // uses the same flag settings as xor, so we just use this method

        totalMCycles += 2;
        PC += 2;
    }

    private void swap_phl() {
        final int hlByteValue = memory.readByte(HL);
        final int swappedValue = swapUpperLowerNibble(hlByteValue);

        memory.writeByte(HL, (short) swappedValue);
        xorFlagSets(swappedValue); // uses the same flag settings as xor, so we just use this method

        totalMCycles += 4;
        PC += 2;
    }

    // SRL instructions (shift right logically) doesn't preserve sign

    private void srl_r8(char register) {
        final int regValue = getr8(register);
        final int rightShiftValue = shiftRight(regValue);

        final int cFlagValue = rightShiftValue & 0x01;
        final int newRegValue = rightShiftValue >> 1;

        setr8(register, newRegValue);
        rotationFlagSets(cFlagValue, newRegValue);

        totalMCycles += 2;
        PC += 2;
    }

    private void srl_phl() {
        final int hlByteValue = memory.readByte(HL);
        final int rightShiftValue = shiftRight(hlByteValue);

        final int cFlagValue = rightShiftValue & 0x01;
        final int newHlByteValue = rightShiftValue >> 1;

        memory.writeByte(HL, (short) newHlByteValue);
        rotationFlagSets(cFlagValue, newHlByteValue);

        totalMCycles += 4;
        PC += 2;
    }

    // BIT instrs

    // tests the bitIndex (0-7) to see if on or off to set Z flag accordingly
    private void bit_u3_r8(final int bitIndex, final char register) {
        final int regValue = getr8(register);
        switch (bitIndex) {
            case 0 -> handleBitTest(regValue & 0x01);
            case 1 -> handleBitTest((regValue >> 1) & 0x01);
            case 2 -> handleBitTest((regValue >> 2) & 0x01);
            case 3 -> handleBitTest((regValue >> 3) & 0x01);
            case 4 -> handleBitTest((regValue >> 4) & 0x01);
            case 5 -> handleBitTest((regValue >> 5) & 0x01);
            case 6 -> handleBitTest((regValue >> 6) & 0x01);
            case 7 -> handleBitTest((regValue >> 7) & 0x01);
            default -> throw new RuntimeException("invalid bitIndex: " + bitIndex);
        }

        totalMCycles += 2;
        PC += 2;
    }

    private void bit_u3_phl(final int bitIndex) {
        final int hlByteValue = memory.readByte(HL);
        switch (bitIndex) {
            case 0 -> handleBitTest(hlByteValue & 0x01);
            case 1 -> handleBitTest((hlByteValue >> 1) & 0x01);
            case 2 -> handleBitTest((hlByteValue >> 2) & 0x01);
            case 3 -> handleBitTest((hlByteValue >> 3) & 0x01);
            case 4 -> handleBitTest((hlByteValue >> 4) & 0x01);
            case 5 -> handleBitTest((hlByteValue >> 5) & 0x01);
            case 6 -> handleBitTest((hlByteValue >> 6) & 0x01);
            case 7 -> handleBitTest((hlByteValue >> 7) & 0x01);
            default -> throw new RuntimeException("invalid bitIndex: " + bitIndex);
        }

        totalMCycles += 3;
        PC += 2;
    }


    // RES instrs (sets the bitIndex to 0)

    private void res_u3_r8(final int bitIndex, final char register) {
        // we will and these with the regValue to set the correct bitIndex to 0
        final int setBit0 = 0b11111110;
        final int setBit1 = 0b11111101;
        final int setBit2 = 0b11111011;
        final int setBit3 = 0b11110111;
        final int setBit4 = 0b11101111;
        final int setBit5 = 0b11011111;
        final int setBit6 = 0b10111111;
        final int setBit7 = 0b01111111;

        final int regValue = getr8(register);

        switch (bitIndex) {
            case 0 -> setr8(register, regValue & setBit0);
            case 1 -> setr8(register, regValue & setBit1);
            case 2 -> setr8(register, regValue & setBit2);
            case 3 -> setr8(register, regValue & setBit3);
            case 4 -> setr8(register, regValue & setBit4);
            case 5 -> setr8(register, regValue & setBit5);
            case 6 -> setr8(register, regValue & setBit6);
            case 7 -> setr8(register, regValue & setBit7);
            default -> throw new RuntimeException("invalid bitIndex: " + bitIndex);
        }

        totalMCycles += 2;
        PC += 2;
    }

    private void res_u3_phl(final int bitIndex) {
        // we will and these with the regValue to set the correct bitIndex to 0
        final int setBit0 = 0b11111110;
        final int setBit1 = 0b11111101;
        final int setBit2 = 0b11111011;
        final int setBit3 = 0b11110111;
        final int setBit4 = 0b11101111;
        final int setBit5 = 0b11011111;
        final int setBit6 = 0b10111111;
        final int setBit7 = 0b01111111;

        final int hlByteValue = memory.readByte(HL);

        switch (bitIndex) {
            case 0 -> memory.writeByte(HL, (short) (hlByteValue & setBit0));
            case 1 -> memory.writeByte(HL, (short) (hlByteValue & setBit1));
            case 2 -> memory.writeByte(HL, (short) (hlByteValue & setBit2));
            case 3 -> memory.writeByte(HL, (short) (hlByteValue & setBit3));
            case 4 -> memory.writeByte(HL, (short) (hlByteValue & setBit4));
            case 5 -> memory.writeByte(HL, (short) (hlByteValue & setBit5));
            case 6 -> memory.writeByte(HL, (short) (hlByteValue & setBit6));
            case 7 -> memory.writeByte(HL, (short) (hlByteValue & setBit7));
            default -> throw new RuntimeException("invalid bitIndex: " + bitIndex);
        }

        totalMCycles += 4;
        PC += 2;
    }


    // SET instrs (opposite of RES, we set the bitIndex to 1)

    private void set_u3_r8(final int bitIndex, final char register) {
        // use these to OR the correct bitIndex to 1
        final int setBit0 = 0b00000001;
        final int setBit1 = 0b00000010;
        final int setBit2 = 0b00000100;
        final int setBit3 = 0b00001000;
        final int setBit4 = 0b00010000;
        final int setBit5 = 0b00100000;
        final int setBit6 = 0b01000000;
        final int setBit7 = 0b10000000;

        final int regValue = getr8(register);

        switch (bitIndex) {
            case 0 -> setr8(register, regValue | setBit0);
            case 1 -> setr8(register, regValue | setBit1);
            case 2 -> setr8(register, regValue | setBit2);
            case 3 -> setr8(register, regValue | setBit3);
            case 4 -> setr8(register, regValue | setBit4);
            case 5 -> setr8(register, regValue | setBit5);
            case 6 -> setr8(register, regValue | setBit6);
            case 7 -> setr8(register, regValue | setBit7);
            default -> throw new RuntimeException("invalid bitIndex: " + bitIndex);
        }

        totalMCycles += 2;
        PC += 2;
    }

    private void set_u3_phl(final int bitIndex) {
        // use these to OR the correct bitIndex to 1
        final int setBit0 = 0b00000001;
        final int setBit1 = 0b00000010;
        final int setBit2 = 0b00000100;
        final int setBit3 = 0b00001000;
        final int setBit4 = 0b00010000;
        final int setBit5 = 0b00100000;
        final int setBit6 = 0b01000000;
        final int setBit7 = 0b10000000;

        final int hlByteValue = memory.readByte(HL);

        switch (bitIndex) {
            case 0 -> memory.writeByte(HL, (short) (hlByteValue | setBit0));
            case 1 -> memory.writeByte(HL, (short) (hlByteValue | setBit1));
            case 2 -> memory.writeByte(HL, (short) (hlByteValue | setBit2));
            case 3 -> memory.writeByte(HL, (short) (hlByteValue | setBit3));
            case 4 -> memory.writeByte(HL, (short) (hlByteValue | setBit4));
            case 5 -> memory.writeByte(HL, (short) (hlByteValue | setBit5));
            case 6 -> memory.writeByte(HL, (short) (hlByteValue | setBit6));
            case 7 -> memory.writeByte(HL, (short) (hlByteValue | setBit7));
            default -> throw new RuntimeException("invalid bitIndex: " + bitIndex);
        }

        totalMCycles += 4;
        PC += 2;
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
    private void setF(final int value) { // TODO: i think this is pretty pointless..
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
        int zFlagSet = regFValue | BIT_7_ON; // keeps all other bits same but makes sure 7th is set
        if (!toOne) {
            zFlagSet = zFlagSet & BIT_7_OFF; // otherwise set 7th bit to off (0)
        }
        setF(zFlagSet);
    }

    private boolean zFlagOn() {
        final int bit7 = getF() & BIT_7_ON; // try to get 7th bit only

        return (bit7 == BIT_7_ON); // if 7th bit was on, it will be equal to BIT_7_ON
    }

    /**
     * Subtraction Flag, Accesses the 6th bit in F register (from AF) and sets it to 1 or 0
     */
    private void setNFlag(final boolean toOne) {
        final short regFValue = (short) getF();
        int nFlagSet = regFValue | BIT_6_ON;
        if (!toOne) {
            nFlagSet = nFlagSet & BIT_6_OFF;
        }
        setF(nFlagSet);
    }

    private boolean nFlagOn() {
        final int bit6 = getF() & BIT_6_ON;

        return (bit6 == BIT_6_ON);
    }


    /**
     * Half Carry Flag, Accesses the 5th bit in F register (from AF) and sets it to 1 or 0
     */
    private void setHFlag(final boolean toOne) {
        final short regFValue = (short) getF();
        int hFlagSet = regFValue | BIT_5_ON;
        if (!toOne) {
            hFlagSet = hFlagSet & BIT_5_OFF;
        }
        setF(hFlagSet);
    }

    private boolean hFlagOn() {
        final int bit5 = getF() & BIT_5_ON;

        return (bit5 == BIT_5_ON);
    }

    /**
     * CarryFlag, Accesses the 4th bit in F register (from AF) and sets it to 1 or 0
     */
    private void setCFlag(final boolean toOne) {
        final short regFValue = (short) getF();
        int cFlagSet = regFValue | BIT_4_ON;
        if (!toOne) {
            cFlagSet = cFlagSet & BIT_4_OFF;
        }
        setF(cFlagSet);
    }

    private boolean cFlagOn() {
        final int bit4 = getF() & BIT_4_ON;

        return (bit4 == BIT_4_ON);
    }


    // Flag set cases

    private void zFlag_8bit_overflow_or_borrow(final int result) {
        setZFlag(result == 0);
    }

    private void hFlag_8bit_overflow(final int value, final int adder) {
        final int valueLowerNibble = value & 0xF;
        final int adderLowerNibble = adder & 0xF;
        final int halfResult = valueLowerNibble + adderLowerNibble;
        setHFlag(halfResult > 0xF);
    }

    private void cFlag_8bit_overflow(final int result) {
        setCFlag(result > 0xFF);
    }


    private void hFlag_8bit_borrow(final int value, final int subtractor) {
        final int valueLowerNibble = value & 0xF;
        final int subtractorLowerNibble = subtractor & 0xF;
        // when subtractorNib is bigger, we have to borrow from bit5!

        setHFlag(subtractorLowerNibble > valueLowerNibble);
    }

    private void cFlag_8bit_borrow(final int result) {
        setCFlag(result < 0);
    }

      private boolean cFlag_16bit_overflow(final int result) {
        if (result > 0xFFFF) {
            setCFlag(true);
            return true;
        } else {
            setCFlag(false);
            return false;
        }
    }

    private void hFlag_16bit_overflow(final int value, final int adder) {
        /*
        The above is still incorrect.
        We need to check from nibble 1 to 3, if there's a carry. If there is, we check the next valueNibble
        if we can carry from there.
        If we can't we check the next nibble again.
        If we reach nibble 4 and we are able to carry, we know a half carry happened.

        My thinking right now is just to loop. But I wonder if there is a quicker way to determine this?
        Just check if both lower 12 bits add up to > 0xFFF. This should be fine.
         */
        final int value12bit = value & 0xFFF;
        final int adder12bit = adder & 0xFFF;
        final int halfResult = value12bit + adder12bit;
        setHFlag(halfResult > 0xFFF);
    }

    // ONLY used in ADD SP,e8
    // we check if the 16 bit result has any 8bit overflows (carry > 7th, half > 3rd)
    private void spCFlagOverflow(final int value, final int adder) {
        final int valueLowerByte = value & 0xFF;
        final int adderLowerByte = adder & 0xFF;
        final int result = valueLowerByte + adderLowerByte;
        setCFlag(result > 0xFF);
    }

    private void spHFlagOverflow(final int value, final int adder) {
        final int valueLowerNibble = value & 0xF;
        final int adderLowerNibble = adder & 0xF;
        final int halfResult = valueLowerNibble + adderLowerNibble;
        setHFlag(halfResult > 0xF);
    }


    private void andFlagSets(final int result) {
        setZFlag(result == 0);
        setNFlag(false);
        setHFlag(true);
        setCFlag(false);
    }

    private void xorFlagSets(final int result) {
        setZFlag(result == 0);
        setNFlag(false);
        setHFlag(false);
        setCFlag(false);
    }

    private void rotationAFlagSets(final int cFlagResult) {
        setZFlag(false);
        setNFlag(false);
        setHFlag(false);
        setCFlag(cFlagResult == 1);
    }

    private void rotationFlagSets(final int cFlagResult, final int result) {
        setZFlag(result == 0);
        setCFlag(cFlagResult == 1);
        setNFlag(false); // these must be set to false for rrc/rlc's
        setHFlag(false);
    }

    /**
     * Checks wether 8bit result has overflown or not. Will wrap the result accordingly if so.
     */
    private int checkAndSetOverflowVal8bit(final int result) {
        if (result > 0xFF) {
            return result - 0x100; // was 0xFF, but we this doesn't wrap around completely (-256 to get the correct val)
        } else {
            return result;
        }
    }

    /**
     * Checks whether 8bit result has underflown or not. Will wrap the result accordingly if so.
     * @param result initial result
     * @return corrected result
     */
    private int checkAndSetUnderflowVal8bit(final int result) {
        if (result < 0) {
            return result + 0x100; // wrap around
        } else {
            return result;
        }
    }

    // same as 8 bit but for 16bit vals
    private int checkAndSetOverflowVal16bit(final int result) {
        if (result > 0xFFFF) {
            return result - 0x10000;
        } else {
            return result;
        }
    }

    private int checkAndSetUnderflowVal16bit(final int result) {
        if (result < 0) {
            return result + 0x10000;
        } else {
            return result;
        }
    }

    /**
     * 8bit Rotate right mechanism. For avoiding repeated calls in rotate instructions.
     * It places C flag value on the left and shifts everything left.
     * These return 9bits which include the CFlag value for other methods to extract
     * @param value the value to rotate
     * @return rotated value
     */
    private int rotateLeft(final int value) {
        final int cFlagValue = cFlagOn() ? 1 : 0;

        // combine in correct bit places
        final int combine = (cFlagValue << 8) | value;
        final int leftRotateResult = (combine << 1) & 0b111111111; // & ensures we only keep the 9 bits (we don't want 10)

        final int cFlagResult = leftRotateResult >> 8;
        int leftRotateValue;
        if (cFlagResult == 1) {
            leftRotateValue = leftRotateResult | cFlagResult;
        } else {
            leftRotateValue = leftRotateResult; // right-most bit will be 0 anyway
        }

        return leftRotateValue;
    }

    /**
     * 8bit Rotate right mechanism. For avoiding repeated calls in rotate instructions.
     * Places C flag value on right and shifts everything right.
     * These return 9bits which include the CFlag value for other methods to extract
     * @param value the value to rotate
     * @return rotated value
     */
    private int rotateRight(final int value) {
        final int cFlagValue = cFlagOn() ? 1 : 0;

        final int combine = (value << 1) | cFlagValue;
        final int rightRotateResult = (combine >> 1) & 0b111111111; // 9bits only

        final int cFlagResult = rightRotateResult & 0x01; // bit 1
        // we wrap cFlagResult around (cFlag basically just get's a copy of this)
        int rightRotateValue;
        if (cFlagResult == 1) {
            rightRotateValue = rightRotateResult | (cFlagResult << 8);
        } else {
            final int cFlag9bitValue = 0b011111111;
            rightRotateValue = rightRotateResult & cFlag9bitValue;
        }

        return rightRotateValue;
    }

    private int rotateLeftThroughC(final int value) {
        final int cFlagValue = cFlagOn() ? 1 : 0;

        final int combine = cFlagValue << 8 | value; // bit 9 = cFlag
        final int leftRotateResult = combine << 1; // keep 10 bits this time (to wrap c back around)

        // keep last bit (10th since we shifted left 1) and shifts back to 1st bit placement
        //final int overflowBit = (leftRotateResult & 0b1000000000) >> 9;
        // places overflow bit to the first bit of val and keeps 9 bits (carry at 9)
        int leftRotatedValue;
        if (cFlagValue == 1) {
            leftRotatedValue = (leftRotateResult | cFlagValue) & 0b111111111; // keep 9 bits
        } else {
            // leftmost ends up 0 anyway, so just trim out the 10th bit
            leftRotatedValue = leftRotateResult & 0b111111111;
        }

        return leftRotatedValue;
    }

    private int rotateRightThroughC(final int value) {
        final int cFlagValue = cFlagOn() ? 1 : 0;

        final int combine = (value << 1) | cFlagValue;
        final int rightRotateResult = combine >> 1;

        // then wrap the orginal cFlag value around
        int rightRotatedValue;
        if (cFlagValue == 1) { // if on, we can OR to apply it
            rightRotatedValue = (cFlagValue << 8) | rightRotateResult;
        } else {
            // if off, 9th bit will be zero anyway, so no need to do anything
            rightRotatedValue = rightRotateResult;
        }

        return rightRotatedValue;
    }

    // we don't wrap values around in shifts (like in rotates)
    // left not needed for now, but keep incase
    private int shiftLeft(final int value) {
        final int cFlagValue = cFlagOn() ? 1 : 0;
        final int combine = (cFlagValue << 8) | value;

        return (combine << 1) & 0b111111111;
    }

    private int shiftRight(final int value) {
        final int cFlagValue = cFlagOn() ? 1 : 0;
        final int combine = (value << 1) | cFlagValue;
        return (combine >> 1) & 0b111111111;
    }


    /**
     * Helper method for swapping upper nibble with lower nibble.
     * To be used for SWAP instructions.
     */
    private int swapUpperLowerNibble(final int value) {
        final int lowerNibble = value & 0xF;
        final int upperNibble = value >> 4;

        return (lowerNibble << 4) | upperNibble;
    }


    /**
     * Set's the flags accordingly for BIT instructions
     * @param value the bit index we're testing
     */
    private void handleBitTest(final int value) {
        setZFlag(value == 0); // value == 0 will set true/false automatically
        setNFlag(false);
        setHFlag(true);
    }

    /**
     * To be used by the Interrupt Handler ONLY!
     */
    private void pushPCToStack() {
        final int pcHighByte = PC >> 8;
        final int pcLowByte = PC & 0xFF;
        SP--;
        memory.writeByte(SP, (short) pcHighByte);
        SP--;
        memory.writeByte(SP, (short) pcLowByte);

        totalMCycles += 3;
    }


}

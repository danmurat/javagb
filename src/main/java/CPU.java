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
    private int pcAccess = PC + 1;

    private boolean IME; // interrupt master enable flag (write only)
    private boolean eiTurnImeOn; // ei turns IME on after the next instruction (so we use this to check in executeInstr())

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

        IME = false;
        eiTurnImeOn = false;

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
                interruptHandle(); // attempt to handle (redirects PC to handle even)
            }
            final short opcode = memory.readByte(PC);
            instructionCall(opcode);
        } else {
            if (IME) {
                interruptHandle(); // this may never actually execute
            }
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
            case 0x76 -> halt(); // TODO!!!
            case 0x77 -> ld_phl_r8('A');
            case 0x78 -> ld_r8_r8('A', 'B');
            case 0x79 -> ld_r8_r8('A', 'C');
            case 0x7A -> ld_r8_r8('A', 'D');
            case 0x7B -> ld_r8_r8('A', 'E');
            case 0x7C -> ld_r8_r8('A', 'H');
            case 0x7D -> ld_r8_r8('A', 'L');
            case 0x7E -> ld_r8_phl('A');
            case 0x7F -> ld_r8_r8('A', 'A');

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
            case 0xC6 -> add_a_n8();
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
            case 0xD6 -> sub_a_n8();
            case 0xD7 -> rst(0x10);
            case 0xD8 -> ret_c();
            case 0xD9 -> reti(); // TODO: fully implement this
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
            case 0xF3 -> di(); // TODO!!!
            case 0xF5 -> push_r16('A', 'F');
            case 0xF6 -> or_a_n8();
            case 0xF7 -> rst(0x30);
            case 0xF8 -> ld_hl_spe8();
            case 0xF9 -> ld_sp_hl();
            case 0xFA -> ld_a_pn16();
            case 0xFB -> ei(); // TODO!!!
            case 0xFE -> cp_a_n8();
            case 0xFF -> rst(0x38);

            default -> throw new RuntimeException("invalid opcode: " + opcode);
        }
    }

    /**
     * Run different set of instructions when opcode is prefixed with xCB
     */
    private void prefixedInstructionCall() {
        // need to access next byte (first was xCB)
        PC += 1;
        final short opcode = memory.readByte(PC);

        switch (opcode) {
            case 0x00 -> System.out.println("implement the prefixed opcodes!");
            default -> throw new RuntimeException("invalid opcode: " + opcode);
        }
    }


    // TODO: shorten this!! lot of repeats
    private void interruptHandle() {
        final int pcHighByte = PC >> 8;
        final int pcLowByte = PC & 0xFF;

        totalMCycles += 2;

        // this instruction only runs if the IME is on
        // check which interrput we are handling (already in heirarchy)
        if (memory.vBlankRequest() && memory.vBlankEnable()) {
            memory.vBlankRequestReset();
            IME = false;
            // pc pushed to stack, then = source address
            SP--;
            memory.writeByte(SP, (short) pcHighByte);
            SP--;
            memory.writeByte(SP, (short) pcLowByte);
            PC = V_BLANK_SOURCE_ADDRESS;

            totalMCycles += 3;
        } else if (memory.lcdRequest() && memory.lcdEnable()) {
            memory.lcdRequestReset();
            IME = false;

            SP--;
            memory.writeByte(SP, (short) pcHighByte);
            SP--;
            memory.writeByte(SP, (short) pcLowByte);
            PC = STAT_SOURCE_ADDRESS;

            totalMCycles += 3;
        } else if (memory.timerRequest() && memory.timerEnable()) {
            memory.timerRequestReset();
            IME = false;

            SP--;
            memory.writeByte(SP, (short) pcHighByte);
            SP--;
            memory.writeByte(SP, (short) pcLowByte);
            PC = TIMER_SOURCE_ADDRESS;

            totalMCycles += 3;
        } else if (memory.serialRequest() && memory.serialEnable()) {
            memory.serialRequestReset();
            IME = false;

            SP--;
            memory.writeByte(SP, (short) pcHighByte);
            SP--;
            memory.writeByte(SP, (short) pcLowByte);
            PC = SERIAL_SOURCE_ADDRESS;

            totalMCycles += 3;
        } else if (memory.joypadRequest() && memory.joypadEnable()) {
            memory.joypadRequestReset();
            IME = false;

            SP--;
            memory.writeByte(SP, (short) pcHighByte);
            SP--;
            memory.writeByte(SP, (short) pcLowByte);
            PC = JOYPAD_SOURCE_ADDRESS;

            totalMCycles += 3;
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

    private void ld_pn16_sp() {
        final int address = memory.readWord(pcAccess);
        memory.writeWord(address, SP); // handles the low/high bytes

        totalMCycles += 5;
        PC += 3;
    }

    private void ld_sp_hl() {
        SP = HL;

        totalMCycles += 2;
        PC += 1;
    }

    private void ld_hl_spe8() {
        final int addressValue = memory.readByte(pcAccess); // signed
        SP += addressValue;
        setr16("HL", SP);

        setZFlag(false);
        setNFlag(false);
        if (SP > 0xFF) {
            setCFlag(true);
        } else if (SP > 0xF){
            setHFlag(true);
        }

        totalMCycles += 3;
        PC += 2;
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
        final short addressValue = memory.readByte(pcAccess);
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
        final short addressValue = memory.readByte(pcAccess);
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
        final short addressValue = memory.readByte(pcAccess);
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
        final int subValue = aValue - r8Value;

        zFlagHFlag_8bit_borrow(subValue);
        setNFlag(true);
        if (r8Value > aValue) {
            setCFlag(true);
        }

        totalMCycles += 1;
        PC += 1;
    }

    private void cp_a_phl() {
        final short aValue = (short) getr8('A');
        final short addressValue = memory.readByte(HL);
        final int subValue = aValue - addressValue;

        zFlagHFlag_8bit_borrow(subValue);
        setNFlag(true);
        if (addressValue > aValue) {
            setCFlag(true);
        }

        totalMCycles += 2;
        PC += 1;
    }

    private void cp_a_n8() {
        final short aValue = (short) getr8('A');
        final short addressValue = memory.readByte(pcAccess);
        final int subValue = aValue - addressValue;

        zFlagHFlag_8bit_borrow(subValue);
        setNFlag(true);
        if (addressValue > aValue) {
            setCFlag(true);
        }

        totalMCycles += 2;
        PC += 2;
    }


    // PUSH/POP instructions

    // we already have flags set inside AF, so no need to do anything else for PUSH AF
    private void push_r16(final char highRegister, final char lowRegister) {
        SP--;
        memory.writeByte(SP, (short) getr8(highRegister));
        SP--;
        memory.writeByte(SP, (short) getr8(lowRegister));

        totalMCycles += 4;
        PC += 1;
    }

    private void pop_r16(final char highRegister, final char lowRegister) {
        final short firstSPAddressValue = memory.readByte(SP);
        setr8(lowRegister, firstSPAddressValue);
        SP++;

        final short secondSPAddressValue = memory.readByte(SP);
        setr8(highRegister, secondSPAddressValue);
        SP++;

        totalMCycles += 3;
        PC += 1;
    }

    // since AF become new values,we need to remember and place the flags back to the new value
    private void pop_af() {
        // first save flags before popping
        final boolean zFlagOn = zFlagOn();
        final boolean nFlagOn = nFlagOn();
        final boolean hFlagOn = hFlagOn();
        final boolean cFlagOn = cFlagOn();

        pop_r16('A', 'F');

        if (zFlagOn) setZFlag(true);
        if (nFlagOn) setNFlag(true);
        if (hFlagOn) setHFlag(true);
        if (cFlagOn) setCFlag(true);
    }

    // JP instructions (jump)

    private void jp_n16() {
        PC = memory.readWord(pcAccess); // pc becomes the value of the immediate 2 address' values

        totalMCycles += 4;
        PC += 3; // TODO: should this be incremented at all??
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
        if (nFlagOn() && zFlagOn()) {
            jp_n16();
        } else {
            totalMCycles += 3;
            PC += 3;
        }
    }

    private void jp_nc_n16() {
        if (nFlagOn() && cFlagOn()) {
            jp_n16();
        } else {
            totalMCycles += 3;
            PC += 3;
        }
    }

    private void jp_hl() {
        PC = HL;

        totalMCycles += 1;
        PC += 1; // should we be doing this?
    }

    // JR instructions (Relative jump)

    // e8 is signed (so we can jump forwards 128 or backwards 127).
    private void jr_e8() {
        final short addressValueOffset = memory.readByte(pcAccess);
        PC += addressValueOffset; // relative jump (PC = PC + offset (where offset can be negative value))

        totalMCycles += 3;
        PC += 2;
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

    private void jr_nz_e8() {
        if (nFlagOn() && zFlagOn()) {
            jr_e8();
        } else {
            totalMCycles += 2;
            PC += 2;
        }
    }

    private void jr_nc_e8() {
        if (nFlagOn() && cFlagOn()) {
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
        SP++;

        final short secondSPAddressValue = memory.readByte(SP);
        final int pcHighByte = secondSPAddressValue;
        SP++;

        PC = (pcHighByte << 8) | pcLowByte;

        totalMCycles += 4;
        PC += 1;
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
        if (nFlagOn() && zFlagOn()) {
            ret();
            totalMCycles += 1;
        } else {
            totalMCycles += 2;
            PC += 1;
        }
    }

    private void ret_nc() {
        if (nFlagOn() && cFlagOn()) {
            ret();
            totalMCycles += 1;
        } else {
            totalMCycles += 2;
            PC += 1;
        }
    }

    public void reti() {
        ret();
        IME = true;
        // cycles/pc already set
    }


    // CALL instructions

    private void call_n16() {
        final short lowByte = memory.readByte(pcAccess);
        final short highByte = memory.readByte(pcAccess + 1);
        SP--;
        memory.writeByte(SP, highByte);
        SP--;
        memory.writeByte(SP, lowByte);

        // then implicit jp n16
        PC = memory.readWord(pcAccess);

        totalMCycles += 6;
        PC += 3;
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
        if (nFlagOn() && zFlagOn()) {
            call_n16();
        } else {
            totalMCycles += 3;
            PC += 3;
        }
    }

    private void call_nc_n16() {
        if (nFlagOn() && cFlagOn()) {
            call_n16();
        } else {
            totalMCycles += 3;
            PC += 3;
        }
    }

    // RST instruction

    // is a call instruction on the given vec TODO: no idea if this is correct..
    private void rst(final int address) {
        SP--;
        memory.writeByte(SP, memory.readByte(address));
        // TODO: do we need to do the implicit jp instruction????

        totalMCycles += 4;
        PC += 1;
    }

    // Rotation instructions

    private void rla() { // this is through the carry flag, so whatever is shifted out comes back
        int cFlagValue = 0;
        if (cFlagOn()) cFlagValue = 1;

        final int carryAndAreg = cFlagValue << 9 | getr8('A'); // bit 9 = cFlag
        final int leftRotateResult = carryAndAreg << 1;

        // keep last bit (10th since we shifted left 1) and shifts back to 1st bit placement
        final int overflowBit = (leftRotateResult & 0b1000000000) >> 9;
        cFlagValue = (leftRotateResult & 0b100000000) >> 8; // keep 9th

        // places overflow bit to the first bit of A and keeps only 8 bits
        final int aRegValue = (leftRotateResult | overflowBit) & 0xFF;

        setr8('A', aRegValue);
        rotationFlagSets(cFlagValue);

        totalMCycles += 1;
        PC += 1;
    }

    private void rra() { // through carry
        int cFlagValue = 0;
        if (cFlagOn()) cFlagValue = 1;

        // we'll need to make this 10 bits long, and consider bit 0 as the underflow bit (to move to bit 9)
        final int carryAndAreg = (getr8('A') << 2) | (cFlagValue << 1); // bit 0 = 0, bit 1 = cFlag
        final int rightRotateResult = carryAndAreg >> 1;

        final int underflowBit = rightRotateResult & 0b000000001; // keep 1st bit
        cFlagValue = (rightRotateResult & 0b000000010) >> 1; // keep 2nd bit (bit shift back to 1st bit)

        int aRegValue;
        if (underflowBit == 1) { // if on, we can OR to apply it
            aRegValue = (underflowBit << 8) | (rightRotateResult >> 2);
        } else { // if off, we need to AND
            // put 1's to the rest of the underflow byte so we don't change the other A register values
            aRegValue = ((underflowBit << 8) | 0b01111111) | (rightRotateResult >> 2);
        }

        setr8('A', aRegValue);
        rotationFlagSets(cFlagValue);

        totalMCycles += 1;
        PC += 1;
    }

    private void rlca() { // rotate left (not through carry)
        int cFlagValue = 0;
        if (cFlagOn()) cFlagValue = 1;

        final int carryAndAreg = (cFlagValue << 9) | getr8('A'); // combine in correct bit places
        final int leftRotateResult = (carryAndAreg << 1) & 0b111111111; // & ensures we only keep the 9 bits (we don't want 10)

        cFlagValue = leftRotateResult >> 8; // keep 9th bit (the c flag value) as a single bit
        final int aRegValue = leftRotateResult & 0b11111111; // keep only first 8 bits
        setr8('A', aRegValue);

        rotationFlagSets(cFlagValue);

        totalMCycles += 1;
        PC += 1;
    }

    private void rrca() { // rotate right
        int cFlagValue = 0;
        if (cFlagOn()) cFlagValue = 1;

        final int carryAndAreg = (getr8('A') << 1) | cFlagValue;
        final int rightRotateResult = (carryAndAreg >> 1) & 0b111111111;

        cFlagValue = rightRotateResult & 0b000000001; // keep 1st bit
        final int aRegValue = rightRotateResult >> 1; // removes the cValue so we just keep aReg
        setr8('A', aRegValue);

        rotationFlagSets(cFlagValue);

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

    private void di() {
        IME = false;

        totalMCycles += 1;
        PC += 1;
    }

    private void ei() {
        eiTurnImeOn = true;

        totalMCycles += 1;
        PC += 1;
    }

    // Miscellaneous instructions

    private void daa() { // decimal adjust accumulator
        int adjustment = 0;
        if (nFlagOn()) {
            if (hFlagOn()) adjustment += 0x6;
            if (cFlagOn()) adjustment += 0x60;
            final int aRegValue = getr8('A');
            final int newAValue = aRegValue - adjustment;
            setr8('A', newAValue);
        } else {
            final int aRegValue = getr8('A');
            if (hFlagOn() || (aRegValue & 0xF) > 0x9) adjustment += 0x6;
            if (cFlagOn() || aRegValue > 0x99) {
                adjustment += 0x60;
                setCFlag(true);
            }
            final int newAValue = aRegValue + adjustment;
            setr8('A', newAValue);
        }

        if (getr8('A') == 0) setZFlag(true);
        setHFlag(false);
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

    // TODO: how are we handling when result goes under 0?
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


    private void andFlagSets(final int result) {
        if (result == 0) setZFlag(true);
        setNFlag(false);
        setHFlag(true);
        setCFlag(false);
    }

    private void xorFlagSets(final int result) {
        if (result == 0) setZFlag(true);
        setNFlag(false);
        setHFlag(false);
        setCFlag(false);
    }

    private void rotationFlagSets(final int cFlagResult) {
        setZFlag(false);
        setNFlag(false);
        setHFlag(false);
        if (cFlagResult == 1) {
            setCFlag(true);
        } else {
            setCFlag(false);
        }
    }

}
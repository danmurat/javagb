import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Will run the emulation
 */
public class Run {
    public static void main(String[] args) throws IOException {
        System.out.println("Welcom to Java gb");

        // -- SET UP
        Memory memory = new Memory("individual/06-ld r,r.gb");
        CPU cpu = new CPU(memory);
        memory.setCPU(cpu);
        //memory.hexDumpRomContents();


        run(cpu, memory);
    }

    /**
     * Sets up and runs gameboy game
     */
    public static void run(CPU cpu, Memory memory) throws IOException {

        /*
        Von nueman, fetch decode execute cycle.

        PC starts at 0x100. Reads memory to get instruction, executes, and PC subsequently updates
        as this runs. This is continuously

        TODO: we will need to set up debugging helpers here to see how correct our implementation is
        remember we kind of need to implement one of the I/O ports for debugging so we see outputs
         */
        int executionAmount = 0;

        int testFlip = 0xFB;
        System.out.println("Test flip: " + memory.getTwosCompliment(testFlip));
        while (executionAmount < 1000) {
        //while (true) {
            //System.out.print(executionAmount + ": ");
            //cpu.printPC();
            logState(cpu); // every instr
            cpu.executeInstruction(); // handles the above comment

            //cpu.printOP();
            //System.out.println(" Instruction=" + getInstructionName(cpu.getOP()));

            // text output port (for the cpu test results)
           /* if (memory.readByte(0xFF02) == 0x81) {
                System.out.println((char) memory.readByte(0xFF01));
                memory.writeByte(0xFF02, (short) 0x00);
            }*/

            executionAmount++;
        }
    }

    // log
    private static void logState(CPU cpu) {
        String fileName = "log.txt";
        byte a = (byte) cpu.get8bitReg('A');
        byte b = (byte) cpu.get8bitReg('B');
        byte c = (byte) cpu.get8bitReg('C');
        byte d = (byte) cpu.get8bitReg('D');
        byte e = (byte) cpu.get8bitReg('E');
        byte h = (byte) cpu.get8bitReg('H');
        byte l = (byte) cpu.get8bitReg('L');
        byte f = (byte) cpu.getFreg();
        int sp = cpu.getSP();
        int pc = cpu.getPC();
        int[] pcmem = cpu.getPCmem();

        String logLine = String.format("A:%02x F:%02x B:%02x C:%02x D:%02x E:%02x H:%02x L:%02x SP:%04x PC:%04x PCMEM:%02x,%02x,%02x,%02x",
                a, f, b, c, d, e, h, l, sp, pc, pcmem[0], pcmem[1], pcmem[2], pcmem[3]);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true))) {
            writer.write(logLine);
            writer.newLine();
            writer.flush();
        } catch (IOException err) {
            err.printStackTrace(); // for now..
        }
    }

    // debugging assistance
    private static String getInstructionName(int opcode) {
        return switch (opcode) {
            case 0x00 -> "NOP";
            case 0x01 -> "LD BC,nn";
            case 0x02 -> "LD (BC),A";
            case 0x03 -> "INC BC";
            case 0x04 -> "INC B";
            case 0x05 -> "DEC B";
            case 0x06 -> "LD B,n";
            case 0x07 -> "RLCA";
            case 0x08 -> "LD (nn),SP";
            case 0x09 -> "ADD HL,BC";
            case 0x0A -> "LD A,(BC)";
            case 0x0B -> "DEC BC";
            case 0x0C -> "INC C";
            case 0x0D -> "DEC C";
            case 0x0E -> "LD C,n";
            case 0x0F -> "RRCA";

            case 0x10 -> "STOP";
            case 0x11 -> "LD DE,nn";
            case 0x12 -> "LD (DE),A";
            case 0x13 -> "INC DE";
            case 0x14 -> "INC D";
            case 0x15 -> "DEC D";
            case 0x16 -> "LD D,n";
            case 0x17 -> "RLA";
            case 0x18 -> "JR n";
            case 0x19 -> "ADD HL,DE";
            case 0x1A -> "LD A,(DE)";
            case 0x1B -> "DEC DE";
            case 0x1C -> "INC E";
            case 0x1D -> "DEC E";
            case 0x1E -> "LD E,n";
            case 0x1F -> "RRA";

            case 0x20 -> "JR NZ,n";
            case 0x21 -> "LD HL,nn";
            case 0x22 -> "LD (HL+),A";
            case 0x23 -> "INC HL";
            case 0x24 -> "INC H";
            case 0x25 -> "DEC H";
            case 0x26 -> "LD H,n";
            case 0x27 -> "DAA";
            case 0x28 -> "JR Z,n";
            case 0x29 -> "ADD HL,HL";
            case 0x2A -> "LD A,(HL+)";
            case 0x2B -> "DEC HL";
            case 0x2C -> "INC L";
            case 0x2D -> "DEC L";
            case 0x2E -> "LD L,n";
            case 0x2F -> "CPL";

            case 0x30 -> "JR NC,n";
            case 0x31 -> "LD SP,nn";
            case 0x32 -> "LD (HL-),A";
            case 0x33 -> "INC SP";
            case 0x34 -> "INC (HL)";
            case 0x35 -> "DEC (HL)";
            case 0x36 -> "LD (HL),n";
            case 0x37 -> "SCF";
            case 0x38 -> "JR C,n";
            case 0x39 -> "ADD HL,SP";
            case 0x3A -> "LD A,(HL-)";
            case 0x3B -> "DEC SP";
            case 0x3C -> "INC A";
            case 0x3D -> "DEC A";
            case 0x3E -> "LD A,n";
            case 0x3F -> "CCF";

            case 0x40 -> "LD B,B";
            case 0x41 -> "LD B,C";
            case 0x42 -> "LD B,D";
            case 0x43 -> "LD B,E";
            case 0x44 -> "LD B,H";
            case 0x45 -> "LD B,L";
            case 0x46 -> "LD B,(HL)";
            case 0x47 -> "LD B,A";
            case 0x48 -> "LD C,B";
            case 0x49 -> "LD C,C";
            case 0x4A -> "LD C,D";
            case 0x4B -> "LD C,E";
            case 0x4C -> "LD C,H";
            case 0x4D -> "LD C,L";
            case 0x4E -> "LD C,(HL)";
            case 0x4F -> "LD C,A";

            case 0x50 -> "LD D,B";
            case 0x51 -> "LD D,C";
            case 0x52 -> "LD D,D";
            case 0x53 -> "LD D,E";
            case 0x54 -> "LD D,H";
            case 0x55 -> "LD D,L";
            case 0x56 -> "LD D,(HL)";
            case 0x57 -> "LD D,A";
            case 0x58 -> "LD E,B";
            case 0x59 -> "LD E,C";
            case 0x5A -> "LD E,D";
            case 0x5B -> "LD E,E";
            case 0x5C -> "LD E,H";
            case 0x5D -> "LD E,L";
            case 0x5E -> "LD E,(HL)";
            case 0x5F -> "LD E,A";

            case 0x60 -> "LD H,B";
            case 0x61 -> "LD H,C";
            case 0x62 -> "LD H,D";
            case 0x63 -> "LD H,E";
            case 0x64 -> "LD H,H";
            case 0x65 -> "LD H,L";
            case 0x66 -> "LD H,(HL)";
            case 0x67 -> "LD H,A";
            case 0x68 -> "LD L,B";
            case 0x69 -> "LD L,C";
            case 0x6A -> "LD L,D";
            case 0x6B -> "LD L,E";
            case 0x6C -> "LD L,H";
            case 0x6D -> "LD L,L";
            case 0x6E -> "LD L,(HL)";
            case 0x6F -> "LD L,A";

            case 0x70 -> "LD (HL),B";
            case 0x71 -> "LD (HL),C";
            case 0x72 -> "LD (HL),D";
            case 0x73 -> "LD (HL),E";
            case 0x74 -> "LD (HL),H";
            case 0x75 -> "LD (HL),L";
            case 0x76 -> "HALT";
            case 0x77 -> "LD (HL),A";
            case 0x78 -> "LD A,B";
            case 0x79 -> "LD A,C";
            case 0x7A -> "LD A,D";
            case 0x7B -> "LD A,E";
            case 0x7C -> "LD A,H";
            case 0x7D -> "LD A,L";
            case 0x7E -> "LD A,(HL)";
            case 0x7F -> "LD A,A";

            case 0x80 -> "ADD A,B";
            case 0x81 -> "ADD A,C";
            case 0x82 -> "ADD A,D";
            case 0x83 -> "ADD A,E";
            case 0x84 -> "ADD A,H";
            case 0x85 -> "ADD A,L";
            case 0x86 -> "ADD A,(HL)";
            case 0x87 -> "ADD A,A";
            case 0x88 -> "ADC A,B";
            case 0x89 -> "ADC A,C";
            case 0x8A -> "ADC A,D";
            case 0x8B -> "ADC A,E";
            case 0x8C -> "ADC A,H";
            case 0x8D -> "ADC A,L";
            case 0x8E -> "ADC A,(HL)";
            case 0x8F -> "ADC A,A";

            case 0x90 -> "SUB B";
            case 0x91 -> "SUB C";
            case 0x92 -> "SUB D";
            case 0x93 -> "SUB E";
            case 0x94 -> "SUB H";
            case 0x95 -> "SUB L";
            case 0x96 -> "SUB (HL)";
            case 0x97 -> "SUB A";
            case 0x98 -> "SBC A,B";
            case 0x99 -> "SBC A,C";
            case 0x9A -> "SBC A,D";
            case 0x9B -> "SBC A,E";
            case 0x9C -> "SBC A,H";
            case 0x9D -> "SBC A,L";
            case 0x9E -> "SBC A,(HL)";
            case 0x9F -> "SBC A,A";

            case 0xA0 -> "AND B";
            case 0xA1 -> "AND C";
            case 0xA2 -> "AND D";
            case 0xA3 -> "AND E";
            case 0xA4 -> "AND H";
            case 0xA5 -> "AND L";
            case 0xA6 -> "AND (HL)";
            case 0xA7 -> "AND A";
            case 0xA8 -> "XOR B";
            case 0xA9 -> "XOR C";
            case 0xAA -> "XOR D";
            case 0xAB -> "XOR E";
            case 0xAC -> "XOR H";
            case 0xAD -> "XOR L";
            case 0xAE -> "XOR (HL)";
            case 0xAF -> "XOR A";

            case 0xB0 -> "OR B";
            case 0xB1 -> "OR C";
            case 0xB2 -> "OR D";
            case 0xB3 -> "OR E";
            case 0xB4 -> "OR H";
            case 0xB5 -> "OR L";
            case 0xB6 -> "OR (HL)";
            case 0xB7 -> "OR A";
            case 0xB8 -> "CP B";
            case 0xB9 -> "CP C";
            case 0xBA -> "CP D";
            case 0xBB -> "CP E";
            case 0xBC -> "CP H";
            case 0xBD -> "CP L";
            case 0xBE -> "CP (HL)";
            case 0xBF -> "CP A";
            
            
            case 0xC0 -> "RET NZ";
            case 0xC1 -> "POP BC";
            case 0xC2 -> "JP NZ,nn";
            case 0xC3 -> "JP nn";
            case 0xC4 -> "CALL NZ,nn";
            case 0xC5 -> "PUSH BC";
            case 0xC6 -> "ADD A,n";
            case 0xC7 -> "RST 00H";
            case 0xC8 -> "RET Z";
            case 0xC9 -> "RET";
            case 0xCA -> "JP Z,nn";
            case 0xCB -> "PREFIX CB";
            case 0xCC -> "CALL Z,nn";
            case 0xCD -> "CALL nn";
            case 0xCE -> "ADC A,n";
            case 0xCF -> "RST 08H";

            case 0xD0 -> "RET NC";
            case 0xD1 -> "POP DE";
            case 0xD2 -> "JP NC,nn";
            case 0xD4 -> "CALL NC,nn";
            case 0xD5 -> "PUSH DE";
            case 0xD6 -> "SUB A,n";
            case 0xD7 -> "RST 10H";
            case 0xD8 -> "RET C";
            case 0xD9 -> "RETI";
            case 0xDA -> "JP C,nn";
            case 0xDC -> "CALL C,nn";
            case 0xDE -> "SBC A,n";
            case 0xDF -> "RST 18H";

            case 0xE0 -> "LDH (nn),A";
            case 0xE1 -> "POP HL";
            case 0xE2 -> "LDH (C),A";
            case 0xE5 -> "PUSH HL";
            case 0xE6 -> "AND A,n";
            case 0xE7 -> "RST 20H";
            case 0xE8 -> "ADD SP,n";
            case 0xE9 -> "JP HL";
            case 0xEA -> "LD (nn),A";
            case 0xEE -> "XOR A,n";
            case 0xEF -> "RST 28H";

            case 0xF0 -> "LDH A,(nn)";
            case 0xF1 -> "POP AF";
            case 0xF2 -> "LDH A,(C)";
            case 0xF3 -> "DI";
            case 0xF5 -> "PUSH AF";
            case 0xF6 -> "OR A,n";
            case 0xF7 -> "RST 30H";
            case 0xF8 -> "LD HL,SP+n";
            case 0xF9 -> "LD SP,HL";
            case 0xFA -> "LD A,(nn)";
            case 0xFB -> "EI";
            case 0xFE -> "CP A,n";
            case 0xFF -> "RST 38H";

            default -> String.format("Unknown (0x%02X)", opcode);
        };
    }

}
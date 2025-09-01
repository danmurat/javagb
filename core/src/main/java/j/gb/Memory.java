package j.gb;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Represents memory in the Game Boy.
 * <br>
 * It will have all addresses set accordingly.
 * <br><br>
 * GameBoy has 16 bit memory bus, so transfers data in 16 bit chunks.. so x0000-xFFFF in hex
 *
 */
public class Memory {
    private static final String ABSOLUTE_ROM_PATH = "/Users/gohan/Documents/projects/javagb/roms/";
    private static final int DIV_COUNTER_RATE = 16384 / 4; // keep at m-cycles

    private CPU cpu;
    private MBC mbc;

    private final short[] romBank0 = new short[0x4000];
    // these can include some sort of banking, so their sizes will be allocated after checking cartridge type
    private short[] romBankN;
    private short[] externalRam;

    // initially we'll load boot and the game cartridge. Then we'll resize.
    private short[] bootAndHeader = new short[0x150];
    private short[] romData;
    private short[] cartridges0xffData = new short[0x100];


    private final short[] videoRam = new short[0x2000];
    //private final short[] externalRam = new short[0x2000];
    private final short[] workRam = new short[0x2000]; // holds both bank 0 and 1 (for now)

    private final short[] echo = new short[0x1E00];

    private int[] spriteTable = new int[0xA0]; // re-assigned in DMA transfer
    private final short[] ioMem = new short[0x80]; // TODO: currenlty throw an error when IO section (readByte()) doesn't find anything, otherwise, use this..

    private final short[] highRam = new short[0x80]; // TODO: the size may be wrong for this (0x7F instead)


    // io interrupt registers (holds a byte (bits 0,1,2,3,4 are the interrupt flags)
    private short IF = 0x00; // interrupt flag
    private short IE = 0x00; // interrupt enable

    // io TIMER registers
    private short DIV = 0x00;
    private short TIMA = 0x00;
    private short TMA = 0x00;
    private short TAC = 0x00;

    // io video registers
    private short LCDC = 0x00; // at 0xFF40
    private short STAT = 0x00; // at 0xFF41
    private short SCY = 0x00; // scroll y at 0xFF42
    private short SCX = 0x00; // scroll x at 0xFF43
    private short LY = 0x90; // at FF44 (use 0x90 for GB DOCTOR!!)
    private short LYC = 0x00; // at FF45
    private short WY = 0x00; // window y pos at FF4A
    private short WX = 0x00; // window x pos (-7) at FF4B
    private short BGP = 0x00; // bg pallete data at FF47
    private short OBP0 = 0x00; // object pallete1 data at FF48
    private short OBP1 = 0x00; // object pallete2 data at FF49
    private short DMA = 0x00; // DMA transfers at FF46

    // access restrictors (for things like ppu modes, vblank, etc..)
    private boolean oamAccessible = true;
    private boolean vramAccessible = true;

    /**
     * Constructs and sets everything we need up.
     * TODO: think about loading rom inside here?
     */
    public Memory(final String romName) throws IOException // handle this..
    {
        // this sequence correctly arranges boot rom and cartridge rom contents
        // once 0xFE instruction runs, 0xFF50 is written to and we swap the original rom contents in place of boot data
        loadBootAndHeader(romName);
        System.out.println("Boot rom and header cartridge loaded.");
        mbcSetup();
        System.out.println("MBC set up complete.");
        loadRom(romName);
        System.out.println("Full cartridge rom loaded.");
        fillBootRomData();
        System.out.println("$0-ff address range swapped with boot rom data.");
        // below is for running/testing boot rom only
        //loadBootRomOnly();

        // below is for running cartridge only (remember to re-set cpu registers from 0x100, etc..)
      /*  loadBootAndHeader(romName);
        loadRom(romName);
        mbcSetup(); // we don't fill boot data*/
    }

    /**
     * Memory and CPU both require eachother, but 1 is created before the other, so here we set the cpu
     * after the CPU is subsequently created
     * @param cpu The cpu object
     */
    public void setCPU(CPU cpu) {
        this.cpu = cpu;
    }

   /* public void setMBC(MBC mbc) {
        this.mbc = mbc;
    }*/

    public short getIF() {
        return IF;
    }

    public short getIE() {
        return IE;
    }

    public void setOamAccessible(final boolean setOamAccess) {
        oamAccessible = setOamAccess;
    }

    public void setVramAccessible(final boolean setVramAccess) {
        vramAccessible = setVramAccess;
    }

    public short[] getRomData() {
        return romData;
    }

    public void setEram(final int address, final short value) {
        externalRam[address] = value;
    }

    public short[] getEram() {
        return externalRam;
    }

    /// this indicates whether what mode we access in VRAM
    public int getLCDCbit0() {
        return LCDC & 0x01;
    }

    /// object enable
    public int getLCDCbit1() {
        final int bit1 = 0b10;
        return (LCDC & bit1) >> 1;
    }

    /// determines ALL objects size's. If on, objs will be 8x16 instead.
    public int getLCDCbit2() {
        final int bit2 = 0b100;
        return (LCDC & bit2) >> 2;
    }

    /// background tile map area: if 0, area = $9800, 1 = $9C00
    public int getLCDCbit3() {
        final int bit3 = 0b1000;
        return (LCDC & bit3) >> 3;
    }

    /// determines the addressing mode for getting background/window tiles
    public int getLCDCbit4() {
        final int bit4 = 0b10000;
        return (LCDC & bit4) >> 4;
    }

    /// indicates wether the window is displayed
    public int getLCDCbit5() {
        final int bit5 = 0b100000;
        return (LCDC & bit5) >> 5;
    }

    /// window tile map area: if 0, area = $9800, else $9C00 (like bit3 above)
    public int getLCDCbit6() {
        final int bit6 = 0b1000000;
        return (LCDC & bit6) >> 6;
    }

    /// LCD enable bit, to check if screen should be on or not
    public int getLCDCbit7() {
        final int bit7 = 0b10000000;
        return (LCDC & bit7) >> 7;
    }



    /**
     * Reads 8bit info from somewhere...
     * <br>
     * Note: address is (int) because all primitives are signed (+ and -) so short doesn't reach xFFFF (because it's cut in half essentially)
     * @param address the memory address we access
     */
    public short readByte(final int address) {
        // so the address can be anywhere from 0x0000 to 0xFFFF, which is the whole range of memory (64kbyts)
        // memory map

        // ROM BANK 0 and 1
        if (0x0000 <= address && address <= 0x7FFF) {
            // this should now handle appropriately depending on MBC type
            return (short) mbc.handleRomRead(address);
        } else if (0x8000 <= address && address <= 0x9FFF) { // 8kb VRAM
            if (vramAccessible) return videoRam[address - 0x8000]; // minus is to get absolute position
            else return 0xFF; // read junk val
        } else if (0xA000 <= address && address <= 0xBFFF) { // 8kb external ram
            return (short) mbc.handleRamRead(address);
        } else if (0xC000 <= address && address <= 0xDFFF) { // handles bank0 and 1 together
            return workRam[address - 0xC000];
        } else if (0xE000 <= address && address <= 0xFDFF) { // == C000 to DDFF ECHO (not really used) no idea what this is
            return echo[address - 0xE000];
        } else if (0xFE00 <= address && address <= 0xFE9F) { // sprite attribute table
            if (oamAccessible) return (short) spriteTable[address - 0xFE00];
            else return 0xFF; // junk value
        } else if (0xFEA0 <= address && address <= 0xFEFF) { // not usable ...
            // TODO: return something appropriate
            //throw new RuntimeException("implement do nothing for addresses 0xFEA0 to 0xFEFF");
            // for now just return 0
            return 0;
        } else if (0xFF00 <= address && address <= 0xFF7F) { // IO ports
            return switch (address) {
                // timer addresses
                case 0xFF04 -> DIV;
                case 0xFF05 -> TIMA;
                case 0xFF06 -> TMA;
                case 0xFF07 -> TAC;
                // interrupt address
                case 0xFF0F -> IF;
                // video
                case 0xFF40 -> LCDC;
                case 0xFF41 -> STAT;
                case 0xFF42 -> SCY;
                case 0xFF43 -> SCX;
                case 0xFF44 -> LY;
                case 0xFF45 -> LYC;
                case 0xFF46 -> DMA;
                case 0xFF47 -> BGP;
                case 0xFF48 -> OBP0;
                case 0xFF49 -> OBP1;
                case 0xFF4A -> WY;
                case 0xFF4B -> WX;
                default -> ioMem[address - 0xFF00];
            };
        } else if (0xFF80 <= address && address <= 0xFFFE) { // high ram
            return highRam[address - 0xFF80];
        } else if (address == 0xFFFF) { // Interrupt enable register
            return IE;
        } else {
            throw new RuntimeException("Invalid address: " + address + "\nAddress needs to be between 0x0000 and 0xFFFF");
        }
    }

    /**
     * Writes a value to the specified address
     * <br><br>
     * NOTE: we use SHORT because all types are signed (have negatives) so short upper limit == unsigned byte
     * @param address the specified memory address
     * @param value the byte value to write at that address
     */
    public void writeByte(final int address, final short value) {
        /*
        The rom addresses ($0000-3FFF for bank 0) ($4000-7FFF for bank n) should not be writable (since they're rom).
        However, aspects of bank switching will cause a write to these (depending on what it's trying to accomplish).
        WE DO NOT ACTUALLY WRITE TO THE ROMS HOWEVER!
        The MBC (Memory Bank Controller) will 'intercept' these writes instead.
        So when 0xA is written to any address between $0000-1FFF, the MBC sees this and 'enables RAM'.
        0xnn written to $2000-3FFF, MBC will specify the bank number for bank n.
        And so on.
         */
        if (0x0000 <= address && address <= 0x7FFF) {
            mbc.handleRomWrite(address, value);
        } else if (0x8000 <= address && address <= 0x9FFF) { // 8kb vram
            if (vramAccessible) videoRam[address - 0x8000] = value; // minus is to get absolute position
            // otherwise do nothing (so access is blocked)
        } else if (0xA000 <= address && address <= 0xBFFF) { // 8kb external ram
            mbc.handleRamWrite(address, value);
        } else if (0xC000 <= address && address <= 0xDFFF) { // handles both banks 0/1
            workRam[address - 0xC000] = value;
        } else if (0xE000 <= address && address <= 0xFDFF) { // == C000 to DDFF ECHO (not really used) no idea what this is
            echo[address - 0xE000] = value;
        } else if (0xFE00 <= address && address <= 0xFE9F) { // sprite attribute table
            if (oamAccessible) spriteTable[address - 0xFE00] = value; // same as vram write
        } else if (0xFEA0 <= address && address <= 0xFEFF) { // not usable ...
            // TODO: return something appropriate
            throw new RuntimeException("implement do nothing for addresses 0xFEA0 to 0xFEFF");
        } else if (0xFF00 <= address && address <= 0xFF7F) { // IO ports
            switch (address) {
                // timer addresses
                case 0xFF04 -> DIV = 0x00; // is reset
                case 0xFF05 -> TIMA = value;
                case 0xFF06 -> TMA = value;
                case 0xFF07 -> TAC = value; // TODO: add & 0x07 and update TIMA counter
                // interrupt address
                case 0xFF0F -> IF = value;
                // video
                case 0xFF40 -> LCDC = value;
                case 0xFF41 -> STAT = value;
                case 0xFF42 -> SCY = value;
                case 0xFF43 -> SCX = value;
                case 0xFF44 -> LY = value; // ly is also reset?? (why did i just set to 0?)
                case 0xFF45 -> LYC = value;
                case 0xFF46 -> dmaTransfer(value);
                case 0xFF47 -> BGP = value;
                case 0xFF48 -> OBP0 = value;
                case 0xFF49 -> OBP1 = value;
                case 0xFF4A -> WY = value;
                case 0xFF4B -> WX = value;
                case 0xFF50 -> {
                    if (value == 1) replaceBootRomData();
                }
                default -> ioMem[address - 0xFF00] = value;
            }
        } else if (0xFF80 <= address && address <= 0xFFFE) { // high ram
            highRam[address - 0xFF80] = value;
        } else if (address == 0xFFFF) { // Interrupt enable register
            IE = value;
        } else {
            throw new RuntimeException("Invalid address: " + address + "\nAddress needs to be between 0x8000 and 0xFFFF");
        }
    }

    /**
     * Since we have some instruction that read/write 16bits, we create a method to read/write words.
     * <br><br>
     * NOTE: the GameBoy is a little-endian system. So the lower memory address is the least significant byte
     * (the right-most). So we bit shift the value at the next address to the left by 8.
     *
     * @param address the primary address to access
     * @return word
     */
    public int readWord(final int address) {
        // little endian
        return (readByte(address+1) << 8) | readByte(address);
    }

    /**
     * Follows same concept as readWord, but for writing.
     * @param address the primary address access
     * @param value the value we want to write
     */
    public void writeWord(final int address, final int value) {
        final int value1 = 0x00FF & value;        // least significant byte only
        final int value2 = value >> 8; // most significant byte, (bit shifted to remove low byte)
        writeByte(address, (short)value1);
        writeByte(address+1, (short)value2);
    }


    /**
     * Since ppu may block access to vram/oam memory, we still need a way for it to access those locations
     * when blocked (blockage is meant for cpu).
     */
    public int ppuReadVram(final int address) {
        if (0x8000 <= address && address <= 0x9FFF) {
            return videoRam[address - 0x8000];
        } else {
            throw new RuntimeException("Invalid address: " + address + "\nAddress needs to be between 0x8000 and 0x9FFF");
        }
    }

    public int ppuReadOam(final int address) {
         if (0xFE00 <= address && address <= 0xFE9F) {
            return spriteTable[address - 0xFE00];
        } else {
            throw new RuntimeException("Invalid address: " + address + "\nAddress needs to be between 0xFE00 and 0xFE9F");
        }
    }


    // reading interrept flag info

    // if

    /**
     * Get's the boolean value of bit 0 from IF
     * @return boolean vblank value
     */
    public boolean vBlankRequest() {
        final int bit0 = IF & 0b00000001; // and only bit 0 then checks if that bit was on
        return bit0 == 0b00000001;
    }

    public boolean lcdRequest() {
        final int bit1 = IF & 0b00000010;
        return bit1 == 0b00000010;
    }

    public boolean timerRequest() {
        final int bit2 = IF & 0b00000100;
        return bit2 == 0b00000100;
    }

    public boolean serialRequest() {
        final int bit3 = IF & 0b00001000;
        return bit3 == 0b00001000;
    }

    public boolean joypadRequest() {
        final int bit4 = IF & 0b00010000;
        return bit4 == 0b00010000;
    }

    // interrupt enables
    public boolean vBlankEnable() {
        final int bit0 = IE & 0b00000001; // and only bit 0 then checks if that bit was on
        return bit0 == 0b00000001;
    }

    public boolean lcdEnable() {
        final int bit1 = IE & 0b00000010;
        return bit1 == 0b00000010;
    }

    public boolean timerEnable() {
        final int bit2 = IE & 0b00000100;
        return bit2 == 0b00000100;
    }

    public boolean serialEnable() {
        final int bit3 = IE & 0b00001000;
        return bit3 == 0b00001000;
    }

    public boolean joypadEnable() {
        final int bit4 = IE & 0b00010000;
        return bit4 == 0b00010000;
    }

    public void vBlankRequestReset() {
        IF = (short) (IF & 0b11111110); // keep all bits same, but resets 0bit to 0
    }

    public void lcdRequestReset() {
        IF = (short) (IF & 0b11111101);
    }

    public void timerRequestReset() {
        IF = (short) (IF & 0b11111011);
    }

    public void serialRequestReset() {
        IF = (short) (IF & 0b11110111);
    }

    public void joypadRequestReset() {
        IF = (short) (IF & 0b11101111);
    }

    public int getTwosCompliment(int num) {
        // most significant bit == 1, it's negative
        if ((num & 0b10000000) == 0b10000000) {
            return -((~num + 1) & 0xFF); // flip all bits, add 1 (xFF ensures we keep only 8 bits)
        } else {
            return num;
        }
    }

    /**
     * DMA transfer. To load memory contents into OAM when xFF46 is written to
     *
     * TODO: this can only be done during VBlank/HBlank!!
     */
    public void dmaTransfer(final int xx) {
        // we take all the contents specifed by the xx in memory
        final int[] memToBeLoaded = new int[0xA0];
        final int higherByteAddress = xx << 8;
        for (int i = 0x00; i < 0x9F; i++) {
            memToBeLoaded[i] = readByte(higherByteAddress | i); // gives us correct addr to access
        }

        spriteTable = memToBeLoaded;
        cpu.setTotalMCycles(cpu.getTotalMCycles() + 160); // dma transf takes 160 m cycles
    }

    /**
     * Helps with testing. <br>
     * It handles a few cases so that it looks perfectly symmetrical.
     */
 /*   public void hexDumpRomContents()
    {
        int addressCounter = 0;
        while (addressCounter < romData.length)
        {
            System.out.print("Address: " + Integer.toHexString(addressCounter) + " |");
            // handle the 0 case
            if (addressCounter % 16 == 0 && addressCounter < romData.length)
            {
                short value = romData[addressCounter];
                String hexValue = Integer.toHexString(value);
                if (value < 10)
                {
                    hexValue = "0" + hexValue;
                }
                else if (value < 16)
                {
                    hexValue = hexValue + "0";
                }
                System.out.print(" " + hexValue);
                addressCounter++;
            }
            while (addressCounter % 16 != 0 && addressCounter < romData.length)
            {
                short value = romData[addressCounter];
                String hexValue = Integer.toHexString(value);
                if (value < 10)
                {
                    hexValue = "0" + hexValue;
                }
                else if (value < 16)
                {
                    hexValue = hexValue + "0";
                }
                System.out.print(" " + hexValue);
                addressCounter++;
            }
            System.out.println(); // new line
        }

    } */

    private void loadBootAndHeader(final String romName) throws IOException {
        final FileInputStream bootFile = new FileInputStream(ABSOLUTE_ROM_PATH + "boot.bin");
        final FileInputStream romFile = new FileInputStream(ABSOLUTE_ROM_PATH + romName);
        int addressCounter = 0;

        while (bootFile.available() > 0) {
            bootAndHeader[addressCounter++] = (short) bootFile.read();
        }
        bootFile.close();

        addressCounter = 0; // reset
        while (addressCounter < bootAndHeader.length) {
            if (addressCounter < 0x100) {
                romFile.read(); // throw away first $100, to not overwrite boot
                addressCounter++;
            } else {
                bootAndHeader[addressCounter++] = (short) romFile.read();
            }
        }
        romFile.close();
    }

    // done after loading boot/header + mbcSetup, so we've assigned a size for romData.
    private void loadRom(final String romName) throws IOException {
        final FileInputStream romFile = new FileInputStream(ABSOLUTE_ROM_PATH + romName);
        int addressCounter = 0;

        while (romFile.available() > 0) {
            romData[addressCounter++] = (short) romFile.read();
        }
        romFile.close();
    }

    /**
     * After loading boot/header and then the whole romData, we need to swap the first xFF addresses with the
     * boot rom before running instructions.
     */
    private void fillBootRomData() {
        // we save the cartridge's data then swap in the boot data. (cartridge's data will be swapped back in after bootup).
        for (int i = 0; i < 0x100; i++) {
            cartridges0xffData[i] = romData[i];
            romData[i] = bootAndHeader[i];
        }
    }

    /**
     * after bootrom ends games need their original $0-ff data they may have had
     */
    private void replaceBootRomData() {
        for (int i = 0; i < 0x100; i++) {
            romData[i] = cartridges0xffData[i];
        }
    }

    /**
     * FOR TESTING BOOT ROM ONLY. Will load its contents + the logo data correctly
     */
    private void loadBootRomOnly() throws IOException {
        final FileInputStream bootFile = new FileInputStream(ABSOLUTE_ROM_PATH + "boot.bin");
        int addressCounter = 0;
        romData = new short[0x4000];

        while (bootFile.available() > 0) {
            romData[addressCounter++] = (short) bootFile.read();
        }
        loadLogo();
        // must set up so we can do read/writes
        mbc = new MBC(this);
        mbc.setType(MbcType.ROM_ONLY);
    }


    /**
     * This will check the cartridge type (from 0x147) and appropriately allocate the sizes for
     * our remaining rombank and external ram
     */
    private void mbcSetup() {
        mbc = new MBC(this);

        final int cartridgeType = bootAndHeader[0x147];
        // romSize is here only so we're able to run individual cpu tests (they're under mbc1 for some reason).
        // NOW INDIVDUALS RUN WITHOUT THIS! (prob since we fixed the +1 banking select bug)
        //final int romSize = romBank0[0x148];

        if (cartridgeType == 0x0) { // ROM ONLY
            System.out.println("ROM ONLY Cartridge.");
            mbc.setType(MbcType.ROM_ONLY);
            romData = new short[0x8000];
            externalRam = new short[0x2000];
        } else if (cartridgeType == 0x01 || cartridgeType == 0x02 || cartridgeType == 0x03) {
            // one of the MBC1's (we just apply the maximum sizes, 2MB rom and 32kb ram)
            System.out.println("MBC1 Cartridge.");
            mbc.setType(MbcType.MBC_1);
            romData = new short[2000000];
            externalRam = new short[32000];
        }
    }



    /**
     * To help debug the boot rom. This takes the logo values from the carteridge (not from within the boot rom) and
     * so we inject this in from addr $104-133
     */
    public void loadLogo() {
        final int[] logoData = {0xCE, 0xED,0x66,0x66,0xCC,0x0D,0x00,0x0B,0x03,0x73,0x00,0x83,0x00,0x0C,0x00,0x0D,
            0x00,0x08,0x11,0x1F,0x88,0x89,0x00,0x0E,0xDC,0xCC,0x6E,0xE6,0xDD,0xDD,0xD9,0x99,
            0xBB,0xBB,0x67,0x63,0x6E,0x0E,0xEC,0xCC,0xDD,0xDC,0x99,0x9F,0xBB,0xB9,0x33,0x3E};

        final int startAddress = 0x104;

        for (int i = 0; i < logoData.length; i++) {
            romData[startAddress + i] = (short) logoData[i];
        }
    }

    /**
     * The DIV register increments at a rate of 16384Hz. Since writing directly to the memory address will reset it,
     * this method is used to actually increment at the desired speed outside this class.
     */
    private void incrementDiv() {
        DIV++; // PanDocs says nothing about overflow for this.. I'll handle it anyway
        if (DIV > 0xFF) {
            DIV = 0x00;
        }
    }

    /**
     * Same concept as DIV, but there are a few extras involved. TIMA increments depend on TAC (handled in Run.java).
     * When this overflows, an interrupt occurs.
     */
    private void incrementTIMA() {
        TIMA++;
        if (TIMA > 0xFF) {
            TIMA = TMA;
            // interrupt request at bit 3 for timer (OR into place to change only bit 3)
            IF = (short) (IF | 0b00000100);
        }
    }

    // these handleIncrements are called in Run.java (execute instruction) and CPU.java inside the
    // HALT instruction (while waiting for interrupt)
    public void handleDIVIncrement(final int mCycleCount) {
        if (mCycleCount % DIV_COUNTER_RATE == 0) {
            incrementDiv();
        }
    }

    public void handleTIMAIncrement(final int mCycleCount) {
        final int[] tacValues = getTimerControlValues();

        // timer enable on and cycleCount reaches desired increment rate
        if (tacValues[1] == 1 && mCycleCount % tacValues[0] == 0) {
            incrementTIMA();
        }
    }

    private int[] getTimerControlValues() throws RuntimeException {
        final int TAC = readByte(0xFF07);

        final int clockSelect = TAC & 0b00000011; // first 2 bits only
        final int timerEnable = TAC >> 2; // 3rd bit only

        final int clockSelectVal = switch (clockSelect) {
            case 0 -> 256; // in m-cycles
            case 1 -> 4;
            case 2 -> 16;
            case 3 -> 64;
            default -> throw new RuntimeException("clockSelect value should be between 0-3, but is " + clockSelect);
        };

        return new int[] {clockSelectVal, timerEnable};
    }

}

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
public class Memory
{
    private static final String ROM_PATH = "roms/";
    private CPU cpu;

                                        // TODO: change back to 0x8000. But the rom files are bigger than this??
    private final short[] romData = new short[0x10000]; // load rom data here (holds both banks)
    private int currentBank = 0;

    private final short[] videoRam = new short[0x2000];
    private final short[] externalRam = new short[0x2000];
    private final short[] workRam = new short[0x1000]; // holds both bank 0 and 1 (for now)

    private final short[] echo = new short[0x1E00];

    private final short[] spriteTable = new short[0xA0];
    private final short[] ioMem = new short[0x80]; // TODO: currenlty throw an error when IO section (readByte()) doesn't find anything, otherwise, use this..

    private final short[] highRam = new short[0x80]; // TODO: the size may be wrong for this (0x7F instead)


    // io interrupt registers (should these not be booleans?)
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
    private short LY = 0x00; // at FF44
    private short LYC = 0x00; // at FF45
    private short WY = 0x00; // window y pos at FF4A
    private short WX = 0x00; // window x pos (-7) at FF4B
    private short BGP = 0x00; // bg pallete data at FF47
    private short OBP0 = 0x00; // object pallete1 data at FF48
    private short OBP1 = 0x00; // object pallete2 data at FF49
    private short DMA = 0x00; // DMA transfers at FF46

    /**
     * Constructs and sets everything we need up.
     * TODO: think about loading rom inside here?
     */
    public Memory(final String romName) throws IOException // handle this..
    {
        //this.cpu = cpu;
        loadRom(romName); // romData filled
    }

    /**
     * Memory and CPU both require eachother, but 1 is created before the other, so here we set the cpu
     * after the CPU is subsequently created
     * @param cpu The cpu object
     */
    public void setCPU(CPU cpu) {
        this.cpu = cpu;
    }

    /**
     * Reads 16bit info from somewhere...
     * <br>
     * Note: address is (int) because all primitives are signed (+ and -) so short doesn't reach xFFFF (because it's cut in half essentially)
     * @param address the memory address we access
     */
    public short readByte(final int address)
    {

        // so the address can be anywhere from 0x0000 to 0xFFFF, which is the whole range of memory (16kbyts)

        // memory map

        if (0x0000 <= address && address <= 0x3FFF) // ROM BANK 0
        {
            return romData[address];
        }
        else if (0x4000 <= address && address <= 0x7FFF) // ROM Bank 1 or 01..n ??
        {
            // the old code was doing an offset for this, but I'm not sure why it's needed
            // (could we just pretend there's no banks?) TODO: investigate this
            return romData[address];
        }
        else if (0x8000 <= address && address <= 0x9FFF) // 8kb VRAM
        {
            return videoRam[0x8000 - address]; // minus is to get absolute position
        }
        else if (0xA000 <= address && address <= 0xBFFF) // 8kb external ram
        {
            return externalRam[0xA000 - address];
        }
        else if (0xC000 <= address && address <= 0xCFFF) // 4kb work ram Bank 0
        {
            return workRam[0xC000 - address];
        }
        else if (0xD000 <= address && address <= 0xDFFF) // 4kb work ram bank 1
        {
            return workRam[0xD000 - address]; // re
        }
        else if (0xE000 <= address && address <= 0xFDFF) // == C000 to DDFF ECHO (not really used) no idea what this is
        {
            return echo[0xE000 - address];
        }
        else if (0xFE00 <= address && address <= 0xFE9F) // sprite attribute table
        {
            return spriteTable[0xFE00 - address];
        }
        else if (0xFEA0 <= address && address <= 0xFEFF) // not usable ...
        {
            // TODO: return something appropriate
            throw new RuntimeException("implement do nothing for addresses 0xFEA0 to 0xFEFF");
        }
        else if (0xFF00 <= address && address <= 0xFF7F) // IO ports
        {
            switch (address)
            {
                // timer addresses
                case 0xFF04:
                    return DIV;
                case 0xFF05:
                    return TIMA;
                case 0xFF06:
                    return TMA;
                case 0xFF07:
                    return TAC;
                // interrupt address
                case 0xFF0F:
                    return IF;
                // video
                case 0xFF40:
                    return LCDC;
                case 0xFF41:
                    return STAT;
                case 0xFF42:
                    return SCY;
                case 0xFF43:
                    return SCX;
                case 0xFF44:
                    return LY;
                case 0xFF45:
                    return LYC;
                case 0xFF46:
                    return DMA;
                case 0xFF47:
                    return BGP;
                case 0xFF48:
                    return OBP0;
                case 0xFF49:
                    return OBP1;
                case 0xFF4A:
                    return WY;
                case 0xFF4B:
                    return WX;
                default:
                    throw new RuntimeException("unknown address 0x" + Integer.toHexString(address));
            }
        }
        else if (0xFF80 <= address && address <= 0xFFFE) // high ram
        {
            return highRam[0xFF80 - address];
        }
        else if (address == 0xFFFF) // Interrupt enable register
        {
            return IE;
        }
        else
        {
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
    public void writeByte(final int address, final short value)
    {

        if (0x0000 <= address && address <= 0x3FFF) // ROM BANK 0
        {
            romData[address] = value;
        }
        else if (0x4000 <= address && address <= 0x7FFF) // ROM Bank 1 or 01..n ??
        {
            // the old code was doing an offset for this, but I'm not sure why it's needed
            // (could we just pretend there's no banks?) TODO: investigate this
            romData[address] = value;
        }
        else if (0x8000 <= address && address <= 0x9FFF) // 8kb VRAM
        {
            videoRam[0x8000 - address] = value; // minus is to get absolute position
        }
        else if (0xA000 <= address && address <= 0xBFFF) // 8kb external ram
        {
            externalRam[0xA000 - address] = value;
        }
        else if (0xC000 <= address && address <= 0xCFFF) // 4kb work ram Bank 0
        {
            workRam[0xC000 - address] = value;
        }
        else if (0xD000 <= address && address <= 0xDFFF) // 4kb work ram bank 1
        {
            workRam[0xD000 - address] = value; // re
        }
        else if (0xE000 <= address && address <= 0xFDFF) // == C000 to DDFF ECHO (not really used) no idea what this is
        {
            echo[0xE000 - address] = value;
        }
        else if (0xFE00 <= address && address <= 0xFE9F) // sprite attribute table
        {
            spriteTable[0xFE00 - address] = value;
        }
        else if (0xFEA0 <= address && address <= 0xFEFF) // not usable ...
        {
            // TODO: return something appropriate
            throw new RuntimeException("implement do nothing for addresses 0xFEA0 to 0xFEFF");
        }
        else if (0xFF00 <= address && address <= 0xFF7F) // IO ports
        {
            switch (address)
            {
                // timer addresses
                case 0xFF04:
                    DIV = 0x00; // is reset
                case 0xFF05:
                    TIMA = value;
                case 0xFF06:
                    TMA = value;
                case 0xFF07:
                    TAC = value; // TODO: add & 0x07 and update TIMA counter
                // interrupt address
                case 0xFF0F:
                    IF = value;
                // video
                case 0xFF40:
                    LCDC = value;
                case 0xFF41:
                    STAT = value;
                case 0xFF42:
                    SCY = value;
                case 0xFF43:
                    SCX = value;
                case 0xFF44:
                    LY = 0x00; // ly is also reset
                case 0xFF45:
                    LYC = value;
                case 0xFF46:
                    DMA = value;
                case 0xFF47:
                    BGP = value;
                case 0xFF48:
                    OBP0 = value;
                case 0xFF49:
                    OBP1 = value;
                case 0xFF4A:
                    WY = value;
                case 0xFF4B:
                    WX = value;
                default:
                    throw new RuntimeException("unknown address 0x" + Integer.toHexString(address));
            }
        }
        else if (0xFF80 <= address && address <= 0xFFFE) // high ram
        {
            highRam[0xFF80 - address] = value;
        }
        else if (address == 0xFFFF) // Interrupt enable register
        {
            IE = value;
        }
        else
        {
            throw new RuntimeException("Invalid address: " + address + "\nAddress needs to be between 0x0000 and 0xFFFF");
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
        return readByte(address) | (readByte(address+1) << 8);
    }

    /**
     * Follows same concept as readWord, but for writing.
     * @param address the primary address access
     * @param value the value we want to write
     */
    public void writeWord(final int address, final int value) {
        final int value1 = 0x00FF & value;        // least significant byte only
        final int value2 = (0xFF00 & value) >> 8; // most significant byte, then bit shifted to become an actual byte
        writeByte(address, (short)value1);
        writeByte(address+1, (short)value2);
    }


    /**
     * Helps with testing. <br>
     * It handles a few cases so that it looks perfectly symmetrical.
     */
    public void hexDumpRomContents()
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

    }


    /**
     * Dumps data from rom file into our array
     * @param romName the name of our rom we run
     */
    private void loadRom(String romName) throws IOException
    {
        final FileInputStream romFile = new FileInputStream(ROM_PATH + romName);
        int addressCounter = 0;
        while (romFile.available() > 0)
        {
            romData[addressCounter++] = (short)romFile.read();

            if (addressCounter == romData.length)
            {
                romFile.close(); // TODO: we may be closing this too early? Roms can have more data in them than total mem size
                break;
            }
        }
    }
}

package j.gb;

/**
 * Memory Bank Controller.
 * This class will handle MBC functionality that the game cartridges had.
 * Methods here will 'intercept' some writes into Memory (which are intended for the MBC) so that it
 * handles the modes and switching between the different banks of ROM and RAM that the cartridge may hold.
 */
public class MBC {
    private static final String ROM_ADDRESS_OUT_OF_RANGE = "Address is out of range $0-7FFF. Address = %d";
    private static final String UNIMPLEMENTED_ROM_READ_TYPE = "We have not implemented the %s type yet for ROM reads.";
    private static final String UNIMPLEMENTED_ROM_WRITE_TYPE = "We have not implemented the %s type yet for ROM writes.";
    private static final String UNIMPLEMENTED_RAM_READ_TYPE = "We have not implemented the %s type yet for RAM reads.";
    private static final String UNIMPLEMENTED_RAM_WRITE_TYPE = "We have not implemented the %s type yet for RAM writes.";

    private MbcType type;
    private Memory memory;

    // MBC1 variables
    private boolean mbcON = false;
    private int currentRomBank = 0;
    private int currentRamBank = 0;
    private boolean mbc1EramEnable = false;
    private int mbc1Mode = 0;


    public MBC(Memory mem) {
        memory = mem;
    }

    // in some method that recognises a rom bank switch for a specific MBC, this would
    // make romBank1 re-read data at the specified address (where the cartridge holds the rom bank)

    public void setType(MbcType type) {
        this.type = type;
    }

    public MbcType getType() {
        return this.type;
    }

    public void setCurrentRomBank(int num) {
        currentRomBank = num;
    }

    public int getCurrentRomBank() {
        return currentRomBank;
    }

    public void setCurrentRamBank(int num) {
        currentRamBank = num;
    }

    public int getCurrentRamBank() {
        return currentRamBank;
    }

    /**
     * Gives the correct rom location based off our mbc types.
     */
    public int handleRomRead(final int address) {
        return switch (type) {
            case ROM_ONLY -> romOnlyHandleRomRead(address);
            case MBC_1 -> mbc1HandleRomRead(address);
            default -> throw new RuntimeException(String.format(UNIMPLEMENTED_ROM_READ_TYPE, type));
        };
    }

    /**
     * When cpu writes to a rom address, we'll handle the MBC specific routing
     */
    public void handleRomWrite(final int address, final int value) {
        switch (type) {
            case ROM_ONLY -> romOnlyHandleRomWrite();
            case MBC_1 -> mbc1HandleRomWrite(address, value);
            default -> throw new RuntimeException(String.format(UNIMPLEMENTED_ROM_WRITE_TYPE, type));
        }
    }

    /**
     * Same as handleRomRead but for External Ram.
     */
    public int handleRamRead(final int address) {
        return switch (type) {
            case ROM_ONLY -> romOnlyHandleRamRead(address);
            case MBC_1 -> mbc1HandleRamRead(address);
            default -> throw new RuntimeException(String.format(UNIMPLEMENTED_RAM_READ_TYPE, type));
        };
    }

    /**
     * Same as handleRomWrite but for eram location.
     */
    public void handleRamWrite(final int address, final int value) {
        switch (type) {
            case ROM_ONLY -> romOnlyHandleRamWrite(address, value);
            case MBC_1 -> mbc1HandleRamWrite(address, value);
            default -> throw new RuntimeException(String.format(UNIMPLEMENTED_RAM_WRITE_TYPE, type));
        }
    }

    // ---- ROM_ONLY methods ----

    private int romOnlyHandleRomRead(final int address) {
        if (0x0000 <= address && address <= 0x3FFF) {
            return memory.getRomBank0()[address];
        } else if (0x4000 <= address && address <= 0x7FFF) {
            // remember we need to scale the actual array index back 0x4000!
            return memory.getRomBankN()[address - 0x4000];
        } else {
            throw new RuntimeException(String.format(ROM_ADDRESS_OUT_OF_RANGE, address));
        }
    }
    // we do nothing since gb can't write to rom
    private void romOnlyHandleRomWrite() {}

    private int romOnlyHandleRamRead(final int address) {
        return memory.getEram()[address - 0xA000];
    }

    private void romOnlyHandleRamWrite(final int address, final int value) {
        // no mbc may still require an eram enable to use this addressing... https://gbdev.io/pandocs/nombc.html
        memory.setEram(address - 0xA000, (short) value);
    }

    // --- End of ROM_ONLY methods ---


    // ---- MBC_1 methods ----

    private int mbc1HandleRomRead(final int address) {
        if (0x0000 <= address && address <= 0x3FFF) {
            if (mbc1Mode == 0) return memory.getRomBank0()[address];
                // mbc1 mode 1 allows for this address range (bank 0) to be banked and swapped too.
            else return memory.getRomBankN()[(address + 0x4000) + romBankNumScaler()];
        } else if (0x4000 <= address && address <= 0x7FFF){
            return memory.getRomBankN()[(address - 0x4000) + romBankNumScaler()];
        } else {
            throw new RuntimeException(String.format(ROM_ADDRESS_OUT_OF_RANGE, address));
        }
    }

    private void mbc1HandleRomWrite(final int address, final int value) {
        if (0x0000 <= address && address <= 0x1FFF) {
            mbc1RamEnableCheck(value);
        } else if (0x2000 <= address && address <= 0x3FFF) {
            /*
            Big note:
            For some reason, adding + 1 fixes the cpu_instrs.gb (such that all the test numbers appear as passing too).
            Without the + 1, when setting a breakpoint here, this code only ever runs once. It is written to with
            value = 1 (so no switch), then no more breaks occur and our test finishes. This doesn't make sense since
            cpu_instrs should be made up of 4 rom banks, only 1 call to switch doesn't make sense.

            Adding + 1 seems to make it access the correct intended bank, and thus accesses all other banks (2,3 and 4)

            But PanDocs says that if value = 0, then it should = 1 (for rom bank 1). But if the value is 1, then it should
            also be 1 (for rom bank 1)?? This is very confusing, and still we've not completely implemented correctly.

            FIXED: ignore above
            It turns out we were not accessing the start of romBankN's array. We were writing to the array from file
            starting at index 0x4000, so all the banks were ahead by 1!
             */
            mbc1HandleRomBankNum(value);
        } else if (0x4000 <= address && address <= 0x5FFF) {
            mbc1HandleRamOrUpperRomBank(value);
        } else if (0x6000 <= address && address <= 0x7FFF) {
            mbc1HandleBankMode(value);
        } else {
            throw new RuntimeException(String.format(ROM_ADDRESS_OUT_OF_RANGE, address));
        }
    }

    private int mbc1HandleRamRead(final int address) {
        if (mbc1EramEnable) {
            mbc1EramEnable = false;
            return memory.getEram()[(address - 0xA000) + ramBankNumScaler()];
        } else {
            return 0xFF; // junk value
            // or maybe just the gb's built in vram? (as in, no scaling)
        }
    }

    private void mbc1HandleRamWrite(final int address, final int value) {
        if (mbc1EramEnable) {
            mbc1EramEnable = false;
            memory.setEram((address - 0xA000) + ramBankNumScaler(), (short) value);
        }
    }

    private void mbc1RamEnableCheck(final int writeValue) {
        // lower byte only
        if ((writeValue & 0xF) == 0xA) {
            mbc1EramEnable = true;
        } else {
            mbc1EramEnable = false;
        }
    }

    private void mbc1HandleRomBankNum(final int writeValue) {
        final int lower5bits = writeValue & 0b11111;
        if (lower5bits == 0) {
            currentRomBank = 1;
        } else {
            currentRomBank = lower5bits;
        }
    }

    private void mbc1HandleBankMode(final int writeValue) {
        if (writeValue == 0) {
            mbc1Mode = 0;
        } else if (writeValue == 1) {
            mbc1Mode = 1;
        }
    }

    private void mbc1HandleRamOrUpperRomBank(final int writeValue) {
       /* if (mbcMode == 0) {
            // serves as upper bit5/6 of rombank
            final int bit5and6 = writeValue & 0b1100000;
            currentRomBank = bit5and6 | currentRomBank;
        } else {
            currentRamBank = writeValue;
            // 2bit value so should be between 0-3
        }

        The above follows the documentation from https://gbdev.gg8.se/wiki/articles/Memory_Bank_Controllers (which is depracted)
        This made more sense to me initially since it clearly split up which we should handle (the RAM or ROM)

        PanDocs actually seems to explain that mode 1 allows banking in BOTH regions (because the gb here uses an AND gate).
        We'll attempt this to see if our cpu_instrs.gb properly works.
        */

        if (mbc1Mode == 1) {
            final int bit5and6 = writeValue & 0b1100000;
            currentRomBank = bit5and6 | currentRomBank;
            currentRamBank = bit5and6 >> 5;
        }
    }

    // --- end of MBC_1 methods ---

    /**
     * Depending on the rom bank selected, this will apply the appropriate scale to access the correct region
     * in memory (depending on which bank) when reading from rom.
     */
    private int romBankNumScaler() {
        if (currentRomBank == 0) return 0;
        else return 0x4000 * (currentRomBank - 1); // bank1 starts at 0x0 in array, so if we remain on bank1, we need the 0 result.
    }

    /**
     * Same application as romBankNumScaler. Will select appropriate region in array depending on the bank num.
     */
    private int ramBankNumScaler() {
        return 0x2000 * currentRamBank;
    }

}

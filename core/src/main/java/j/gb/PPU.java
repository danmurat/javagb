package j.gb;

/**
 * Implementation of the GameBoy's Pixel Processing Unit.
 *
 * This will handle how the graphics are displayed basically.
 */
public class PPU {
    // these pointers are for accessing tile data in VRAM
    private static final int BASE_POINTER_8000 = 0x8000;
    private static final int BASE_POINTER_9000 = 0x9000;
    // this is the PPU's time unit. 4 dots = 1 Mcycle.
    private static final int DOTS_PER_FRAME = 70224;
    // hello

    private Memory memory;

    private boolean mode0; // horizontal blank
    private boolean mode1; // vertical blank
    private boolean mode2; // OAM scan
    private boolean mode3; // Drawing pixels

    private int[][][][] tileMap1;
    private int[][][][] tileMap2;

    public PPU(Memory memory) {
        memory = memory;
        // set all false for now till we figure out what to do
        mode0 = false;
        mode1 = false;
        mode2 = false;
        mode3 = false;
    }

    // methods..

    /**
     * Fills out 2 32x32 tile maps for background and window tiles.
     *
     * This accesses the tile map addresses in VRAM (in this case x9800-9BFF). Each address there will hold the index
     * to the actual tile inside the VRAM tile data (x8000-97FF). This will call the computeTile method to retreive
     * the data from that index and constructs a 2bit per pixel tile.
     *
     * startAddress will be 0x9800 for tileMap 1 and 0x9C00 for the second
     */
    public int[][][][] computeTileMap(final int startAddress) {
        final int[][][][] tileMap = new int[32][32][8][8];

        int basePointer;
        // TODO: i'm not sure if we check this once, or that we check through each iteration?
        // assuming once for now
        if (getLCDCbit4() == 1) {
            basePointer = BASE_POINTER_8000;

            for (int i = 0; i < 32; i++) {
                for (int j = 0; j < 32; j++) {
                    // this only gives values 0-255 (0-FF). It gives us which tile to use, but not the actual address of it
                    // since 1 tile occupies 16 bytes (so 16 memory addresses, 0-F).
                    // thus we'll multiply by 16 to get the correct addressing for the tile index

                    // j * (i+1) will give us the correct value as we go through each row
                    final int tileAddress = memory.readByte(startAddress + j * (i+1)) * 16;

                    // 32[ith] x 32[jth] filled with 8x8 tile
                    tileMap[i][j] = computeTile(basePointer + tileAddress);
                }
            }
        } else {
            basePointer = BASE_POINTER_9000;

            for (int i = 0; i < 32; i++) {
                for (int j = 0; j < 32; j++) {
                    final int tileAddress = memory.readByte(startAddress + j * (i+1));
                    // uses signed value from $9000
                    memory.getTwosCompliment(tileAddress);
                    // twos compliment should work on the byte. Then we scale up 16 on the access
                    tileMap[i][j] = computeTile(basePointer + (tileAddress * 16));
                }
            }
        }

        return tileMap;
    }

    /**
     * this will return some 8x8 tile with 2bit values in each index (nums 0-3)
     *
     * The tileAddress gives us the address of the start of a tile (which is the start of 16 addresses, x0-xF)
     * 0x0-0x7 I think holds the first part of the tile, then 0x8-0xF holds the second.
     *
     * We treat those as seperate 8x8's, then calculate the final 2bit colour by combining each index of them.
     * The second part is the most significant bit in the combined value.
     */
    public int[][] computeTile(final int tileAddress) {
        int[][] tile = new int[8][8];
        // we have 2 8x8 bits.
        // for each index in them, we or them for that position.
        // we save the result (0-3 colour index) for that pixel
        final int part1Start = tileAddress;
        final int part2Start = part1Start + 0x8;
        // but how do we access the tiles?
        final int[][] tile1 = new int[8][8];
        for (int i = part1Start; i < part2Start; i++) {
            final int byteValue = memory.readByte(i);
            // each row is given an array of 8 bits
            tile1[i] = byteToArray(byteValue);
        }

        // I'm assuming that the second part of a tile is simply stored from 0x8-0xF in VRAM
        final int[][] tile2 = new int[8][8];
        for (int i = part2Start; i < part2Start + 0x8; i++) {
            final int byteValue = memory.readByte(i);
            tile2[i] = byteToArray(byteValue);
        }

        // then this last step is calculating the 2bit colour value.
        for (int i = 0; i < tile.length; i++) {
            for (int j = 0; j < tile[i].length; j++) {
                // tile2 is the most significant bit. This will save some value 0-3 in each index
                tile[i][j] = (tile2[i][j] << 1) | tile1[i][j];
            }
        }

        return tile;
    }

    private int[] byteToArray(final int byteValue) {
        final int[] bitArray = new int[8];
        // start at most significant bit down to least
        for (int i = 7; i >= 0; i--) {
            bitArray[7 - i] = (byteValue >> i) & 0x1;
        }

        return bitArray;
    }

    // this indicates whether what mode we access in VRAM
    private int getLCDCbit0() {
        return memory.readByte(0xFF40) & 0x01;
    }

    // determines the addressing mode for getting background/window tiles
    private int getLCDCbit4() {
        final int bit4 = 0b10000;
        return (memory.readByte(0xFF40) & bit4) >> 3;
    }

    // indicates wether the window is displayed
    private int getLCDCbit5() {
        final int bit5 = 0b100000;
        return (memory.readByte(0xFF40) & bit5) >> 4;
    }



    // these get the starting points out of the 256x256 tilemap. X will be some byte telling us the start width
    private int getOriginX() {
        // TODO: consider using a GET for specific registers in mem, instead of searching through if,else in readByte
        return memory.readByte(0xFF43);
    }

    private int getOriginY() {
        return memory.readByte(0xFF42);
    }

    // like above, this specifies where the start of our window tiles are placed.
    private int getWindowX() {
        return memory.readByte(0xFF4B);
    }

    private int getWindowY() {
        return memory.readByte(0xFF4A);
    }
}

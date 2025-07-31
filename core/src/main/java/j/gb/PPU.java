package j.gb;

import java.util.ArrayList;
import java.util.ArrayDeque;

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

    // for getting object attribute info
    private static final int OAM_MEMORY_START = 0xFE00;
    // since OAM info for object is 4 bytes long, access for each obj must be scaled for correct location of object info
    private static final int OAM_ACCESS_SCALER = 4;


    private Memory memory;

    private boolean mode0; // horizontal blank
    private boolean mode1; // vertical blank
    private boolean mode2; // OAM scan
    private boolean mode3; // Drawing pixels

    private boolean ppuDisabled;

    private int[][][][] tileMap1;
    private int[][][][] tileMap2;

    private ArrayDeque<Integer> bgFIFO;
    private ArrayDeque<Integer> objFIFO;

    public PPU(Memory memory) {
        this.memory = memory;
        // set all false for now till we figure out what to do
        mode0 = false;
        mode1 = false;
        mode2 = false;
        mode3 = false;

        ppuDisabled = false;

        bgFIFO = new ArrayDeque<>();
        objFIFO = new ArrayDeque<>();
    }

    // methods..

    /*
    Main screen rendering (actually getting things on each pixel on the screen)

    Not sure if we'll deal with libgdx stuff in here? I'm thinking atm to just set all the correct
    values in a 2d array, and return it so that we can just render with libgdx in one go.

    The each scanline takes up 460 dots, with the different modes taking up a different amount.
    Mode 3 is when we actually draw the pixels.
    It takes up 172-289 dots. The variance is caused by things slowing down the time to draw (so it's a bit complicated)
    At minimum, with no delays, we draw the 160 pixels for a scan line in 172 dots.
     */

    public int[][] renderScreen() {
        final int[][] screen = new int[144][160];
        for (int scanline = 0; scanline < 144; scanline++) { // TODO change back to 154!
            // we have 460 dots per scanline (unit of time)
            for (int dots = 0; dots < 160; dots++) { // TODO: change back to 460!
                // we'll work on mode 3 for the time being.. Get all the pixels drawn properly.

                int xPos = 0; // x of the current scanline

                // only accepts when empty
                if (bgFIFO.isEmpty()) {
                    bgFIFO = pixelFetcher(xPos, scanline);
                }

                /*
                Just quickly trying to get this running and working to see if there's any feedback.
                This is probably very far from correct, but I want to see if I can quickly get
                something on the screen!

                So the scanline and dots are just between the lcd screen sizes (not how they should be!).
                 */
                screen[scanline][dots] = bgFIFO.pop();
            }
        }

        return screen;
    }

    /*
     manipulated and used only during mode 3!!

     Scanline x/y positions so we know our position relative to what should be shown from the tileMap?
     This should help us determine which tiles to select (then push the row of 8 bits into queue)
     */
    private ArrayDeque<Integer> pixelFetcher(final int scanlineXPos, final int scanlineYPos) {
        // row of 8 bg/w pixels

        // 5 steps
        // - get tile
        int tileMapLocation = 0x9800; // defualt unless below cases change
        // TODO: what does it mean for x to/not to be inside window??
        // i'll assume 'inside window' means whether x is off screen or not
        if (getLCDCbit3() == 1 && isOffScreenX(scanlineXPos)) {
            tileMapLocation = 0x9C00;
        } else if (getLCDCbit6() == 1 && !isOffScreenX(scanlineXPos)) {
            tileMapLocation = 0x9C00;
        }

        /* TODO: handle the difference between window and background tile
        boolean isWindowTile = false;
        if (getLCDCbit5() == 1 && fetcherX == getWindowX() && fetcherY == getWindowY()) {
            isWindowTile = true;
        }
         */


        // "The fetcher keeps track of which X and Y coordinate of the tile it’s on"
        // forgetting about dealing with window tiles for the moment, to get the correct tile position, we do:
        final int fetcherX = ((getSCX() / 8) + (scanlineXPos / 8)) & 0x1F; // 1F ensures result between 0-31
        final int fetcherY = (getSCY() + scanlineYPos) & 255; // y accesses the actual row
        /*
        So fetcher X only cares about where the tile starts on the X plane, so it wants 0-31
        fetcher Y cares about the actual row of the tile! This wants 0-255.

        This way we can access the correct byte of tile data.

        TODO: handle VRAM blockage (on interrupts)
         */

        // tile high? tile low? figure this all out..
        int[] dataRow = getTileRow(tileMapLocation, fetcherX, fetcherY);
        // - get tile data low
        // - get tile data high
        /*
        I'm a bit confused since panDocs seems to be telling us to access the low (addr) and then high (addr+1)
        Which I think means the two bytes to be combined to give values 0-3. We already do this in computeTileRow.

        But then it doesn't really tell us how these two differ in the push steps? Just says "Pushes a row of
        background/window pixels to the FIFO" with no explanation for the tileLow/high.

        For now I'm assuming I've handled it correctly.
         */

        // - sleep: do nothing..

        // - push
        ArrayDeque<Integer> rowOfPixels = new ArrayDeque<>();
        for (int i : dataRow) {
            rowOfPixels.add(i);
        }

        return rowOfPixels;
    }

    /*
    Object Attribute Memory. OAM. The extra info for object are stored at $FE00-FE9F.
    We can only display up to 40 objects at once, so this mem address should hold max 40.
    Each object attribute consists of 4 bytes.
    : fe00 -> fe03 = one obj. 0xFE00 + 40*4 = 0xFEA0 -1 = FE9F

    What I don't understand is, how do we determine what objects to show? Priority selection!

    Pan Docs doesn't seem to have any info on this. I'm guessing that we just automatically try to load
    ALL 40? If there's no info, then it doesn't matter. But otherwise, we just show it?

    Selection priority kind of tells us what to do.

    We can only have 10 objects per scanline. So on each scanline, we scan OAM by comparing LY (LCDC bit 2)
    to each object's Y position to select up to 10 for that line.
     */

    // per scanline (up to 10 objects max). We'll return an array holding the object's numbers that we've selected
    private ArrayList<Integer> scanlineObjectSelection() {
        ArrayList<Integer> selectedObjs = new ArrayList<>(); // TODO: make sure this isn't too much a performance hit

        // we need to go through each object's OAM and select based of their Y position (to LY)
        final int LY = getLY(); // current horizontal scanline

        // this will determine size, but do we really need to check that in here?
        final int checkObjSize = getLCDCbit2(); // TODO: what do we really need this for?

        // we loop through all oam's sequentially, check if their yPos = LY, then add to selected if so
        for (int i = 0; i < 40; i++) {
            final int[] oamInfo = getObjectOAM(i);
            final int yPos = oamInfo[0];
            final int xPos = oamInfo[1];
            if (!isOffScreenX(xPos) && yPos == LY) {
                selectedObjs.add(i);
            }
            if (selectedObjs.size() == 10) break;
        }

        return selectedObjs;
    }

    // width = 160 pixels with +/- 8 (the width of each tile). So 0 is off screen, and 168 is too.
    // used for object selection
    private boolean isOffScreenX(final int xPos) {
        return xPos == 0 || xPos >= 168;
    }

    /*
    We also have to handle drawing priority. That is, 2 pixels from 2 objects can overlap each other on screen.
    Non-gb colour, the object with the smaller xPos has the priority. If they're the same, then it's first in OAM.
    So we select the higher priority pixel to draw.

    NOTE: this might only be for SPECIFIC non-opaque PIXELS!! Not a whole object.
     */
    // we should only call this when we know 2 objects overlap!
    // TODO: also, how do we know 2 pixels are overlapping? I think we determine this outside this method.
    private int drawPriority(final int obj1, final int obj2) {
        final int[] oamInfo1 = getObjectOAM(obj1);
        final int[] oamInfo2 = getObjectOAM(obj2);

        final int xPos1 = oamInfo1[1];
        final int xPos2 = oamInfo2[1];

        if (xPos1 < xPos2) {
            return obj1;
        } else if (xPos1 > xPos2) {
            return obj2;
        } else {
            return Math.min(obj1, obj2); // the lower number obj appears before the other in OAM
        }
    }

    // store the inf
    private int[] getObjectOAM(final int objNum) {
        final int[]oamInfo = new int[4];
        // will get each byte of info
        final int yPos = memory.readByte(OAM_MEMORY_START + objNum * OAM_ACCESS_SCALER);
        final int xPos = memory.readByte((OAM_MEMORY_START + objNum * OAM_ACCESS_SCALER) + 1);
        final int tileIndex = memory.readByte((OAM_MEMORY_START + objNum * OAM_ACCESS_SCALER) + 2);
        final int attributes = memory.readByte((OAM_MEMORY_START + objNum * OAM_ACCESS_SCALER) + 3);

        oamInfo[0] = yPos;
        oamInfo[1] = xPos;
        oamInfo[2] = tileIndex;
        oamInfo[3] = attributes;

        return oamInfo;
    }


    /**
     * This get's us the correct tile row data from the tileMap. So this handles the 'routing' of where to find the specific
     * data.
     * <br>
     * At first we were computing the whole tile map. This wasn't needed. Instead we need to figure out how to retrieve
     * the correct tile row (based off what the pixel fetcher is fetching!).
     *
     * We'll be passing a fetcherX, fetcherY to this method. The fetcherX holds the tile number we're on for the current
     * scanline.
     * fetcherY holds the specified row. So instead of 32 tiles (which are whole) this specifies the actual row for a tile
     * which will be 0-255 (for tile 1 we have rows 0-7, so fetcherY = 5 means row 5 in tile 1).
     *
     * startAddress will be 0x9800 for tileMap 1 and 0x9C00 for the second
     */
    public int[] getTileRow(final int startAddress, final int tileXPos, final int tileYPos) {
        int[] tileRow;

        int basePointer;
        final int correctedTileYPos = tileYPos / 8; // gives us the correct Y tile (0-31)
        final int tileMapNumber = tileXPos * correctedTileYPos; // tells us which tile in the tile map we're on
        final int correctTileRow = tileYPos % 8; // gives us the row in a specific tile

        // TODO: i'm not sure if we check this once, or that we check through each iteration?
        // assuming once for now.
        if (getLCDCbit4() == 1) {
            basePointer = BASE_POINTER_8000;
            /*
            each address in the tileMap holds a byte index. This means that we have the values 0-255 which is the
            number of the tile in VRAM.
            the startAddr + tileNumber accesses the correct tile from the tileMap, then we get the vram tile num
            since 1 tile = 16bytes, a single VRAM address holds a single byte. So VRAM address x0-xF holds 1 tile
            to access correct addr for a tile, we multiply by 16. (tile 2 would = 0x10-0x1F, tile 3 = 0x20-0x2F, etc)
             */
            final int vramTileNumber = memory.readByte(startAddress + tileMapNumber) * 16;
            // tileRow = 0-7, so we access the correct row of the tile too.
            final int tileRowAddress = basePointer + vramTileNumber + correctTileRow;
            tileRow = computeTileRow(tileRowAddress);
        } else {
            basePointer = BASE_POINTER_9000;

            final int vramTileNumber = memory.readByte(startAddress + tileMapNumber);
            // get signed value to then use from address $9000.
            final int signedVramTileNumber = memory.getTwosCompliment(vramTileNumber) * 16;
            final int tileAndRowAddress = basePointer + signedVramTileNumber + correctTileRow;
            tileRow = computeTileRow(tileAndRowAddress);
        }

        return tileRow;
    }

    /**
     * this will return some row on a tile with 2bit values in each index (nums 0-3)
     * The tileAddress gives us the address of the start of a tile (which is the start of 16 addresses, x0-xF)
     */
    public int[] computeTileRow(final int tileAndRowAddress) {
        int[] tileRow = new int[8];
        // we have 2 8x8 bits.
        // for each index in them, we or them for that position.
        // we save the result (0-3 colour index) for that pixel
        int[] rowData1 = byteToArray(memory.readByte(tileAndRowAddress));
        // address after stores the next part of the data for the final byte row (to be combined together to make values 0-3)
        int[] rowData2 = byteToArray(memory.readByte(tileAndRowAddress + 1));

        // then this last step is calculating the 2bit colour value.
        for (int i = 0; i < tileRow.length; i++) {
            // rowData2 is the most significant bit. This will save some value 0-3 in each index
            tileRow[i] = (rowData2[i] << 1) | rowData1[i];
        }

        return tileRow;
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

    // object enable
    private int getLCDCbit1() {
        final int bit1 = 0b10;
        return (memory.readByte(0xFF40) & bit1) >> 1;
    }

    // determines the object size. If on, obj will be 8x16 instead.
    private int getLCDCbit2() {
        final int bit2 = 0b100;
        return (memory.readByte(0xFF40) & bit2) >> 2;
    }

    // background tile map area: if 0, area = $9800, 1 = $9C00
    private int getLCDCbit3() {
        final int bit3 = 0b1000;
        return (memory.readByte(0xFF40) & bit3) >> 3;
    }

    // determines the addressing mode for getting background/window tiles
    private int getLCDCbit4() {
        final int bit4 = 0b10000;
        return (memory.readByte(0xFF40) & bit4) >> 4;
    }

    // indicates wether the window is displayed
    private int getLCDCbit5() {
        final int bit5 = 0b100000;
        return (memory.readByte(0xFF40) & bit5) >> 5;
    }

    // window tile map area: if 0, area = $9800, else $9C00 (like bit3 above)
    private int getLCDCbit6() {
        final int bit6 = 0b1000000;
        return (memory.readByte(0xFF40) & bit6) >> 6;
    }

    // LCD enable bit, to check if screen should be on or not
    private int getLCDCbit7() {
        final int bit7 = 0b10000000;
        return (memory.readByte(0xFF40) & bit7) >> 7;
    }




    // --- LCD Scrolling ---

    /*
    SCX/Y tell us the top left co-ordinate of the LCD screen within the 256x256 background map
    ppu then calculates bottom left (which will give us the whole screen)

    So they are the VISIBLE part of the tilemaps. The part we can see on screen.

    Scroll registers are re-read every tile-fetch!! Apart from lower 3bits of SCX (read at beginning of scanline)
     */
    private int getSCX() {
        // TODO: consider using a GET for specific registers in mem, instead of searching through if,else in readByte
        return memory.readByte(0xFF43);
    }

    private int getSCY() {
        return memory.readByte(0xFF42);
    }

    // we have top-left with SCX/Y. We calc bottom-left to give us the whole screen
    private int[] calculateBottomRightCoOrd() {
        final int[] coOrd = new int[2];
        // if x/y > 256, the modulo will wrap it around (like an overflow)
        final int bottom = (getSCY() + 143) % 256;
        final int right = (getSCX() + 159) % 256;
        coOrd[0] = right; // x
        coOrd[1] = bottom; // y

        return coOrd;
    }

    // like above, this specifies where the start of our window tiles are placed.
    private int getWindowX() {
        return memory.readByte(0xFF4B) + 7; // panDocs says this is needed?
    }

    private int getWindowY() {
        return memory.readByte(0xFF4A);
    }


    // --- LCD Status Registers (STAT) ---

    // gives us the current horizontal line (0-153). 144-153 indicates VBlank period
    private int getLY() {
        return memory.readByte(0xFF44);
    }

    // GameBoy constantly compares LY-LYC, for a STAT flag
    private int getLYC() {
        return memory.readByte(0xFF45);
    }

    /**
     * STAT = LCD status
     * This will indicate what mode the PPU should be in, represented by each bit in the byte.
     */
    private int getSTAT() {
        return memory.readByte(0xFF41);
    }

    // constant check if LY == LYC and sets the bit
    private void setStatBit2() {
        int stat = getSTAT();
        if (getLY() == getLYC()) {
            // OR the '1' in
            memory.writeByte(0xFF41, (short) (stat | 0b100));
        } else {
            // AND the '0' in
            memory.writeByte(0xFF41, (short) (stat & 0b11111011));
        }
    }

    // checks STAT bit 0 and 1 to give what mode out of 3 it's in
    private int getPPUMode() {
        if (ppuDisabled) {
            return 0;
        } else {
            return getSTAT() & 0b11;
        }
    }

    // these all checkers for the STAT interrupt
    private int checkMode0() {
        return (getSTAT() & 0b1000) >> 3;
    }

    private int checkMode1() {
        return (getSTAT() & 0b10000) >> 4;
    }

    private int checkMode2() {
        return (getSTAT() & 0b100000) >> 5;
    }

    private int checkLYC() {
        return (getSTAT() & 0b1000000) >> 6;
    }


    // --- Palettes
    /*
        I don't understand why we still need to grab colour from elsewhere? PanDocs says this is for Background and
        Window tiles, but don't the tiles already assign themselves the values 0-3 in each pixel representing the colour?

        We have BGP for background palletes
        and OBP0 OBP1 for objects.

        I just don't get it? They already have the colour assigned in the pixels? So what's the purpose of this?
     */

    private int getBGP() {
        return memory.readByte(0xFF47);
    }

    private int getOBP0() {
        return memory.readByte(0xFF48);
    }

    private int getOBP1() {
        return memory.readByte(0xFF49);
    }

}

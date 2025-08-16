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

    private static final int IF_ADDRESS = 0xFF0F;
    private static final int STAT_ADDRESS = 0xFF41;
    private static final int LY_ADDRESS = 0xFF44;
    private static final int LYC_ADDRESS = 0xFF45;


    private final int[][] screen = new int[144][160];

    private Memory memory;
    private CPU cpu;

    private int totalCycleCount = 0;

    private boolean mode0; // horizontal blank
    private boolean mode1; // vertical blank
    private boolean mode2; // OAM scan
    private boolean mode3; // Drawing pixels

    private boolean ppuDisabled;

    private ArrayDeque<Integer> bgFIFO;
    private ArrayDeque<Integer> objFIFO;

    public PPU(Memory memory, CPU cpu) {
        this.memory = memory;
        this.cpu = cpu;
        // set all false for now till we figure out what to do
        mode0 = false;
        mode1 = false;
        mode2 = false;
        mode3 = false;

        ppuDisabled = false;

        bgFIFO = new ArrayDeque<>();
        objFIFO = new ArrayDeque<>();
    }

    public void resetTotalCycleCount() {
        totalCycleCount = 0;
    }

    public int getTotalCycleCount() {
        return totalCycleCount;
    }

    public int[][] getScreen() {
        return screen;
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

    NOTE: we run a certain number of instructions here! This is an attempt to have the screen rendering and
    cpu synced.
     */
    public int[][] renderScreen() {
        resetTotalCycleCount();
        for (int scanline = 0; scanline < 154; scanline++) {
            memory.writeByte(0xFF44, (short) scanline); // update since cpu won't do it
            //System.out.println("LY pos : scanline = " + getLY()  + ", " + scanline);
            setStatBit2();
            checkSTATInterrupt();

            // we have 460 dots per scanline (dot = T-cycle)
            if (scanline < 144) {
                // run mode2 (80 dots cycle)
                setStatMode2(true); // TODO: there may be no need to set this manually..
                memory.setOamAccessible(false);
                cpu.runInstructions(80 / 4, true); // div by 4 to get M-cycles
                setStatMode2(false);

                // mode 3 (172-289 dots). We must iterate this time to deal with the varying dots
                memory.setVramAccessible(false);
                int dots = 0;
                int mode3RequiredDots = 172; // max width + 12 dots
                int mode3RunDotsCount = 0; // allow us to control how often we run instructions.
                int penaltyCount = 12; // first penalty occurs instantly in mode 3 (panDocs)
                int penaltyAccumulator = 12; // keep hold of total, so we can access the correct pixel x position
                // testing scroll penalty
              /*  int scrollPenalty = getSCX() % 8;
                penaltyCount = scrollPenalty;
                penaltyAccumulator = penaltyCount;
                mode3RequiredDots += penaltyAccumulator;*/

                while (dots < mode3RequiredDots) {
                    // makes sure we iterate through dots depending on how many cycles cpu went through
                    if (mode3RunDotsCount == 0) {
                        cpu.runInstructions(1, false);
                        mode3RunDotsCount = cpu.getTotalMCycles() * 4; // get the T-cycles just ran
                        cpu.resetTotalMCycles();
                    }
                    mode3RunDotsCount--;
                    /*
                    Below, we're directly translating the dot number to the pixel X position, which isn't correct.
                    I think, depending on the penalty that has occured, we'll have to subtract by the accumulated penalty.
                    For example, if we've accumulated the max penalties (our dots reaches 289) we will be drawing at
                    pixelX 160.

                    PROBLEM. If the requiredDots increases, then drawPixel will be called an
                    increasing amount of times!!
                    We need a way of running drawPixels() 160 times ONLY, but the runInstructions() to run for the required
                    amount of times.

                    something like, if (penaltyCount == 0) drawPixel(),
                                    else penaltyCount-- // until we pass the dot iterations
                     */
                    if (penaltyCount == 0) {
                        final int xPos = dots - penaltyAccumulator;
                        drawPixel(xPos, scanline, screen);
                    } else {
                        penaltyCount--;
                    }

                    dots++; //
                }

                // mode0 HBlank (lasts for the remaining number of dots left)
                setStatMode0(true);
                memory.setOamAccessible(true);
                memory.setVramAccessible(true);
                final int remainingCycles = 460 - 80 - dots;
                cpu.runInstructions(remainingCycles / 4, true);
                setStatMode0(false);
            } else { // finally VBlank
                 /*
                THOUGHT: I think here, we're supposed to be requesting a VBlank interrupt...
                 */
               /* if (scanline == 144) {
                    memory.writeByte(0xFF0F, (short) 0x1); // request vblank interrupt
                    memory.writeByte(0xFFFF, (short) 0x1); // and enable
                    // nothings changed..
                }*/
                // we run per scanline rather than calling the whole 4560 cycles, so that the LY still increments (might be needed)
                setStatMode1(true);
                cpu.runInstructions(456 / 4, true);
                setStatMode1(false); // leave for now, since it will just get reset to on for next iteration.
            }
        }

        return screen;
    }

    /**
     * Fills the screen array with a single pixel whilst in mode 3 of rendering.
     */
    private void drawPixel(final int x, final int y, final int[][] screen) {
        // only accepts when empty
        if (bgFIFO.isEmpty()) {
            bgFIFO = pixelFetcher(x, y);
        }

        screen[y][x] = bgFIFO.pop();
    }

    /*
     manipulated and used only during mode 3!!

     Scanline x/y positions so we know our position relative to what should be shown from the tileMap?
     This should help us determine which tiles to select (then push the row of 8 bits into queue)
     TODO: scanlineX is never off screen!! we're just passing vals within the screen!
     */
    private ArrayDeque<Integer> pixelFetcher(final int scanlineXPos, final int scanlineYPos) {
        // row of 8 bg/w pixels

        // 5 steps
        // - get tile
        int tileMapLocation = 0x9800; // defualt unless below cases change
        /*
        what does it mean for x to/not to be inside window??

        'inside window' means whether our scanline position is within the window tilemap, depending on its position.
        The way the window tilemap is positioned is always from the top-left of the map to the screen and
        (very importantly) can only move RIGHT and DOWN! (we can move left but just by 8 pixels)
        This means the you essentially NEVER SEE the right and bottom parts of the window tilemap on screen.

        Think of the lcd screen being within the bg tilemap. You can move the tilemap left/right up/down and it will
        wrap around if you move it across the edge of the map. This creates a scrolling effect when playing.

        The window does not do this. Your screen will always be fixed to the top-left of the window tilemap, and you
        can only move the tile map ever-so-slightly left, and otherwise right and down. The more right and down you push,
        you will not see anything on your lcd screen on the left and top because this map doesn't wrap around.

        I found it quite hard to visualise with the words from PanDocs. This video instantly gets the image in your
        head of how this works: https://www.youtube.com/watch?v=8TVgN16DrEU
         */
        boolean isWindowTile = false;
        if (memory.getLCDCbit3() == 1 && !isWithinWindow(scanlineXPos, scanlineYPos)) {
            tileMapLocation = 0x9C00;
        } else if (memory.getLCDCbit6() == 1 && isWithinWindow(scanlineXPos, scanlineYPos)) {
            tileMapLocation = 0x9C00;
            if (memory.getLCDCbit5() == 1) isWindowTile = true;
        } else if (memory.getLCDCbit6() == 0 && isWithinWindow(scanlineXPos, scanlineYPos) && memory.getLCDCbit5() == 1) {
            isWindowTile = true;
        }


        /*
        "The fetcher keeps track of which X and Y coordinate of the tile it’s on"

        So fetcher X only cares about where the tile starts on the X plane, so it wants 0-31
        fetcher Y cares about the actual row of the tile! This wants 0-255.
        This way we can access the correct byte of tile data.
         */
        int fetcherX, fetcherY;
        if (isWindowTile) {
            // TODO: window X may need to be subtracted by 7!
            final int WX = getWindowX();
            final int WY = getWindowY();
            final int WXTileNum = WX / 8;
            final int scanlineXTileNum = scanlineXPos / 8;
            // the scanX/Y - winX/Y gives us the correct position of how far along a window tilemap we are.
            fetcherX = WXTileNum + (scanlineXTileNum - WXTileNum) & 0x1F;
            fetcherY = WY + (scanlineYPos - WY) & 255;
        } else {
            // forgetting about dealing with window tiles for the moment, to get the correct tile position, we do:
            fetcherX = ((getSCX() / 8) + (scanlineXPos / 8)) & 0x1F; // 1F ensures result between 0-31
            fetcherY = (getSCY() + scanlineYPos) & 255; // y accesses the actual row
        }


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
        final int checkObjSize = memory.getLCDCbit2(); // TODO: what do we really need this for?

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

    private boolean isWithinWindow(final int xPos, final int yPos) {
        // PanDocs only says to check against xPos. But this doesn't make sense since the window yPos could be further
        // down. We include the yPos, but keep this comment in case my thinking is wrong.
        return xPos >= getWindowX() && yPos >= getWindowY();
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
        final int yPos = memory.ppuReadOam(OAM_MEMORY_START + objNum * OAM_ACCESS_SCALER);
        final int xPos = memory.ppuReadOam((OAM_MEMORY_START + objNum * OAM_ACCESS_SCALER) + 1);
        final int tileIndex = memory.ppuReadOam((OAM_MEMORY_START + objNum * OAM_ACCESS_SCALER) + 2);
        final int attributes = memory.ppuReadOam((OAM_MEMORY_START + objNum * OAM_ACCESS_SCALER) + 3);

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
        // tilyY * 32 will give us the correct number for the row we're on, added by the column.
        final int tileMapNumber = tileXPos + (correctedTileYPos * 32); // tells us which tile in the tile map we're on
        // correctTileRowAccess = 0-7, * 2, so we access the correct row of the tile that's stored in mem and spans all 16 addresses.
        final int correctTileRowAccess = (tileYPos % 8) * 2;

        // TODO: i'm not sure if we check this once, or that we check through each iteration?
        // assuming once for now.
        if (memory.getLCDCbit4() == 1) {
            basePointer = BASE_POINTER_8000;
            /*
            each address in the tileMap holds a byte index. This means that we have the values 0-255 which is the
            number of the tile in VRAM.
            the startAddr + tileNumber accesses the correct tile from the tileMap, then we get the vram tile num
            since 1 tile = 16bytes, a single VRAM address holds a single byte. So VRAM address x0-xF holds 1 tile.
            To access correct addr for a tile, we multiply by 16. (tile 2 would = 0x10-0x1F, tile 3 = 0x20-0x2F, etc)
             */
            final int vramTileNumber = memory.ppuReadVram(startAddress + tileMapNumber) * 16;
            final int tileRowAddress = basePointer + vramTileNumber + correctTileRowAccess;
            tileRow = computeTileRow(tileRowAddress);
        } else {
            basePointer = BASE_POINTER_9000;

            final int vramTileNumber = memory.ppuReadVram(startAddress + tileMapNumber);
            // get signed value to then use from address $9000.
            final int signedVramTileNumber = memory.getTwosCompliment(vramTileNumber) * 16;
            final int tileAndRowAddress = basePointer + signedVramTileNumber + correctTileRowAccess;
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
        int[] rowData1 = byteToArray(memory.ppuReadVram(tileAndRowAddress));
        // address after stores the next part of the data for the final byte row (to be combined together to make values 0-3)
        int[] rowData2 = byteToArray(memory.ppuReadVram(tileAndRowAddress + 1));

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
        return memory.readByte(LY_ADDRESS);
    }

    // GameBoy constantly compares LY-LYC, for a STAT flag
    private int getLYC() {
        return memory.readByte(LYC_ADDRESS);
    }

    /**
     * STAT = LCD status
     * This will indicate what mode the PPU should be in, represented by each bit in the byte.
     */
    private int getSTAT() {
        return memory.readByte(STAT_ADDRESS);
    }

    /// runs per scanline and requests if needed.
    private void checkSTATInterrupt() {
        // the compare is enabled AND we actaully have the same result, then interrupt
        if (STATBitSixOn() && STATBitTwoOn()) {
            memory.writeByte(IF_ADDRESS, (short) 0x2); // sets the request for STAT (bit 2)
        }
        // TODO: the rest of the modes!!
    }

    /// for a constant check if LY == LYC and sets the bit
    private void setStatBit2() {
        int stat = getSTAT();
        if (getLY() == getLYC()) {
            // OR the '1' in
            memory.writeByte(STAT_ADDRESS, (short) (stat | 0b100));
        } else {
            // AND the '0' in
            memory.writeByte(STAT_ADDRESS, (short) (stat & 0b11111011));
        }
    }

    // TODO: check if we do the sets manually or not..

    /// Sets bit 3
    private void setStatMode0(final boolean on) {
        final int stat = getSTAT();
        if (on) memory.writeByte(STAT_ADDRESS, (short) (stat | 0b1000));
        else memory.writeByte(STAT_ADDRESS, (short) (stat & 0b11110111));
    }

    /// Sets bit 4
    private void setStatMode1(final boolean on) {
        final int stat = getSTAT();
        if (on) memory.writeByte(STAT_ADDRESS, (short) (stat | 0b10000));
        else memory.writeByte(STAT_ADDRESS, (short) (stat & 0b11101111));
    }

    /// Sets bit 5
    private void setStatMode2(final boolean on) {
        final int stat = getSTAT();
        if (on) memory.writeByte(STAT_ADDRESS, (short) (stat | 0b100000));
        else memory.writeByte(STAT_ADDRESS, (short) (stat & 0b11011111));
    }

    /// Sets bit 6 (ROM & CPU set this itself..)
    private void setStatLYCSelect(final boolean on) {
        final int stat = getSTAT();
        if (on) memory.writeByte(STAT_ADDRESS, (short) (stat | 0b1000000));
        else memory.writeByte(STAT_ADDRESS, (short) (stat & 0b10111111));
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
    /// is LY==LYC enabled?
    private boolean STATBitTwoOn() {
        return ((getSTAT() & 0b100) >> 2) == 1;
    }

    ///  is Mode 0 select enabled?
    private boolean STATBitThreeOn() {
        return ((getSTAT() & 0b1000) >> 3) == 1;
    }

    ///  is Mode 1 select enabled?
    private boolean STATBitFourOn() {
        return ((getSTAT() & 0b10000) >> 4) == 1;
    }

    ///  is Mode 2 select enabled?
    private boolean STATBitFiveOn() {
        return ((getSTAT() & 0b100000) >> 5) == 1;
    }

    /// is LYC select enabled?
    private boolean STATBitSixOn() {
        return ((getSTAT() & 0b1000000) >> 6) == 1;
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


    // --- Debugging methods

    /**
     * Idea is we are able to visualise the tile map on our screen. We can see what's available and
     * compare it to what's being displayed.
     *
     * Will hold 2d array, just like the lcd screen. This will hold 256 x 256 pixels (32x32 tile map).
     */
    public int[][] computeTileMap(final boolean map2) {
        final int[][] tileMap = new int[256][256];
        int location = 0x9800;
        if (map2) location = 0x9C00;

        // loop through mem, but go through a single row at a time.

        for (int row = 0; row < 256; row++) {
            for (int col = 0; col < 32; col++) {
                int[] tileRow = getTileRow(location, col, row);
                // then fill tilemap array with data
                for (int i = 0; i < tileRow.length; i++) {
                    // col*8 + i properly places a byte of data for all 32 tiles wide
                    tileMap[row][(col * 8) + i] = tileRow[i];
                }

                // TODO: remove
                // here to distinguish tiles with black dots with black dot/**/s
                if (row % 8 == 0) tileMap[row][col*8] = 3;
            }
        }

        return tileMap;
    }

    /**
     * We can't seem to find the bottom half of our text in our tilemap. We'll compute all the tiles
     * and their values we find in vram to look for these missing tiles.
     */
    public int[][][] computeAllVram() {
        final int[][][] vramTileData = new int[384][8][8];
        // looping through total num of tiles (which are all in $8000-97FF) (each tile = 16bytes long)
        for (int i = 0; i < 384; i++) {
            final int[][] singleTileData = new int[8][8];
            for (int j = 0; j < 16; j += 2) {
                /* j += 2 since computTileRow() will get the current address + the next address. So must iterate 2.
                   0x8000 + (i * 16) + j will route us to the correct tileRow
                   (i*16) will return the start of the specific tile address (since each tile consists of 16 addresses)
                   + j for the correct row. */
                singleTileData[j / 2] = computeTileRow(0x8000 + (i * 16) + j);
            }
            vramTileData[i] = singleTileData;
        }

        return vramTileData;
    }
}

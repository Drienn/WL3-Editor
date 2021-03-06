package record;

import java.util.logging.Logger;

import javax.swing.JOptionPane;

import base.Level;
import base.Region;

import graphics.Drawing;

import java.util.Arrays;
import java.awt.image.*;
import java.awt.Color;
import java.io.*;
import java.util.*;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

public class RomReader {
	final static Logger logger = Logger.getLogger(RomReader.class.getName());

	public static RomReader rom;
	
	// When data can be moved anywhere, it's moved to one of these banks.
	// They are completely blank, at first.
	public static final int[] preferredFreeBanks = {0x16,0x17,0x1B,0x1C,0x1D,0x1E,0x1F,0x4A,0x4B,0x4C,
		0x4D,0x59,0x5A,0x5B,0x5C,0x5D,0x5E,0x5F,0x66,0x67,0x73,0x74,0x75};
	byte[] data;
	boolean[] free;
	File file;
	int banks;
	
	public boolean saveFail = false;
	
	public RomReader(File f)
	{
		file = f;
		try {
			FileInputStream in = new FileInputStream(file);
			data = new byte[(int)file.length()];
			in.read(data);
			banks = data.length/0x4000;
			
			free = new boolean[data.length];
			for (int i=0; i<data.length; i++)
			{
				if ((data[i]&0xff) == 0xff)
					free[i] = true;
				else
					free[i] = false;
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		/*
		for (int b=0; b<banks; b++) {
			if (getFreeSpaceLength(b*0x4000) == 0x4000)
				System.out.println(toHexString(b));
		}
		*/
	}

	public int getRomSize() {
		return data.length;
	}

	// This doesn't catch all cases of modification,
	// such as if the rom is written to directly (this shouldn't be done 
	// typically),
	// or if pointers are changed with the data remaining untouched.
	public boolean isModified() {
		for (MoveableDataRecord r : moveableDataRecords) {
			if (r.modified) {
				return true;
			}
		}
		for (RegionRecord r : RegionRecord.regionRecords) {
			if (r.modified)
				return true;
		}
		return false;
	}
	
	// I am not very cautious of records not synced with the rom.
	// If records are overwritten in other areas, they will still be in this arraylist.
	ArrayList<MoveableDataRecord> moveableDataRecords = new ArrayList<MoveableDataRecord>();
	// Parallel arrayList, counting the number of times the record has been accessed.
	ArrayList<Integer> moveableDataRecordAccesses = new ArrayList<Integer>();

	HashMap<Integer, MoveableDataRecord> recordLookupMap = new HashMap<Integer, MoveableDataRecord>();
	
	ArrayList<JoinedRecord> joinedRecords = new ArrayList<JoinedRecord>();

	public MoveableDataRecord moveableDataRecordExists(int addr) {
		for (int i=0; i<moveableDataRecords.size(); i++) {
			MoveableDataRecord r = moveableDataRecords.get(i);
			if (r.getAddr() == addr)
				return r;
		}
		return null;
	}
	// If no pointer is specified, the record will be marked as unmoveable. Otherwise it's moveable.
	public MoveableDataRecord getMoveableDataRecord(int addr, RomPointer ptr, boolean compressed, int size)
	{
		if (addr < 0) {
			System.out.println("getMoveableDataRecord negative addr");
		}

		MoveableDataRecord r = recordLookupMap.get(addr);
		if (r != null) {
			int i = moveableDataRecords.indexOf(r);
			if (ptr != null)
				r.addPtr(ptr);
			moveableDataRecordAccesses.set(i, moveableDataRecordAccesses.get(i)+1);
			return r;
		}
		else {
			ArrayList<RomPointer> pointers = new ArrayList<RomPointer>();
			if (ptr != null)
				pointers.add(ptr);
			r = new MoveableDataRecord(addr, pointers, compressed, size);
			moveableDataRecords.add(r);
			moveableDataRecordAccesses.add(1);
			recordLookupMap.put(addr, r);

			r.setMoveable(ptr != null);

			return r;
		}
	}
	public MoveableDataRecord getMoveableDataRecord(int addr, RomPointer ptr, boolean compressed, int size, int bank)
	{
		MoveableDataRecord r = getMoveableDataRecord(addr, ptr, compressed, size);
		r.setRequiredBank(bank);
		return r;
	}
	// Make a new record from this data
	public MoveableDataRecord getMoveableDataRecord(byte[] data, RomPointer pointer, int bank, boolean compressed)
	{
		ArrayList<RomPointer> pointers = new ArrayList<RomPointer>();
		if (pointer != null)
			pointers.add(pointer);
		MoveableDataRecord r = new MoveableDataRecord(data, pointers, bank, compressed);
		moveableDataRecords.add(r);
		moveableDataRecordAccesses.add(1);
		r.setMoveable(pointer != null);
		return r;
	}

	void updateRecordLookupMap() {
		recordLookupMap = new HashMap<Integer, MoveableDataRecord>();
		for (MoveableDataRecord r : moveableDataRecords) {
			if (r.addr >= 0) {
				recordLookupMap.put(r.addr, r);
			}
		}
	}

	// This function will only take effect when called as many times as the record in question has been
	// accessed from getMoveableDataRecord().
	// So basically, everything which has accessed the record has to "agree" to delete it.
	// This function is intended to be used for corrupt records - records which didn't read the kind of data
	// that it expected to read...
	public void deleteMoveableDataRecord(MoveableDataRecord record) {
		int i = moveableDataRecords.indexOf(record);
		int accesses = moveableDataRecordAccesses.get(i) - 1;
		moveableDataRecordAccesses.set(i, accesses);
		if (accesses == 0) {
			logger.info("Record deleted: \n" +
					"\"" + moveableDataRecords.get(i).getDescription() + "\"\n" +
					" (0x" + RomReader.toHexString(moveableDataRecords.get(i).getAddr()) + ")");
			moveableDataRecords.remove(i);
			moveableDataRecordAccesses.remove(i);
			if (record.addr >= 0)
				recordLookupMap.remove(record.addr);
			// Note: the record's memory won't be unlocked, I guess that's okay?
			// Since this is probably a corrupt record I don't want to risk unlocking the memory.
		}
	}
	public JoinedRecord getJoinedRecord(MoveableDataRecord record1, MoveableDataRecord record2)
	{
		JoinedRecord j = new JoinedRecord(record1, record2);
		joinedRecords.add(j);
		return j;
	}

	public void clearRecords() {
		RegionRecord.regionRecords = new ArrayList<RegionRecord>();
		joinedRecords = new ArrayList<JoinedRecord>();
		moveableDataRecords = new ArrayList<MoveableDataRecord>();
		moveableDataRecordAccesses = new ArrayList<Integer>();
		recordLookupMap = new HashMap<Integer, MoveableDataRecord>();
	}
	
	public int read(int addr)
	{
		return data[addr]&0xff;
	}
	
	public int read16(int addr)
	{
		return read(addr) | (read(addr+1)<<8);
	}
	
	public int read16(int addr, int bank)
	{
		return BANK(read(addr) | (read(addr+1)<<8), bank);
	}
	public int read16Indirect(int addr)
	{
		int addr2 = read16(addr);
		return read16(addr2);
	}
	
	public int read16Indirect(int addr, int bank)
	{
		int addr2 = read16(addr);
		return BANK(read16(addr2), bank);
	}
	
	public int read16FromTable(int tbl, int index, int bank)
	{
		return BANK(read16(tbl + index*2), bank);
	}
	
	public byte[] readBytes(int addr, int numBytes)
	{
		byte[] output = new byte[numBytes];
		for (int i=0; i<numBytes; i++)
			output[i] = data[addr+i];
		return output;
	}
	
	public ArrayList<Byte> readRLE(int addr)
	{
		int startAddr = addr;
		ArrayList<Byte> output = new ArrayList<Byte>();
		
		int n=read(addr++);
		while (n != 0) {
			
			if ((n&0x80) == 0x80)
			{
				n &= 0x7f;
				for (int i=0; i<n; i++)
				{
					output.add((byte)read(addr++));
				}
			}
			else
			{
				byte val = (byte)read(addr++);
				for (int i=0; i<n; i++)
				{
					output.add(val);
				}
			}
			n = read(addr++);
		}
		
		return output;
	}
	public ArrayList<Byte> readRawRLE(int addr)
	{
		ArrayList<Byte> output = new ArrayList<Byte>();
		int startAddr=addr;
		
		byte n = data[addr++];
		while (n != 0)
		{
			if ((n&0x80) == 0)
				addr++;
			else
				addr += (n&0x7f);
			n = data[addr++];
		}
		int size = addr-startAddr;
		for (int i=startAddr; i<addr; i++)
			output.add(data[i]);
		return output;
	}
	
	public ArrayList<Byte> convertToRLE(ArrayList<Byte> bytes)
	{
		ArrayList<Byte> output = new ArrayList<Byte>();
		
		for (int i=0; i<bytes.size(); i++)
		{
			int j=i;
			byte val=bytes.get(j);
			int length=0;
			
			while (j < bytes.size() && length < 0x7f && bytes.get(j) == val)
			{
				j++;
				length++;
			}
			
			if (length == 1)
			{
				ArrayList<Byte> toAdd = new ArrayList<Byte>();
				toAdd.add(val);
				
				while (j < bytes.size() && toAdd.size() < 0x7f)
				{
					int nextLength=0;
					byte nextVal = bytes.get(j);
					while (j < bytes.size() && bytes.get(j) == nextVal)
					{
						j++;
						nextLength++;
					}
					if (nextLength <= 2)
					{
						int t=nextLength;
						while (t-- != 0 && toAdd.size() < 0x7f)
							toAdd.add(nextVal);
						if (t == 0)
							j--;
					}
					else
					{
						j -= nextLength;
						break;
					}
				}
				output.add((byte)(0x80|toAdd.size()));
				output.addAll(toAdd);
			}
			else
			{
				output.add((byte)length);
				output.add(val);
			}
			i = j-1;
		}
		
		output.add((byte)0);
		return output;
	}
	
	public int getFreeSpaceLength(int addr)
	{
		int size=0;
		while (free[addr] && (addr%0x4000 != 0 || size == 0))
		{
			addr++;
			size++;
		}
		return size;
	}
	
	public void write(int addr, byte val)
	{
		data[addr] = val;
	}
	
	public void writePtr(int addr, int val)
	{
		val &= 0x3fff;
		val += 0x4000;
		data[addr] = (byte)(val&0xff);
		data[addr+1] = (byte)(val>>8);
	}
	
	public void write(int addr, ArrayList<Byte> data)
	{
		for (int i=0; i<data.size(); i++)
		{
			write(i+addr, data.get(i));
		}
	}
	
	public void clear(int addr, int bytes)
	{
		for (int i=0; i<bytes; i++)
		{
			write(addr+i, (byte)0xff);
			free[addr+i] = true;
		}
	}
	public void lock(int addr, int bytes)
	{
		for (int i=0; i<bytes; i++)
		{
			free[addr+i] = false;
		}
	}

	boolean[] packedBanks;

	public void packBank(int bank) {
		if (packedBank(bank))
			return;
		logger.fine("Packing bank " + RomReader.toHexString(bank, 2));

		packedBanks[bank] = true;

		ArrayList<MoveableDataRecord> records = new ArrayList<MoveableDataRecord>();

		for (MoveableDataRecord r : moveableDataRecords) {
			if (r.isMoveable && r.requiredBank == bank) {
				records.add(r);
				r.detachFromOriginalSpace();
			}
		}

		for (MoveableDataRecord r : records) {
			// Some of these records may have been iterated through already in the save() function.
			// So, they must all be saved in this function.
			r.save();
			if (saveFail)
				return;
		}
	}

	public boolean packedBank(int bank) {
		return packedBanks[bank];
	}
	
	public void save()
	{
		for (int l=0; l<Level.NUM_LEVELS; l++) {
			Level level = Level.getLevel(l);
			Region r = level.getRegionDataRecord().checkUnsavableRegions();
			if (r != null) {
				JOptionPane.showMessageDialog(null,
						"Warning: region in level " + RomReader.toHexString(level.getId(),2) +
						" can't be saved.\n" +
						"Region's top-left-sector: " + RomReader.toHexString(r.firstVSector*0xa+r.firstHSector) + "\n\n" +
						"To fix this, make a sector destination point to a sector within this region.\n\n" +
						"The rom will still be saved.",
						"Warning",
						JOptionPane.WARNING_MESSAGE);
			}
		}

		packedBanks = new boolean[256];
		saveFail = false;

		// Iterate through all records and delete those which we can, to free up space
		for (int i=0; i<moveableDataRecords.size(); i++) {
			MoveableDataRecord r = moveableDataRecords.get(i);
			if (r.isNull()) {
				// It will free up the memory it took in the save() function
				r.save();
				moveableDataRecords.remove(i);
				moveableDataRecordAccesses.remove(i);
				if (r.addr >= 0)
					recordLookupMap.remove(r.addr);
				i--;
			}
		}

		// Check RegionRecords
		// RegionRecord class takes care of removing invalid records
		for (int i=0; i<RegionRecord.regionRecords.size(); i++) {
			RegionRecord r = RegionRecord.regionRecords.get(i);
			r.save();
		}
		for (int i=0; i<joinedRecords.size(); i++)
		{
			if (joinedRecords.get(i).r1.isNull() && joinedRecords.get(i).r2.isNull()) {
				joinedRecords.remove(i);
				i--;
			}
			else
				joinedRecords.get(i).save();
		}
		for (int i=0; i<moveableDataRecords.size() && !saveFail; i++) {
			MoveableDataRecord r = moveableDataRecords.get(i);
			r.save();
		}

		updateRecordLookupMap();
		
		if (saveFail) {
			return;
		}

		fixRomChecksum();
		try {
			FileOutputStream out = new FileOutputStream(file);
			out.write(data);
			out.close();
		}
		catch(FileNotFoundException e) {}
		catch(IOException e){}
	}

	public boolean savedSuccessfully() {
		return saveFail == false;
	}
	
	public int getRomChecksum() {
		int sum=0;
		for (int i=0; i<data.length; i++) {
			if (i != 0x14e && i != 0x14f)
				sum += data[i]&0xff;
		}
		return sum&0xffff;
	}
	void fixRomChecksum() {
		int sum = getRomChecksum();
		write(0x14e, (byte)(sum>>8));
		write(0x14f, (byte)(sum&0xff));
	}
	
	public int findFreeSpace(int size, boolean claim)
	{
		for (int b=0; b<preferredFreeBanks.length; b++) {
			int addr = findFreeSpace(size, preferredFreeBanks[b], claim);
			if (addr != -1)
				return addr;
		}
		for (int b=0; b<banks; b++)
		{
			int addr = findFreeSpace(size, b, claim);
			if (addr != -1)
				return addr;
		}
		return -1;
	}
	public int findFreeSpace(int size, int bank, boolean claim)
	{
		int b = bank;
		for (int addr=b*0x4000; addr<(b+1)*0x4000; addr++)
		{
			int length=0;
			int start = addr;
			while (addr < (b+1)*0x4000 && free[addr])
			{
				addr++;
				length++;
			}

			if (length >= size)
			{
				if (claim)
				{
					for (int i=start; i<start+size; i++)
						free[i] = false;
				}
				return start;
			}
		}
		return -1;
	}

	public static int BANK(int addr, int bank)
	{
		return (bank*0x4000) + (addr%0x4000);
	}
	
	public static int toGbPtr(int ptr)
	{
		// This first part probably isn't necessary - MBC5 can map bank 0 to the 0x4000-0x7fff slot.
		// But there's no harm.
		if (ptr < 0x4000)
			return ptr;
		return (ptr%0x4000)+0x4000;
	}

	// Read one tile
	public static BufferedImage binToTile(byte[] data, int flags, int[][] palette) {
		if (palette == null)
			palette = Drawing.defaultPalette;

		BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_USHORT_555_RGB);

		int p = flags&7;
		boolean flipX = (flags&0x20) != 0;
		boolean flipY = (flags&0x40) != 0;
		for (int y=0; y<8; y++) {
			for (int x=0; x<8; x++) {
				int c = ((data[y*2]&0xff)>>(7-x))&1;
				c |= (((data[y*2+1]&0xff)>>(7-x))&1)<<1;

				int destY;
				if (flipY)
					destY = 7-y;
				else
					destY = y;
				if (flipX)
					image.setRGB(7-x, destY, palette[p][c]);
				else
					image.setRGB(x, destY, palette[p][c]);
			}
		}
		return image;
	}
	// Read multiple tiles
	public static BufferedImage[] binToTiles(byte[] data, int flags, int[][] palette) {
		BufferedImage[] images = new BufferedImage[data.length/16];
		for (int i=0; i<data.length/16; i++) {
			BufferedImage image = binToTile(Arrays.copyOfRange(data, i*16, i*16+16), flags, palette);

			images[i] = image;
		}
		return images;
	}

	public static int[] binToPalette(byte[] data) {
		int[] paletteColors = new int[4];
		for (int c=0; c<4; c++)
		{
			int index = c*2;
			int b1 = data[index]&0xff;
			int b2 = data[index+1]&0xff;
			int by = b1 | b2<<8;

			int r = by&0x1f;
			int g = (by>>5)&0x1f;
			int b = (by>>10)&0x1f;

			paletteColors[c] = Drawing.rgbToInt(r*8, g*8, b*8);
		}
		return paletteColors;
	}
	public static int[][] binToPalettes(byte[] data) {
		int[][] paletteColors = new int[data.length/8][4];
		for (int i=0; i<data.length/8; i++)
		{
			for (int c=0; c<4; c++)
			{
				int index = i*8 + c*2;
				int b1 = data[index]&0xff;
				int b2 = data[index+1]&0xff;
				int by = b1 | b2<<8;

				int r = by&0x1f;
				int g = (by>>5)&0x1f;
				int b = (by>>10)&0x1f;

				paletteColors[i][c] = Drawing.rgbToInt(r*8, g*8, b*8);
			}
		}

		return paletteColors;
	}
	public static byte[] palettesToBin(int[][] palettes) {
		return palettesToBin(palettes, palettes.length);
	}
	public static byte[] palettesToBin(int[][] palettes, int numPalettes) {
		byte[] data = new byte[numPalettes*8];
		for (int i=0; i<numPalettes; i++) {
			for (int c=0; c<4; c++) {
				Color color = new Color(palettes[i][c]);
				int r = color.getRed()/8;
				int g = color.getGreen()/8;
				int b = color.getBlue()/8;


				int by = r | (g<<5) | (b<<10);
				//	paletteDataRecord.write(p*8+c*2+1, (byte)(data&0xff));
				//	paletteDataRecord.write(p*8+c*2, (byte)(data>>8));
				//paletteDataRecord.write16(p*8+c*2, data);
				data[i*8+c*2] = (byte)(by&0xff);
				data[i*8+c*2+1] = (byte)(by>>8);
			}
		}

		return data;
	}
	public static byte[] palettesToRGB24(int[][] palettes, int numPalettes) {
		byte[] output = new byte[numPalettes*4*3];
		int pos = 0;
		for (int i=0; i<numPalettes; i++) {
			for (int j=0; j<4; j++) {
				Color c = new Color(palettes[i][j]);
				output[pos++] = (byte)c.getRed();
				output[pos++] = (byte)c.getGreen();
				output[pos++] = (byte)c.getBlue();
			}
		}
		return output;
	}

	public static int[][] RGB24ToPalette(byte[] data) {
		int numPalettes = data.length/(4*3);
		int[][] palettes = new int[numPalettes][4];
		for (int p=0; p<numPalettes; p++) {
			for (int i=0; i<4; i++) {
				int pos = p*4*3+i*3;
				palettes[p][i] = new Color(data[pos]&0xff, data[pos+1]&0xff, data[pos+2]&0xff).getRGB();
			}
		}
		return palettes;
	}

	public static String getWorkingDirectory() {
		try {
			BufferedReader in = new BufferedReader(new FileReader(new File("ref/location.txt")));
			String dir = in.readLine();
			return dir;
		}
		catch (FileNotFoundException ex) {}
		catch (IOException ex) {}
		return null;
	}
	public static void setWorkingDirectory(String dir) {
		try {
			PrintWriter out = new PrintWriter(new File("ref/location.txt"));
			out.println(dir);
			out.close();
		} catch (FileNotFoundException ex) {}
	}

	// The rest of the functions are basically I/O helper functions.
	public static void exportData(byte[] data, String title, FileNameExtensionFilter filter) {
		JFileChooser fc = new JFileChooser();
		String dir = getWorkingDirectory();
		if (dir != null)
			fc.setCurrentDirectory(new File(dir));

		fc.setDialogTitle(title);

		fc.setFileFilter(filter);

		if (fc.showSaveDialog(null) == JFileChooser.APPROVE_OPTION)
		{
			setWorkingDirectory(fc.getCurrentDirectory().toString());
			File f = fc.getSelectedFile();
			try {
				FileOutputStream out = new FileOutputStream(f);
				out.write(data);
				out.close();
			} catch(IOException ex){}
		}

	}
	public static byte[] importData(String title, FileNameExtensionFilter filter) {
		JFileChooser fc = new JFileChooser();
		String dir = getWorkingDirectory();
		if (dir != null)
			fc.setCurrentDirectory(new File(dir));

		fc.setDialogTitle(title);
		fc.setFileFilter(filter);

		if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
		{
			setWorkingDirectory(fc.getCurrentDirectory().toString());
			File f = fc.getSelectedFile();
			try {
				FileInputStream in = new FileInputStream(f);
				byte[] data = new byte[(int)f.length()];
				in.read(data);
				in.close();
				return data;
			} catch(IOException ex){}
		}

		return null;
	}

	public static int parseInt(String s) throws NumberFormatException {
		if (s.indexOf(':') == -1)
			return Integer.parseInt(s, 16);
		String[] split = s.split(":");

		return RomReader.BANK(Integer.parseInt(split[1], 16), Integer.parseInt(split[0], 16));
	}

	public static String toHexString(int i) {
		return Integer.toHexString(i).toUpperCase();
	}

	public static String toHexString(int i, int minDigits) {
		String s = Integer.toHexString(i).toUpperCase();
		while (s.length() < minDigits)
			s = "0" + s;
		return s;
	}

	public static byte[] listToArray(ArrayList<Byte> list) {
		byte[] ret = new byte[list.size()];
		for (int i=0; i<list.size(); i++) {
			ret[i] = list.get(i);
		}
		return ret;
	}

	public static ArrayList<Byte> arrayToList(byte[] array) {
		ArrayList<Byte> ret = new ArrayList<Byte>();
		for (int i=0; i<array.length; i++) {
			ret.add(array[i]);
		}

		return ret;
	}
	
}

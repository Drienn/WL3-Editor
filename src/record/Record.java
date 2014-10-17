package record;

import java.io.IOException;
import java.util.*;

public abstract class Record {
	int addr;
	public boolean modified = false;
	boolean ptrsOutOfDate = false;

	ArrayList<RomPointer> ptrs = new ArrayList<RomPointer>();

	String description = "";

	public abstract void save();

	public int getAddr() {
		return addr;
	}
	public void addPtr(RomPointer ptr) {
		if (ptr == null)
			return;
		for (int i=0; i<ptrs.size(); i++)
		{
			if (ptrs.get(i).equals(ptr))
				return;
		}
		// If the pointer isn't pointing to the correct address, it must be saved later on.
		// I don't set "modified" if getPointedAddr() returns -1, because only a metadata pointer
		// would return that, and it's unnecessary to save to metadata unless the data changes location.
		if (ptr.getPointedAddr() != -1 &&
				!(ptr.getPointedAddr() == addr || (ptr.hasBankAddr() == false && ptr.getPointedAddr()%0x4000 == addr%0x4000)))
			ptrsOutOfDate = true;
		ptrs.add(ptr);
	}
	public void removePtr(RomPointer ptr) {
		for (int i=0; i<ptrs.size(); i++)
		{
			if (ptrs.get(i).equals(ptr))
			{
				ptrs.remove(i--);
//				System.out.println("Pointer removed.");
				// Might as well return here, there shouldn't be pointer duplicates.
				// But lets be safe.
			}
		}
	}
	public void savePtrs() {
		// Write to all pointers.
		for (int i=0; i<ptrs.size(); i++)
		{
			if (ptrs.get(i).isNull())
				ptrs.remove(i--);
			else {
				ptrs.get(i).write(addr, addr/0x4000);
				// Some pointers point to other records.
				// All records are saved when the save button is clicked.
				// But if a record that depends on this one is saved first?
				// That happens sometimes, if it does, it's re-saved right here.
				ptrs.get(i).save();
			}
		}
	}

	public int getNumPtrs() {
		return ptrs.size();
	}

	public void setDescription(String desc) {
		description = desc;
	}
	public String getDescription() {
		return description;
	}
}


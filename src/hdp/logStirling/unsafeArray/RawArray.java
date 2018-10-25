package hdp.logStirling.unsafeArray;

import sun.misc.Unsafe;
import java.lang.reflect.Field;


public class RawArray implements AutoCloseable {

	// --- --- --- Sizes
	protected long LENGTH;						// Length of the array, number of elements.
	protected long ITEM_BYTE_SIZE;				// Size of one element, in bytes
	protected long BYTE_SIZE;					// Total size of the array, in bytes
	
	// --- --- --- Address
	protected long ADDRESS;							// Raw address
	
	// --- --- --- Unsafe
	protected final Unsafe UNSAFE_INSTANCE;			// Unsafe access
	
	// --- --- --- Protected Constructor
	protected RawArray(long length, int itm_bsize) throws NoSuchFieldException, IllegalAccessException {
		
		// --- Unsafe
		UNSAFE_INSTANCE = getUnsafe();
		
		// --- Sizes
		LENGTH = length;
		ITEM_BYTE_SIZE = itm_bsize;
		BYTE_SIZE = length * itm_bsize;
		
		// --- Address
		ADDRESS = UNSAFE_INSTANCE.allocateMemory(BYTE_SIZE);
	}
	
	// --- --- --- Reallocate
	public void reallocate(long new_length) {
		LENGTH = new_length;
		BYTE_SIZE = new_length * ITEM_BYTE_SIZE;
		ADDRESS = UNSAFE_INSTANCE.reallocateMemory(ADDRESS, BYTE_SIZE);
	}
	
	// --- --- --- Info
	public String getInfo() {
		long go = BYTE_SIZE/1073741824;
		long mo = (BYTE_SIZE - (go*1073741824)) / 1048576;
		long ko = (BYTE_SIZE - (go*1073741824) - (mo*1048576)) / 1024;
		return "Array: " + LENGTH + " items, " + ITEM_BYTE_SIZE + " bytes per item, " + go + "G " + mo + "M " + ko + "K ";
	}
	
	public long length() {
		return LENGTH;
	}
	
	// --- --- --- RawZeroed
	public void rawZeroed() {
		UNSAFE_INSTANCE.setMemory(ADDRESS, BYTE_SIZE, (byte) 0);
	}
	
	
	// --- --- --- Autocloseable
	
	@Override
	public void close() {
		if(this.ADDRESS != 0) {
			this.UNSAFE_INSTANCE.freeMemory(ADDRESS);
			this.ADDRESS = 0;
		}
	}
	
	@Override
	public void finalize() throws Throwable {
		close();
		super.finalize();
	}
	
	
	// --- --- --- Unsafe access
	// Literally undocumented java black magic
    private static Unsafe getUnsafe() throws NoSuchFieldException, IllegalAccessException {
        Field singleoneInstanceField = Unsafe.class.getDeclaredField("theUnsafe");
        singleoneInstanceField.setAccessible(true);
        return (Unsafe) singleoneInstanceField.get(null);
    }

}

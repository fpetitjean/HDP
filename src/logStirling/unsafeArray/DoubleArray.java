package logStirling.unsafeArray;

public class DoubleArray extends RawArray {
	
	// --- --- --- Constructor
	public DoubleArray(long size) throws NoSuchFieldException, IllegalAccessException {
		super(size, 8);
	}
	
	// --- --- --- Access, unchecked
	
	// @SuppressWarnings("restriction")
	public void set(long idx, double value) {
		UNSAFE_INSTANCE.putDouble(ADDRESS + idx*ITEM_BYTE_SIZE, value);
	}
	
	// @SuppressWarnings("restriction")
	public double get(long idx) {
		return UNSAFE_INSTANCE.getDouble(ADDRESS+idx*ITEM_BYTE_SIZE);
	}
	
}

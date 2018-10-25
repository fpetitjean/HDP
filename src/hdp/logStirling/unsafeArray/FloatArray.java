package hdp.logStirling.unsafeArray;

public class FloatArray extends RawArray {
	
	// --- --- --- Constructor
	public FloatArray(long size) throws NoSuchFieldException, IllegalAccessException {
		super(size, 4);
	}
	
	// --- --- --- Access, unchecked
	
	// @SuppressWarnings("restriction")
	public void set(long idx, float value) {
		UNSAFE_INSTANCE.putFloat(ADDRESS + idx*ITEM_BYTE_SIZE, value);
	}
	
	// @SuppressWarnings("restriction")
	public float get(long idx) {
		return UNSAFE_INSTANCE.getFloat(ADDRESS+idx*ITEM_BYTE_SIZE);
	}
	
}

package logStirling;

import logStirling.cache.DynCache;

/** Helper class to easily create a Log Stirling Generator */
public class LogStirlingFactory {
	
	
	/** Create a LogStirlingGenerator with a discount parameter, limited to a certain maxN.
	 * @param maxN Max N index (included). Index starts at 1.
	 * @param discountP Discount parameter for the generalised log Stirling numbers
	 * @return A generator with a dynamic cache
	 * @throws NoSuchFieldException If can't access "theUnsafe" field (for raw memory access)
	 * @throws IllegalAccessException If can't access "theUnsafe" field (for raw memory access)
	 */
	public static LogStirlingGenerator newLogStirlingGenerator(long maxN, double discountP)
			throws NoSuchFieldException, IllegalAccessException {
		DynCache mc = new DynCache();
		LogStirlingGenerator lsg = new LogStirlingGenerator(maxN, discountP, mc);
		return lsg;
	}
	
	/** Create a LogStirlingGenerator with a discount parameter, limited to a certain maxN and maxK.
	 * @param maxN Max N index (included). Index starts at 1.
	 * @param maxK Max K index (included). Index starts at 1.
	 * @param discountP Discount parameter for the generalised log Stirling numbers
	 * @return A generator with a dynamic cache
	 * @throws NoSuchFieldException If can't access "theUnsafe" field (for raw memory access)
	 * @throws IllegalAccessException If can't access "theUnsafe" field (for raw memory access)
	 */
	public static LogStirlingGenerator newLogStirlingGenerator(long maxN, long maxK, double discountP)
			throws NoSuchFieldException, IllegalAccessException {
		DynCache mc = new DynCache();
		LogStirlingGenerator lsg = new LogStirlingGenerator(maxN, maxK, discountP, mc);
		return lsg;
	}

}

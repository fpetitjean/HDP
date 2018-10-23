package logStirling;

import org.apache.commons.math3.special.Gamma;
import org.apache.commons.math3.util.FastMath;

import logStirling.unsafeArray.DoubleArray;
import tools.MathUtils;

/*
 * Computation of the Log Stirling Numbers, with a discount parameter.
 * This class implements the computation of those numbers in double precision,
 * while returning them (and storing them in a cache) in single precision.
 * Because the computation of a number S(n,k) relies on S(n-1, k-1) and S(k-1),
 * we must maintain 2 extra "frontier caches", storing needed past results in double precision.
 * 
 * This class does not implement the cache itself, but exposes an interface
 * that must be implemented by a class providing a cache implementation.
 * See the classes cache.FixedCache and cacheDynCache for 2 different implementations.
 * 
 * The numbers are computed on demand, filling the cache as we go.
 * When querying a given number S(n,k) that is not yet available,
 * the functions extends_k and extends_n are used to perform the computation and to store the result.
 * In general, extends_k(k') and extends_n(n') are called with a value greater than
 * then ones being queried, i.e. k'>k and n'>n.
 * This allow to compute a bunch of results in one go instead of repeatedly extending the cache.
 * 
 * When calling extends_{k,n}, the underlying cache is also asked to secure the memory allowing to store
 * the new results. Depending on the cache implementation, those functions may return a value different
 * than the one being asked for! For example, the FixedCache will not "agree" to a value larger than the one
 * it was constructed with, and the dynamic cache allocate by "chunk".
 * Example:
 * 	- Imagine that the cache store the number up to S(N,K) 
 * 	- We query S(n,k), with n>N and k<=K: we must extends over n.
 *  - We call extends_n(n*EXTENDS_RATIO)
 *    - extends_n ask the cache to extends its row capacity
 *    - the cache return N' which may be different (greater or lower) than n*EXTENDS_RATIO 
 *    - values are computed up to S(N', K)
 *  - If N'>n, the extension succeed, and we return the result S(n,k)
 *  - Else, the cache could not extends up to n, and the exception CacheExtensionException is thrown
 *  
 *  Note: See Tools for the debugging flag.
 */

public class LogStirlingGenerator implements AutoCloseable {

	
	
	// --- --- --- --- --- --- --- --- --- --- --- ---
	// Constants
	// --- --- --- --- --- --- --- --- --- --- --- ---

	// Extend the line by a ratio when growing the caches (golden ratio).
	public final double EXTENDS_RATIO = 1.618;

	
	
	// --- --- --- --- --- --- --- --- --- --- --- ---
	// Fields
	// --- --- --- --- --- --- --- --- --- --- --- ---

	// Generalized Log Stirling Number. Put 0 for the "classic" one.
	public final double discountP;

	// The main cache (MainCache is an inner interface, see below).
	// It stores the number in single precision up to S(boundingN, boundingK).
	//  - N is the number of items in the dataset and is the max value for boundingN.
	//  - K is the max possible value for boundingK. Optional: ignore if set to 0.
	private final MainCache cache;
	private long boundingN, boundingK;
	private final long N, K;

	// Last row bounding cache.
	// Grows with extends_k (we add columns at the end of the row).
	// After extends_k, boundingRow.length() == boudingK
	private final DoubleArray boundingRow;

	// Last column bounding cache.
	// Grows with extends_n (we add lines at the end of the column).
	// After extends_n, boundingCol.length() == boundingN
	private final DoubleArray boundingCol;

	
	
	// --- --- --- --- --- --- --- --- --- --- --- ---
	// Constructor
	// --- --- --- --- --- --- --- --- --- --- --- ---
	
	/** Construct a new generator with a maximal N and K, discount parameter and a cache.
	 *  The K parameter is optional. It is ignore if set to 0.
	 * @param maxN The maximum possible value for N. Usually, the number of items in a dataset.
	 * @param maxK The maximum possible value for K. May be bounded to limit memory usage.
	 * @param discountP The discount parameter. Set to 0 for "classic log Stirling number"
	 * @param mc An instance of a MainCache implementation to be used as the cache.
	 * @throws NoSuchFieldException If can't access "theUnsafe" field (for raw memory access)
	 * @throws IllegalAccessException If can't access "theUnsafe" field (for raw memory access)
	 */
	public LogStirlingGenerator(long maxN, long maxK, double discountP, MainCache mc)
			throws NoSuchFieldException, IllegalAccessException {

		this.discountP = discountP;
		this.cache = mc;
		this.N = maxN;
		this.K = maxK;

		boundingCol = new DoubleArray(1);
		boundingRow = new DoubleArray(1);
		boundingN = boundingK = 1;

		boundingCol.rawZeroed();
		boundingRow.rawZeroed();
	}
	
	/** Construct a new generator with a maximal N, discount parameter and a cache.
	 *  The 'K' dimension is unlimited. 
	 * @param maxN The maximum possible value for N. Usually, the number of items in a dataset.
	 * @param discountP The discount parameter. Set to 0 for "classic log Stirling number"
	 * @param mc An instance of a MainCache implementation to be used as the cache.
	 * @throws NoSuchFieldException If can't access "unsafe" fields (for raw memory access)
	 * @throws IllegalAccessException If can't access "unsafe" fields (for raw memory access)
	 */
	public LogStirlingGenerator(long maxN, double discountP, MainCache mc)
			throws NoSuchFieldException, IllegalAccessException {
		this(maxN, 0, discountP, mc);
	}


	
	// --- --- --- --- --- --- --- --- --- --- --- ---
	// Main cache
	// --- --- --- --- --- --- --- --- --- --- --- ---
	// The main cache is abstracted through an interface

	/** MainCache interface allowing to abstract the cache structure */
	public interface MainCache extends AutoCloseable {

		/** Write in the main cache. Base indices are (1,1).
		 *  Usually, we have write(n, k, S(n,k)).
		 *  Behaviour is unspecified if out of bound.
		 * @param n Write at the given 'n' coordinate. Index starts at 1.
		 * @param k Write at the given 'k' coordinate. Index starts at 1.
		 * @param value The value to write
		 */
		public void set(long n, long k, float value);

		/** Read the value stored in the cache at (n,k). Bases indices are (1,1)
		 * Behaviour is unspecified if out of bound.
		 * @param n Read at the given 'n' coordinate. Index starts at 1.
		 * @param k Read at the given 'k' coordinate. Index starts at 1.
		 * @return The value stored at (n,k).
		 */
		public float get(long n, long k);
		
		/** Extends the cache over the k dimension, specifying the desired new maximal index (i.e. 'k' is an index).
		 *  The cache does not have to fulfil the request: the returned value gives us the actual maximal index
		 *  up to which we have available memory. 
		 * @param k New desired maximal index for k. Index starts at 1.
		 * @return Actual maximal index available.
		 */
		public long extends_k(long k);

		/** Extends the cache over the n dimension, specifying the desired new maximal index (i.e. 'n' is an index).
		 *  The cache does not have to fulfil the request: the returned value gives us the actual maximal index
		 *  up to which we have available memory. 
		 * @param n New desired maximal index for n. Index starts at 1.
		 * @return Actual maximal index available.
		 */
		public long extends_n(long n);
	}

	/** Exception: can not extends the cache to query a given S(n,k)
	 *  Remark: This exception is not thrown by the MainCache, but by the query function
	 *          when the cache can not fulfil an extension request allowing to computed the desired S(n,k).
	 */
	public class CacheExtensionException extends Exception {

		private static final long serialVersionUID = 1L;

		public CacheExtensionException(String message) {
			super(message);
		}
	}

	
	
	// --- --- --- --- --- --- --- --- --- --- --- ---
	// Bounding caches functions
	// --- --- --- --- --- --- --- --- --- --- --- ---
	// Abstract the reading from/writing to the bounding cache.
	// Mainly debug code here! Deactivate the debug test by putting 'DEBUG=false' in
	// Tools

	
	/** Get the item (n, k) from the boundingCol cache (so we index it with a row, i.e. n, number).
	 *  Only n is really used, k is here for debugging. Base indices are (1,1).
	 * @param n Look in the column cache at index n. Index starts at 1
	 * @param k Used in debug mode
	 * @return The double value stored in the column cache
	 */
	private double queryCacheCol(long n, long k) {
		if (Tools.DEBUG) { // DEBUG ON
			// Bound checking
			if (n > boundingCol.length()) {
				Tools.shouldNotHappen("The index n=" + n + " is out of bound, should be =< " + boundingCol.length());
				return 0.0; // never reached
			}
			// Value checking
			if (n < k) {
				Tools.shouldNotHappen("The index n should not be lower than k. We have n=" + n + " < " + k + "=k");
				return 0.0; // never reached
			} else if (n == k) {
				double val = boundingCol.get(n - 1);
				if (val != 0.0) {
					Tools.shouldNotHappen("We have n=k. Cache contains " + boundingCol.get(n - 1) + ", should be 0.0");
					return 0.0; // never reached
				} else {
					return val;
				}
			} else {
				// General case: implicitly, 'k' always matches the column being stored in boundingCol
				// Index correction (1,1) -> (0,0).
				return boundingCol.get(n - 1);
			}
		} else { // DEBUG OFF
			// Index correction (1,1) -> (0,0).
			return boundingCol.get(n - 1);
		}
	}

	/** Get the item (n, k) from the boundingRow cache (so we index it with a column, i.e. k, number).
	 *  Only k is really used, n is here for debugging. Base indices are (1,1).
	 * @param n Used in debug mode
	 * @param k Look in the row cache at index k. Index starts at 1
	 * @return The double value stored in the row cache
	 */
	private double queryCacheRow(long n, long k) {
		if (Tools.DEBUG) { // DEBUG ON
			// Bound checking
			if (k > boundingRow.length()) {
				Tools.shouldNotHappen("The index k=" + k + " is out of bound, should be =< " + boundingRow.length());
				return 0.0; // never reached
			}
			// Value checking
			if (n < k) {
				Tools.shouldNotHappen("queryCacheRow: n should not be lower than k. We have n=" + n + " < " + k + "=k");
				return 0.0; // never reached
			} else if (n == k) {
				double val = boundingRow.get(k - 1);
				if (val != 0.0) {
					Tools.shouldNotHappen("queryCacheRow: n=k=" + n + ". Cache contains " + boundingCol.get(n - 1)
							+ " should be 0.0");
					return 0.0; // never reached
				} else {
					return val;
				}
			} else {
				// Index correction (1,1) -> (0,0).
				return boundingRow.get(k - 1);
			}
		} else { // DEBUG OFF
			// Index correction (1,1) -> (0,0).
			return boundingRow.get(k - 1);
		}
	}
	
	/** Write a value in the column boundingCol cache (so we index it with a row, i.e. n, number).
	 * @param n Row number. Index starts at 1.
	 * @param value The value to put in cache
	 */
	private void setCacheCol(long n, double value) {
		if (Tools.DEBUG) {
			// Bound checking
			if (n > boundingCol.length()) {
				Tools.shouldNotHappen("The index n=" + n + " is out of bound, should be =< " + boundingCol.length());
			}
		}
		// Index correction 1->0
		boundingCol.set(n - 1, value);
	}

	/** Write a value in the row boundingRow cache (so we index it with a column, i.e. k, number).
	 * @param k Column number. Index starts at 1.
	 * @param value The value to put in cache
	 */
	private void setCacheRow(long k, double value) {
		if (Tools.DEBUG) {
			// Bound checking
			if (k > boundingRow.length()) {
				Tools.shouldNotHappen("The index k=" + k + " is out of bound, should be =< " + boundingRow.length());
			}
		}
		// Index correction 1->0
		boundingRow.set(k - 1, value);
	}

	
	
	// --- --- --- --- --- --- --- --- --- --- --- ---
	// Dynamic extension functions
	// --- --- --- --- --- --- --- --- --- --- --- ---
	
	/** Attempts to extend the cache along K of log Stirling numbers up to the provided index.
	 *  This is constrained by the cache growing policy. See MainCache.extends_k.
	 *  After the extends itself, log Stirling numbers are computed using (and updating) the frontier caches. 
	 * @param upToK_ The desired new maximal index. Index starts at 1.
	 * @return The actual upper K index up to which the numbers have been computed.
	 */
	private long extends_k(long upToK_) {
		// Extend the cache and the bounding row.
		// The cache may return a value larger than uptoK_: cap it to K
		long upToK = cache.extends_k(upToK_);
		if (K != 0 && upToK > K) { upToK = K; }
		boundingRow.reallocate(upToK);

		// Note: this algo is not the best cache-wise!
		// It would be better to advance row by row rather than column by column.
		// However, our "cache frontier" is made of the bottom row and last column:
		// we can only add column by column here. It is possible to improve this,
		// but it is not worth the (code) complexity.
		// Also, the extension in K are usually quite limited compared to the extension in N,
		// and this is taken into account in the 'query' function.

		// For each 'col', after the last computed one, up to "up to K" or boundingN.
		// Complete all the rows from 'col+1' up to boundingN.
		for (long col = boundingK + 1; col <= Long.min(upToK, boundingN); ++col) {
			// We implicitly start with the diagonal S(col, col) = 0
			// When computing an item in the new column, 'vert' is the previous result.
			double result = 0.0;
			for (long row = col + 1; row <= boundingN; ++row) {
				// Compute the result
				double diag = queryCacheCol(row - 1, col - 1);
				double vert = result;
				result = MathUtils.logadd(diag, FastMath.log((row - 1) - col * discountP) + vert);

				// Update the caches
				cache.set(row, col, (float) result);

				// Update the boundingCol cache: we have to update it with a lag of 1 step
				// (to prevent erasing info needed by the next row): in other word, we have
				// to update the cache with the value stored in 'vert', storing this at row-1 and not row.
				setCacheCol(row - 1, vert);
			}

			// Store the last result in the bounding cache.
			// Adding a column extends the rows: also update the row cache!
			setCacheCol(boundingN, result);
			setCacheRow(col, result);
		}

		return upToK;
	}

	/** Attempts to extend the cache along N of log Stirling numbers up to the provided index.
	 *  This is constrained by the cache growing policy. See MainCache.extends_n.
	 *  After the extends itself, log Stirling numbers are computed using (and updating) the frontier caches. 
	 * @param upToN_ The desired new maximal index. Index starts at 1.
	 * @return The actual upper N index up to which the numbers have been computed.
	 */
	private long extends_n(long upToN_) {
		// Extend the cache and the bounding column
		// The cache may return a value larger than uptoN_: cap it to N
		long upToN = cache.extends_n(upToN_);
		if (upToN > N) { upToN = N;	}
		boundingCol.reallocate(upToN);

		// For each new 'row', after the last computed one, 'upToN'.
		for (long row = boundingN + 1; row <= upToN; ++row) {
			// Initialise first cell S(row, 1)
			double result = Gamma.logGamma(row - discountP) - Gamma.logGamma(1 - discountP);
			cache.set(row, 1, (float) result);
			// Compute the result from col=2 up to boundingK or row-1 (reaching without touching the diagonal)
			final long lastCol = Long.min(boundingK, row - 1);
			for (long col = 2; col <= lastCol; ++col) {
				// Get the needed value from the frontier caches
				double diag = queryCacheRow(row - 1, col - 1);
				// Vert may be "touching" the previous diagonal. This only happens for the last item of the loop,
				// when lastCol = row-1. After testing, it is not worth putting this last case out of the loop.
				double vert = (row - 1 == col) ? 0.0 : queryCacheRow(row - 1, col);

				// Storing previous result with a lag of 1 to prevent overwriting needed values from the frontier cache.
				// Must be done AFTER the query on the diagonal
				setCacheRow(col - 1, result);

				// Compute new result and update the main cache
				result = MathUtils.logadd(diag, FastMath.log((row - 1) - col * discountP) + vert);
				cache.set(row, col, (float) result);
			}
			// Store the last result.
			// Adding a row extends the columns: also update the column cache!
			setCacheRow(lastCol, result);
			setCacheCol(row, result);
		}

		return upToN;
	}

	
	
	// --- --- --- --- --- --- --- --- --- --- --- ---
	// LogStirling computation
	// --- --- --- --- --- --- --- --- --- --- --- ---

	/** Compute the log generalized Stirling number S(n,k) in double precision,
	 *  but returns/stores it in cache in single precision.
	 *  Note: The discount parameter discountP is set at construction.
	 * @param The n in S(n,k)
	 * @param The k in S(n,k)
	 * @return S(n,k)
	 * @throws CacheExtensionException If the cache extension can not fulfil a size of (n, k).
	 */
	public float query(long n, long k) throws CacheExtensionException {
		// The first worthy Stirling Number (2nd kind) is S(2,1)
		// Special cases, order matters: n == k takes precedence over k == 0!
		// - Case n == k: S(n,n) = 1   Log(S(n,n)) = 0
		// - Case k == 0: S(_,0) = 0   Log(S(_,0)) = NEGATIVE INFINITY
		// - Case n < k:  S(n,k) = 0   Log(S(n,k)) = NEGATIVE INFINITY

		if (n == k) { return 0;	}
		else if (k == 0 || n < k) {	return Float.NEGATIVE_INFINITY;	}
		else {
			// Check the bounds and extends if needed.
			// Extends k first: gives us the opportunity to add "longer row" with extends_n, which is more efficient.

			// Check k dimension. Cap to K (if K != 0) while trying to grow by extension steps
			if (k > boundingK) {
				long nk = Long.max((long)(boundingK*EXTENDS_RATIO), k);
				if (K != 0 && nk > K) {	nk = K; }
				nk = extends_k(nk);
				if (k > nk) {
					String msg = "Cannnot extends the cache to query k = " + k + ". Cache extended up to + " + nk + ".";
					throw new CacheExtensionException(msg);
				}
				boundingK = nk;
				assert (boundingRow.length() == boundingK);
			}

			// Check n dimension. Cap to N while trying to grow by extension steps.
			if (n > boundingN) {
				long nn = Long.min(N, Long.max((long)(boundingN * EXTENDS_RATIO), n));
				nn = extends_n(nn);
				if (n > nn) {
					String msg = "Cannnot extends the cache to query n = " + n + ". Cache extended up to + " + nn + ".";
					throw new CacheExtensionException(msg);
				}
				boundingN = nn;
				assert (boundingCol.length() == boundingN);
			}

			// Get the result.
			return cache.get(n, k);
		}
	}

	
	
	// --- --- --- --- --- --- --- --- --- --- --- ---
	// AutoCloseable interface
	// --- --- --- --- --- --- --- --- --- --- --- ---
	public void close() throws Exception {
		cache.close();
		boundingCol.close();
		boundingRow.close();
	}
}

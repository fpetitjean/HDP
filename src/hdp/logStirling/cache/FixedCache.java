package hdp.logStirling.cache;

import hdp.logStirling.LogStirlingGenerator;
import hdp.logStirling.unsafeArray.FloatArray;

/*
 * By convention: (0,0) map to the index 0
 * 
 * Size N = 9       *  Size K=4 *
 *                  * 0 * 1 * 2 * 3 *
 *                0 | 0 |   |   |   |   Sub Array 1. Indices from (0,0) to (3,3) -- ONLY IF n < N1
 * Size N1=Y      1 | 1 | 2 |   |   |   1D Size = Sum(4) = (N1 * (N1 + 1) ) / 2 = 10
 *      N1=4      2 | 3 | 4 | 5 |   |   1D Index(n,k) = Sum(n) + k
 *                3 | 6 | 7 | 8 | 9 |   1D Index(3,2) = Sum(3) + 2 = 6 + 2 = 8 
 *  --------------------------------------             
 *                4 | 10| 11| 12| 13|   Sub Array 2. Indices from (0,0) to (4,3) -- ONLY IF n >= N1
 * Size N2=N-N1   5 | 14| 15| 16| 17|   1D Size = N2 * K = 20
 *      N2=5      6 |   |   |   |   |   1D Index(n,k) = SubArray1 Size + ((n-N1)*K) + k
 *                7 |   |   |   |   |   1D Index(5,3) = 10 + (5-4)*4+3 = 10 + 4 + 3 = 17
 *         Nmax = 8 |   |   |   |   |
 *   
 */

public class FixedCache implements LogStirlingGenerator.MainCache {

	// --- --- --- --- --- --- --- --- --- --- --- ---
	// Fields
	// --- --- --- --- --- --- --- --- --- --- --- ---

	// Main cache: one big array that will be manage as two sub arrays.
	// SIZE = number of item in the cache
	// N = number of rows in the cache. K = (maximum) number of column in the cache
	public final FloatArray CACHE;
	public final long SIZE, N, K;

	// The cache is managed as two sub-array, SA1 and SA2.
	// SA1_SIZE and SA2_SIZE are the numbers of items in SA1 and SA2
	// N1 = number of rows of SA1, N2 = number of rows of SA2.
	public final long SA1_SIZE, N1;
	public final long SA2_SIZE, N2;

	// The two sub-arrays are manage in one big array, SA2 being put after SA1.
	// SA2_BASE is the the base index for SA2 in this array.
	// Note: this is actually a negative number! See 'get_index' for explanations
	public final long SA2_BASE;
	
	
	// --- --- --- --- --- --- --- --- --- --- --- ---
	// Constructor
	// --- --- --- --- --- --- --- --- --- --- --- ---
	public FixedCache(long N, long K) throws NoSuchFieldException, IllegalAccessException {

		// Store constructor parameters.
		this.N = N;
		this.K = K;

		// Determine N1 and N2 for sub arrays 1 and 2
		this.N1 = K;
		this.N2 = (N > K) ? N - N1 : 0; // If N<=K; we do not have a SubArray 2

		// Compute the max size of each sub array, giving us the full size of the array
		this.SA1_SIZE = (N1 * (N1 + 1)) / 2;
		this.SA2_SIZE = N2 * K;
		this.SIZE = SA1_SIZE + SA2_SIZE;
		this.SA2_BASE = SA1_SIZE - (N1*K);	// See get_index

		// Allocate the main cache.
		// Note: This cache use raw memory access, and not a Java managed array.
		//       Depending on the operating system, the memory may be "virtually allocated",
		//		 i.e. we do not have physical RAM until we actually use raw memory address.
		//       Touching all the RAM now ensure that we indeed have our memory.
		this.CACHE = new FloatArray(SIZE);
		CACHE.rawZeroed(); // Better to crash early with a "out of memory" when the code is launched...	
	}
	
	// Base index is (0,0)
	private long get_index(long n, long k) {
		// In sub array 1: Sum(n) + k
		if (n < N1) {
			return (n * (n + 1) / 2) + k;
		}
		else {
			// In sub array 2.
			// Index is: SA1_SIZE + ((n-N1)*K) + k
			//           SA1_SIZE + n*K - N1*K + k
			//           (SA1_SIZE-N1*K) + n*K + k.
			// With SA2_BASE = SA1_SIZE-N1*K, we have:
			return SA2_BASE + n*K + k;
		}
	}
	

	// --- --- --- --- --- --- --- --- --- --- --- ---
	// Implementations of the interface
	// --- --- --- --- --- --- --- --- --- --- --- ---

	/**
	 * Write in the main cache. Base indices are (1,1).
	 */
	public void set(long n, long k, float value) {
		CACHE.set(get_index(n - 1, k - 1), value);
	}

	/**
	 * Read from the main cache. Base indices are (1,1).
	 */
	public float get(long n, long k) {
		return CACHE.get(get_index(n - 1, k - 1));
	}

	/**
	 * Extension over k requested. Base index is 1.
	 * The size of this cache is fixed from the start.
	 * Cap the extension to what has been fixed.
	 */
	public long extends_k(long k) {
		return Long.min(k,  K);
	}

	/**
	 * Extension over n requested Base index is 1.
	 * The size of this cache is fixed from the start.
	 * Cap the extension to what has been fixed.
	 */
	public long extends_n(long n) {
		return Long.min(n,N);
	}

	
	@Override
	public void close() throws Exception {
		CACHE.close();
	}

}

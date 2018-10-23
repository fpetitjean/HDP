package logStirling.cache;

import java.util.ArrayList;

import logStirling.LogStirlingGenerator;

public class DynCache implements LogStirlingGenerator.MainCache {

	// --- --- --- --- --- --- --- --- --- --- --- ---
	// Constants
	// --- --- --- --- --- --- --- --- --- --- --- ---
	
	private final int N1 = 7680;				// Approx 110Mo of triangular cache (in float); 15*CHUNKSIZE
	private final int CHUNK_SIZE = 512;			// Approx 2Ko

	
	
	// --- --- --- --- --- --- --- --- --- --- --- ---
	// Fields
	// --- --- --- --- --- --- --- --- --- --- --- ---

	private final float[] cache1;
	private final ArrayList<ArrayList<float[]>> cache2;
	private int nbchunk;
	
	
	
	// --- --- --- --- --- --- --- --- --- --- --- ---
	// Constructor
	// --- --- --- --- --- --- --- --- --- --- --- ---
	
	public DynCache() throws NoSuchFieldException, IllegalAccessException {
		cache1 = new float[N1*(N1+1)/2];	// Triangular cache
		cache2 = new ArrayList<>();
		nbchunk = N1/CHUNK_SIZE+1;
	}

	


	/**
	 * Write in the main cache. Base indices are (1,1).
	 */
	public void set(long n_, long k_, float value) {
		int n = (int)(n_ -1);
		long k = k_-1;

		if(n<N1) {
			// First triangular cache
			int idx = (n * (n + 1) / 2) + ((int)k);
			cache1[idx] = value;
		} else {
			// Second dynamic cache
			int n1 = n-N1;
			int k1 = (int)(k/CHUNK_SIZE);
			int k2 = (int)(k - ((long)CHUNK_SIZE*k1) );
			cache2.get(n1).get(k1)[k2] = value;
		}
	}

	/**
	 * Read from the main cache. Base indices are (1,1).
	 */
	public float get(long n_, long k_) {
		int n = (int)(n_ -1);
		long k = k_-1;
		
		if(n<N1) {
			// First triangular cache
			return cache1[(n * (n + 1) / 2) + ((int)k)];
		} else {
			// Second 
			int n1 = n-N1;
			int k1 = (int)(k/CHUNK_SIZE);
			int k2 = (int)(k - ((long)CHUNK_SIZE*k1) );
			return cache2.get(n1).get(k1)[k2];
		}
	}

	/**
	 * Extension over k requested. Base index is 1.
	 * Return the actual extension top bound, inclusive
	 */
	public long extends_k(long k) {
		if(k>=N1) {
			// We can only index with int...
			int nk = (int)Long.min( (k/CHUNK_SIZE)+1, Integer.MAX_VALUE );
			
			// For all rows, add the chunks
			for(ArrayList<float[]> row : cache2) {
				row.ensureCapacity(nk);
				for(int i=nbchunk; i<nk; ++i) {
					row.add(new float[CHUNK_SIZE]);
				}
			}
			
			// Store the new number of chunk
			nbchunk = nk;
			
			// Return new allocated size, which is a multiple of nk
			return nk*CHUNK_SIZE;

		} else {
			return k;
		}
	}

	/**
	 * Extension over n requested Base index is 1.
	 * Return the actual extension top bound, inclusive
	 */
	public long extends_n(long n) {
		if(n>=N1) {
			
			// We can only index with int. Remove the N1 first.
			int nn = (int)Long.min( n-N1, Integer.MAX_VALUE );
			
			//
			cache2.ensureCapacity(nn);
			for(int i=cache2.size(); i<nn; ++i) {
				// New row:
				ArrayList<float[]> row = new ArrayList<float[]>(); 
				cache2.add(row);
				// New chunks in the row:
				row.ensureCapacity(nbchunk);
				for(int j=0; j<nbchunk;++j) {
					row.add(new float[CHUNK_SIZE]);
				}
			}
			
			// Add again N1 to the result
			return nn+(long)N1;
			
		} else {
			return n;
		}
	}
	
	
	@Override
	public void close() throws Exception { }
	
}

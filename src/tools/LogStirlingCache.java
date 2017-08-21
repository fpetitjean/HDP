package tools;

import java.util.Arrays;

import org.apache.commons.math3.special.Gamma;
import org.apache.commons.math3.util.FastMath;

public class LogStirlingCache {

	double a;
	int N, lastValidM,lastValidN;
	double[][] S;

	public LogStirlingCache(double a, int N, int maxM) {
		this.a = a;
		this.N = N;
		this.lastValidM = maxM;
		createCache();
	}

	public LogStirlingCache(double a, int N) {
		this.a = a;
		this.N = N;
		this.lastValidM = Math.min(1000, N);
		this.lastValidN = Math.min(100000, N);
		createCache();
	}

	protected synchronized void createCache() {
		System.out.println("Creating log stirling cache for parameters: a="+a+", N="+N+", lastValidM="+lastValidM);
		S = new double[N + 1][];
		// S[0] not allocated
		S[1] = new double[] { 0.0 };
		for (int n = 2; n < lastValidN+1; n++) {
			int M = Math.min(lastValidM+1, n + 1);
			S[n] = new double[M];

			// fill first cell using loggamma (S^n_1)
			S[n][1] = Gamma.logGamma(n - a) - Gamma.logGamma(1 - a);
			for (int m = 2; m < S[n].length; m++) {
				if (n == m) {
					S[n][m] = 0;
				} else {
					double diag = S[n - 1][m - 1]; // S^{n-1}_{m-1}
					double vert = S[n - 1][m]; // S^{n-1}_{m}
					S[n][m] = MathUtils.logadd(diag, FastMath.log((n - 1) - m * a) + vert);
				}
			}
		}
	}
	
	protected synchronized void extendCache(int wantedLatValidM){
		System.out.println("extending cache to be able to query m="+wantedLatValidM+"");
		for (int n = 2; n < lastValidN+1; n++) {
			int oldM = S[n].length;
			int M = Math.min(wantedLatValidM+1, n + 1);
			S[n] = Arrays.copyOf(S[n], M);

			for (int m = oldM; m < S[n].length; m++) {
				if (n == m) {
					S[n][m] = 0;
				} else {
					double diag = S[n - 1][m - 1]; // S^{n-1}_{m-1}
					double vert = S[n - 1][m]; // S^{n-1}_{m}
					S[n][m] = MathUtils.logadd(diag, FastMath.log((n - 1) - m * a) + vert);
				}
			}
		}
		this.lastValidM = wantedLatValidM;
		
	}
	
	protected synchronized void extendCacheN(int wantedLatValidN){
		System.out.println("extending cache to be able to query N="+wantedLatValidN+"");
		for (int n = lastValidN+1; n < wantedLatValidN+1; n++) {
			int M = Math.min(lastValidM+1, n + 1);
			S[n] = new double[M];

			// fill first cell using loggamma (S^n_1)
			S[n][1] = Gamma.logGamma(n - a) - Gamma.logGamma(1 - a);
			for (int m = 2; m < S[n].length; m++) {
				if (n == m) {
					S[n][m] = 0;
				} else {
					double diag = S[n - 1][m - 1]; // S^{n-1}_{m-1}
					double vert = S[n - 1][m]; // S^{n-1}_{m}
					S[n][m] = MathUtils.logadd(diag, FastMath.log((n - 1) - m * a) + vert);
				}
			}
		}
		System.out.println(Arrays.toString(S[wantedLatValidN]));
		this.lastValidN = wantedLatValidN;
		
	}

	public String toString() {
		String res = "";
		for (int n = 0; n < S.length; n++) {
			for (int m = 0; S[n] != null && m < S[n].length; m++) {
				res += "S(" + n + "," + m + ")=" + S[n][m] + "\n";
			}
		}
		return res;
	}

	

	/**
	 * Return the log of the stirling number cached, or NaN if not allocated
	 * 
	 * @param a
	 * @param n
	 * @param m
	 * @return
	 */
	public double query(double a, int n, int m) {
		if (n == m) {
			return 0;
		} else if (n < m || m == 0) {
			return Double.NEGATIVE_INFINITY;
		} else if (n > N) {
			System.out.println("n="+n+"\tN="+N);
			throw new RuntimeException("cache not initialized for this query");
		}
		if(a != this.a) {
			this.a = a;
			createCache();
		}
		if (m > lastValidM) {
			//try to reallocate the cache
			extendCache(m+99);
		}
		if(n>lastValidN){
			//try to reallocate the cache
			int newN;
			if(lastValidN>1000000){
				newN = (int) (lastValidN*1.1);
			}else{
				newN = (int) (lastValidN*1.5);
			}
			extendCacheN(Math.max(n,newN));
		}
		return S[n][m];
	}
	
	public double getA(){
		return a;
	}
	
	public static void main(String... args) {
		System.out.println(Gamma.logGamma(0.0));
		LogStirlingCache cache = new LogStirlingCache(0.99, 100, 100);
		System.out.println(cache);
	}

}

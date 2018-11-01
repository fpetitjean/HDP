package hdp.tools;

import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.util.FastMath;

import hdp.Concentration;

public class MathUtils {

	/***
	 * compute log(\Gamma(t+n) / \Gamma(t))
	 * 
	 * @param t
	 * @param n
	 * @return
	 */
	public static double logGammaRatio(double t, int n) {

		double lgr = 0.0;
		for (int i = 0; i < n; i++) {
			lgr += FastMath.log(i + t);
		}
		return lgr;

	}

	/***
	 * compute log((c|d)_T)
	 * 
	 * @param c
	 * @param N
	 * @param d
	 * @return
	 */

	public static double logPochhammerSymbol(Concentration c, double d, int N) {

		double lps = 0.0;

		if (d == 0) {
			lps = N * c.getLogConcentration();
		} else {
			lps = N * FastMath.log(d) + logGammaRatio(c.getConcentration() / d, N);
		}

		return lps;
	}

	public static final double logadd(double V, double lp) {
		if (lp > V) {
			// swap so V is bigger
			double t = lp;
			lp = V;
			V = t;
		}
		return V + FastMath.log(1.0 + FastMath.exp(lp - V));
	}

	public static void normalizeInLogDomain(double[] logs) {
		double logSum = sumInLogDomain(logs);
		for (int i = 0; i < logs.length; i++)
			logs[i] -= logSum;
	}

	public static double sumInLogDomain(double[] logs) {
		// first find max log value
		double maxLog = logs[0];
		int idxMax = 0;
		for (int i = 1; i < logs.length; i++) {
			if (maxLog < logs[i]) {
				maxLog = logs[i];
				idxMax = i;
			}
		}
		// now calculate sum of exponent of differences
		double sum = 0;
		for (int i = 0; i < logs.length; i++) {
			if (i == idxMax) {
				sum++;
			} else {
				sum += FastMath.exp(logs[i] - maxLog);
			}
		}
		// and return log of sum
		return maxLog + FastMath.log(sum);
	}

	public static void exp(double[] logs) {
		for (int c = 0; c < logs.length; c++) {
			logs[c] = FastMath.exp(logs[c]);
		}
	}

	public static final int sampleFromMultinomial(RandomGenerator rdg, double[] probs) {
		double rand = rdg.nextDouble();
		int chosenValue = 0;
		double sumProba = probs[chosenValue];
		while (rand > sumProba) {
			chosenValue++;
			sumProba += probs[chosenValue];
		}
		return chosenValue;
	}
	
	// --- --- --- Penny's MEsti
	
	public static double m_MEsti = 1.0;
	
	public static double MEsti(double freq1, double freq2, double numValues) {
		double mEsti = (freq1 + m_MEsti / numValues) / (freq2 + m_MEsti);
//		System.out.println(m_MEsti+" - "+mEsti);
		if(Double.isNaN(mEsti) && freq1==0.0){
			mEsti = 0.0;
		}
		return mEsti;
	}
	
	 public static int sum(int[] int_array) {
		 int acc = 0;
		 for (int item : int_array) { acc += item;}
		 return acc;
	}
	
}

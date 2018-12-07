package monash.ml.hdp;

import java.util.ArrayList;

import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.distribution.GammaDistribution;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.util.FastMath;

import static monash.ml.tools.MathUtils.*;

public class Concentration {
	private double priorRate = 1.0;
	private static final double priorShape = 2.0;
	private double c;
	ArrayList<Float> logGammaRatioCache;
	int indexLastValidLogGammaRatio;
	private double logC;

	ArrayList<ProbabilityNode> tiedNodes;

	public Concentration(double c) {
		setConcentration(c);
		this.priorRate = priorShape / c;
		computeLogGammaRatioCache();
	}

	public Concentration() {
		this(2.0);
	}

	public Concentration(int nOutcomesForTarget) {
		this(priorShape / nOutcomesForTarget);
	}

	public void addNode(ProbabilityNode node) {
		if (tiedNodes == null) {
			tiedNodes = new ArrayList<>();
		}
		tiedNodes.add(node);
	}

	/***
	 * Caches ratios of logGamma for current concentration log(\Gamma(c+n) /
	 * \Gamma(c))
	 * 
	 * @param t
	 * @param n
	 * @return
	 */
	private void computeLogGammaRatioCache() {
		if (logGammaRatioCache == null) {
			logGammaRatioCache = new ArrayList<>();
		}
		if (0 < logGammaRatioCache.size()) {
			logGammaRatioCache.set(0, 0.0f);
		} else {
			logGammaRatioCache.add(0.0f);// 0 case
		}
		indexLastValidLogGammaRatio = 0;
		extendLogGammaRatioCache(10);
	}

	/***
	 * Extends the cache of logGammaRatios to upTo
	 * 
	 * @return
	 */
	private void extendLogGammaRatioCache(int upTo) {
		if (logGammaRatioCache == null) {
			computeLogGammaRatioCache();
		}
		for (int i = indexLastValidLogGammaRatio + 1; i <= upTo; i++) {
			double val = logGammaRatioCache.get(i - 1) + FastMath.log((i - 1) + c);
			if (i < logGammaRatioCache.size()) {
				logGammaRatioCache.set(i, (float) val);
			} else {
				logGammaRatioCache.add((float) val);
			}
		}
		indexLastValidLogGammaRatio = upTo;
		logGammaRatioCache.trimToSize();
	}

	public void setConcentration(double c) {
		double flooredC = Math.min(4000, c);
		if (this.c == flooredC)
			return;

		this.c = flooredC;
		this.logC = FastMath.log(c);
		computeLogGammaRatioCache();
	}

	public double getConcentration() {
		return this.c;
	}

	public double getLogConcentration() {
		return this.logC;
	}

	public float logGammaRatioForConcentration(int n) {
		if (n > indexLastValidLogGammaRatio) {
			extendLogGammaRatioCache(n + 50);
		}
		return logGammaRatioCache.get(n);
	}

	/**
	 * This function samples the value of C for this node. See Lan Du's PhD thesis
	 * about sampling concentration (Chapter 5 - Section 4.3)
	 */
	public void sample(RandomGenerator rng) {
		double rate = priorRate;
		int sumTk = 0;
		for (ProbabilityNode node : tiedNodes) {
			BetaDistribution betaD = new BetaDistribution(rng, this.c, node.marginal_nk);
			double q = Math.max(1e-75, betaD.sample());
			rate += FastMath.log(1.0 / q);
			sumTk += node.marginal_tk;
		}
		double scale = 1.0 / rate;
		// marginal nk here is \sum_{child}child.marginal_tk
		GammaDistribution gammaD = new GammaDistribution(rng, sumTk + priorShape, scale);
		this.setConcentration(gammaD.sample());
	}

	public String toString() {
		return "" + c;
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
}

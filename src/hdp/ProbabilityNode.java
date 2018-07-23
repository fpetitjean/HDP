package hdp;

import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.util.FastMath;

import tools.MathUtils;

public class ProbabilityNode {
	/**
	 * True count
	 */
	int[] nk;
	/**
	 * sum of nk
	 */
	int marginal_nk;

	/**
	 * Simulated count
	 */
	int[] tk;
	/**
	 * sum of tk
	 */
	int marginal_tk;

	/**
	 * contains the parameters calculated as a function of (c,d,nk,tk)
	 */
	double[] pk;

	/**
	 * contains the accumulated pk for several runs of Gibbs sampling
	 */
	double[] pkAccumulated;
	/**
	 * contains the number of pks that have been accumulated in the pkSum
	 */
	int nPkAccumulated;

	Concentration c;
	
	int varNumberForBanchingChildren;
	
	int windowForSamplingTk=10;
	double[]probabilityForWindowTk=new double[2*windowForSamplingTk+1];

	ProbabilityNode parent;
	ProbabilityNode[] children;
	ProbabilityTree tree;

	public ProbabilityNode(ProbabilityTree probabilityTree, int varNumberForBanchingChildren) {
		this(probabilityTree, varNumberForBanchingChildren, false);
	}

	public ProbabilityNode(ProbabilityTree probabilityTree, int varNumberForBanchingChildren,
			boolean createFullTree) {
		this.tree = probabilityTree;
		int nValuesY = tree.nValuesConditionedVariable;
		int[] nValuesXs = tree.nValuesContioningVariables;
		nk = new int[nValuesY];
		tk = new int[nValuesY];
		parent = null;
		this.varNumberForBanchingChildren = varNumberForBanchingChildren;
		if (createFullTree && varNumberForBanchingChildren + 1 <= tree.getNXs()) {
			children = new ProbabilityNode[nValuesXs[varNumberForBanchingChildren]];
			for (int i = 0; i < children.length; i++) {
				children[i] = new ProbabilityNode(this, varNumberForBanchingChildren + 1,
						createFullTree);
			}
		}

	}

	public ProbabilityNode(ProbabilityNode parent, int varNumberForBanchingChildren) {
		this(parent, varNumberForBanchingChildren, false);
	}

	public ProbabilityNode(ProbabilityNode parent, int varNumberForBanchingChildren,
			boolean createFullTree) {
		this.parent = parent;
		this.tree = parent.tree;
		int nValuesY = tree.nValuesConditionedVariable;
		int[] nValuesXs = tree.nValuesContioningVariables;
		nk = new int[nValuesY];
		tk = new int[nValuesY];
		this.varNumberForBanchingChildren = varNumberForBanchingChildren;

		if (createFullTree && varNumberForBanchingChildren + 1 <= tree.getNXs()) {
			children = new ProbabilityNode[nValuesXs[varNumberForBanchingChildren]];

			for (int i = 0; i < children.length; i++) {
				children[i] = new ProbabilityNode(this, varNumberForBanchingChildren + 1,
						createFullTree);
			}
		}

	}

	/**
	 * Add observation to the leaves in the associated subtree
	 * 
	 * @param values
	 *            the set of values for the observation; the first is the target (y)
	 * @param xIndexToUse
	 *            the index of the covariate to use in values; first covariate is at index 1
	 */
	public void addObservation(int[] values, int xIndexToUse) {
		if (isLeaf()) {
			// if at the leaf, then count the data
			nk[values[0]]++;
			marginal_nk++;
		} else {
			// else just call recursively
			if (children == null) {
				// -1 because values here has y as well
				children = new ProbabilityNode[tree.nValuesContioningVariables[xIndexToUse - 1]];
			}
			if (children[values[xIndexToUse]] == null) {
				children[values[xIndexToUse]] = new ProbabilityNode(this, xIndexToUse);
			}
			children[values[xIndexToUse]].addObservation(values, xIndexToUse + 1);
		}
	}

	public boolean isLeaf() {
		return varNumberForBanchingChildren >= tree.getNXs();
	}

	/**
	 * This function should be called after have seen all the data. - At the leaves, nk already
	 * exist so we just sum create tk respecting the constraints. - At the intermediate nodes, we
	 * first add the tks from the children to form the current nk and then create the tk
	 */
	public void prepareForSamplingTk() {
		if (children != null) {
			// first we launch the recursive call to make the nk and
			// tk correct for the children
			for (int c = 0; children != null && c < children.length; c++) {
				if (children[c] != null) {
					children[c].prepareForSamplingTk();
				}
			}

			/*
			 * Now the tks (and nks) from the children are correctly set. If a leaf, nk is already
			 * set, so we only have to do it if not a leaf (by summing the tks from the children).
			 */
			for (int c = 0; children != null && c < children.length; c++) {
				if (children[c] != null) {
					for (int k = 0; k < nk.length; k++) {
						int tkChild = children[c].tk[k];
						nk[k] += tkChild;
						marginal_nk += tkChild;
					}
				}
			}
		}
		// Now nks are set for current node; let's initialize the tks

		if (parent == null) {
			for (int k = 0; k < nk.length; k++) {
				tk[k] = (nk[k] == 0) ? 0 : 1;
				marginal_tk += tk[k];
			}
		} else {
			double concentration = getConcentration();
			for (int k = 0; k < nk.length; k++) {
				if (nk[k] <= 1) {
					tk[k] = nk[k];
				} else {
					tk[k] = (int) Math.max(
							1,
							Math.floor(concentration
									* (tree.digamma(concentration + nk[k]) - tree.digamma(concentration))));
					
				}
				marginal_tk += tk[k];
			}
		}
	}

	/**
	 * Computes the log-likelihood function for the tree under the current node (included)
	 * 
	 * @return
	 */
	public double logScoreSubTree() {
		double res = 0.0;
		double concentration = getConcentration();
		res += MathUtils.logPochhammerSymbol(concentration, 0.0, marginal_tk);
//		System.out.println(res+" c="+concentration+" T="+marginal_tk);
//		res -= MathUtils.logGammaRatio(concentration, marginal_nk);
//		System.out.println(res+" c="+concentration+" N="+marginal_nk);
		res -= c.logGammaRatioForConcentration(marginal_nk);

		// Now nks are set for current node; let's initialize the tks
		for (int k = 0; k < nk.length; k++) {
			res += tree.logStirling(0.0, nk[k], tk[k]);
			if(res==Double.NEGATIVE_INFINITY){
				throw new RuntimeException("log stirling return neg infty");
			}
		}
		// we score all of the children (doesn't matter if done first or after)
		for (int c = 0; children != null && c < children.length; c++) {
			if (children[c] != null) {
				res += children[c].logScoreSubTree();
			}
		}

		return res;

	}

	/**
	 * This function computes the values of the smoothed conditional probabilities as a function of
	 * (nk,tk,c,d) and of the parent probability. <br/>
	 * p_k = ( ( nk - tk*d ) / (N + c) ) ) + ( ( c + T*d ) / (N + c) ) ) * p^{parent}_k
	 * 
	 * @see <a href=
	 *      "http://topicmodels.org/2014/11/13/training-a-pitman-yor-process-tree-with-observed-data-at-the-leaves-part-2/">
	 *      topicmodels.org</a> (Equation 1)
	 */
	public void computeProbabilities() {
		if (pk == null) {
			pk = new double[nk.length];
		}
		double concentration = getConcentration();
		double sum = 0.0;
		for (int k = 0; k < pk.length; k++) {
			double parentProb=(parent!=null)?parent.pk[k]:1.0/pk.length;//uniform parent if root node
			pk[k] = (nk[k]) / (marginal_nk + concentration) + (concentration )
						* parentProb/ (marginal_nk + concentration);
			sum += pk[k];
		}
		for (int k = 0; k < pk.length; k++) {
			pk[k] /= sum;
		}
		if (children != null) {
			for (int c = 0; c < children.length; c++) {
				if (children[c] != null) {
					children[c].computeProbabilities();
				}
			}
		}
	}

	/**
	 * This method accumulates the pks so that the final result is averaged over several successive
	 * iterations of the Gibbs sampling process
	 */
	protected void recordAndAverageProbabilities() {

		if (pkAccumulated == null) {
			pkAccumulated = new double[nk.length];
			nPkAccumulated = 0;
		}
		double sum = 0.0;
		for (int k = 0; k < pkAccumulated.length; k++) {
			pkAccumulated[k] = pkAccumulated[k] * nPkAccumulated + pk[k];
			pkAccumulated[k] /= (nPkAccumulated+1);
			if (pkAccumulated[k] == 0)
				pkAccumulated[k] = 1e-75;
			sum += pkAccumulated[k];
		}
		for (int k = 0; k < pkAccumulated.length; k++) {
			pkAccumulated[k] /= sum;
		}
		nPkAccumulated++;

		if (children != null) {
			for (int c = 0; c < children.length; c++) {
				if (children[c] != null) {
					children[c].recordAndAverageProbabilities();
				}
			}
		}
	}

	public String printNksRecursively(String prefix) {
		String res = "";

		// root node
		res += prefix + ":nk=" + Arrays.toString(nk) + "\n";
		if (children != null) {
			for (int c = 0; c < children.length; c++) {
				res += children[c].printNksRecursively(prefix + " -> " + c);
			}
		}
		return res;
	}

	public String printTksRecursively(String prefix) {
		String res = "";

		// root node
		res += prefix + ":tk=" + Arrays.toString(tk) + "\n";
		if (children != null) {
			for (int c = 0; c < children.length; c++) {
				res += children[c].printTksRecursively(prefix + " -> " + c);
			}
		}
		return res;
	}

	public String printTksAndNksRecursively(String prefix) {
		String res = "";

		// root node
		res += prefix + ":tk=" + Arrays.toString(tk) + " :nk=" + Arrays.toString(nk) + " :c=" + this.c
				+ "\n";
		if (children != null) {
			for (int c = 0; c < children.length; c++) {
				if (children[c] != null) {
					res += children[c].printTksAndNksRecursively(prefix + " -> " + c);
				}
			}
		}
		return res;
	}

	public String printPksRecursively(String prefix) {
		String res = "";

		res += prefix + ":pk=" + Arrays.toString(pk) + " c=" + this.c + "\n";
		if (children != null) {
			for (int c = 0; c < children.length; c++) {
				if (children[c] != null) {
					res += children[c].printPksRecursively(prefix + " -> " + c);
				}
			}
		}
		return res;
	}

	public String printAccumulatedPksRecursively(String prefix) {
		String res = "";

		// root node
		res += prefix + ":pk=" + Arrays.toString(pkAccumulated) + " c=" + this.c + "\n";
		if (children != null) {
			for (int c = 0; c < children.length; c++) {
				if (children[c] != null) {
					res += children[c].printAccumulatedPksRecursively(prefix + " -> " + c);
				}
			}
		}
		return res;
	}

	public ArrayList<ProbabilityNode> getAllNodesAtRelativeDepth(int depth) {
		ArrayList<ProbabilityNode> res = new ArrayList<>();
		if (depth == 0) {
			res.add(this);
		} else {
			for (int c = 0; c < children.length; c++) {
				if (children[c] != null) {
					res.addAll(children[c].getAllNodesAtRelativeDepth(depth - 1));
				}
			}
		}
		return res;
	}

	/**
	 * add a value to the current one of the tk[k]
	 * 
	 * @param k the index of the value to change in tk
	 * @param the value to set tk 
	 * @return the non-normalized posterior probability at this point; negative-infinity if value not authorized
	 */
	protected double setTk(int k, int val) {
		//how much to increment (or decrement tk by)
		int incVal = val-tk[k];
		if (incVal < 0) {
			// if decrement, then have to check that valid for the
			// parent
			if (parent != null && incVal<0 && (parent.nk[k] + incVal) < parent.tk[k]) {
				// not valid; skip
				return Double.NEGATIVE_INFINITY;
			}
		}
		
		tk[k] += incVal;
		marginal_tk += incVal;
		
		double concentration = getConcentration();
		double res = 0.0;

		//partial score difference for current node
		res += tree.logStirling(0.0, nk[k], tk[k]);
		res += MathUtils.logPochhammerSymbol(concentration, 0.0, marginal_tk);
		
		// partial score difference for parent
		if (parent != null) {
			parent.nk[k] += incVal;
			parent.marginal_nk += incVal;
			res += tree.logStirling(0.0, parent.nk[k],  parent.tk[k]);
//			res -= MathUtils.logGammaRatio(parent.getConcentration(), parent.marginal_nk);
			res -= parent.c.logGammaRatioForConcentration(parent.marginal_nk);
		}
		
		return res;
//		return tree.logScoreTree();
			
	}

	public void sampleTks() {
//		System.out.println("tk="+Arrays.toString(tk));
		if (parent == null) {
			/*
			 * case for root: no sampling, t is either 0 or 1
			 */
//			System.out.println("sampling root");
			for (int k = 0; k < tk.length; k++) {
				// Wray says this is GEM
				int t = (nk[k] == 0) ? 0 : 1;
				setTk(k, t);
			}
		} else {
			for (int k = 0; k < tk.length; k++) {
//				double startingScore = tree.logScoreTree();
//				String treeBefore = tree.printTksAndNks();
//				 System.out.println("starting score = "+tree.logScoreTree()+" with tk="+Arrays.toString(tk));
				// System.out.println("previous tk["+k+"]="+oldTk);
				if (nk[k] <= 1) {
					/*
					 * can't sample anything, constraints say that tk[k] must be nk[k] just have to
					 * check that tk[k] is different or not to the previous time (in case nk[k] has
					 * just changed)
					 */
					setTk(k, nk[k]);
//					System.out.println("can't sample; should be set to " + nk[k] + " tk=" + tk[k]);
				} else {
					// sample case
					//starting point
					int oldTk = tk[k];
					int valTk = tk[k] - windowForSamplingTk;
					// maxTk can't be larger than nk[k]
					int maxTk = Math.min(tk[k] + windowForSamplingTk, nk[k]);
					int index = 0;
					while(valTk<1){//move to first allowed position
						probabilityForWindowTk[index]=Double.NEGATIVE_INFINITY;
						valTk++;
						index++;
					}
					boolean hasOneValue = false;
					while(valTk <= maxTk){//now fill posterior
						double logProbDifference =setTk(k, valTk);
						probabilityForWindowTk[index]=logProbDifference;
						hasOneValue=(hasOneValue||probabilityForWindowTk[index]!=Double.NEGATIVE_INFINITY);
						index++;
						valTk++;
					}
					if(!hasOneValue){
						setTk(k, oldTk);
						continue;
					}
					for(;index<probabilityForWindowTk.length;index++){
						//finish filling with neg infty
						probabilityForWindowTk[index]=Double.NEGATIVE_INFINITY;
					}
					
					//now lognormalize probabilityForWindowTk and exponentiate
//					System.out.println(Arrays.toString(probabilityForWindowTk));
					MathUtils.normalizeInLogDomain(probabilityForWindowTk);
//					System.out.println(Arrays.toString(probabilityForWindowTk));
					MathUtils.exp(probabilityForWindowTk);
					
					for (int j = 0; j < probabilityForWindowTk.length; j++) {
						if(Double.isNaN(probabilityForWindowTk[j])){
							System.err.println("problem "+Arrays.toString(probabilityForWindowTk));
						}
					}
//					System.out.println(Arrays.toString(probabilityForWindowTk));
					
//					System.out.println(Arrays.toString(probabilityForWindowTk));
					//now sampling tk according to probability vector
					int chosenIndex = MathUtils.sampleFromMultinomial(tree.rng, probabilityForWindowTk);
					
					// assign chosen tk
					int valueTkChosen = oldTk-windowForSamplingTk+chosenIndex;
//					System.out.println("sampled element "+chosenIndex+" ie tk="+valueTkChosen+" with p="+probabilityForWindowTk[chosenIndex]);
					setTk(k, valueTkChosen);
//					double finishingScore = tree.logScoreTree();
//					if(finishingScore<startingScore){
//						System.out.println("starting score = "+startingScore+" with tk="+Arrays.toString(tk));
//						System.out.println("finishing score = "+finishingScore+" with tk="+Arrays.toString(tk));
//						System.out.println(Arrays.toString(probabilityForWindowTk));
//						System.out.println("chosen val="+valueTkChosen+" at index "+chosenIndex);
//						System.out.println("before\n"+treeBefore);
//						System.out.println("after\n"+tree.printTksAndNks());
//					}
						
				}
			}

		}

	}

	

	
	public double getConcentration(){
		if(c==null){
			return 2.0;
		}else{
			return c.c;
		}
	}

	public boolean checkNkSumTks() {
		if (children != null) {
			for (int k = 0; k < nk.length; k++) {
				// System.out.println("tk["+k+"]="+tk[k]+"\tnk["+k+"]="+nk[k]);
				int sumTkChildren = 0;
				for (int c = 0; c < children.length; c++) {
					if (children[c] != null) {
						sumTkChildren += children[c].tk[k];
					}
				}

				if (sumTkChildren != nk[k]) {
					System.out.println(sumTkChildren + " != " + nk[k]);
					return false;
				}
			}
			for (int c = 0; c < children.length; c++) {
				if (children[c] != null && !children[c].checkNkSumTks()) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * To be called to free the memory of the local caches
	 */
	public void clearMemoryAfterSmoothing() {
		pk = null;
		tk = null;
		probabilityForWindowTk = null;
		if (children != null) {
			for (int c = 0; c < children.length; c++) {
				if (children[c] != null) {
					children[c].clearMemoryAfterSmoothing();
				}
			}
		}
	}

	public void createSyntheticSubTree(RandomDataGenerator rdg) {
		if (pk == null) {
			pk = new double[tk.length];
		}
		// sample some concentration
		double parentConcentration = (parent==null)?2.0:parent.getConcentration();
		double[] parentProbs;
		if (parent == null) {
			// root case
			parentProbs = new double[pk.length];
			Arrays.fill(parentProbs, 1.0 / tree.nValuesConditionedVariable);
		} else {
			// normal case
			parentProbs = parent.pk;
		}

		// now sampling pk from parentPk and concentration
		double sumPk = 0.0;
		for (int k = 0; k < pk.length; k++) {
			pk[k] = rdg.nextGamma(Math.max(parentProbs[k] * parentConcentration,1e-75), 1.0);
			sumPk += pk[k];
		}
		for (int k = 0; k < pk.length; k++) {
			pk[k] /= sumPk;
		}
		
		if(children!=null){
			//choose concentration for this (to be used by children
			c = new Concentration();
			for (int c = 0; c < children.length; c++) {
				if (children[c] != null) {
					children[c].c = this.c;
					this.c.addNode(children[c]);
					children[c].createSyntheticSubTree(rdg);
				}
			}
			c.sample(rdg.getRandomGenerator());
		}
		

	}
	
	public int getNOutcomesTarget(){
		return tree.nValuesConditionedVariable;
	}

	public void computeProbabilitiesInLogScale() {

		if (pk == null) {
			pk = new double[nk.length];
		}
		double concentration = getConcentration();
		double sum = 0.0;
		for (int k = 0; k < pk.length; k++) {
//			double parentProb=(parent!=null)?parent.pk[k]:1.0/pk.length;//uniform parent if root node

			double parentProb = (this.parent != null) ? FastMath.exp(this.parent.pk[k]) : 1.0 / pk.length;

			pk[k] = (nk[k]) / (marginal_nk + concentration)
					+ (concentration) * parentProb / (marginal_nk + concentration);
			sum += pk[k];
		}

		// normalize
		for (int k = 0; k < pk.length; k++) {
			pk[k] /= sum;
		}

		// convert into log space
		for (int k = 0; k < pk.length; k++) {
			pk[k] = FastMath.log(pk[k]);
		}

		if (children != null) {
			for (int c = 0; c < children.length; c++) {
				if (children[c] != null) {
					children[c].computeProbabilitiesInLogScale();
				}
			}
		}
	}

	/**
	 * This method accumulates the pks so that the final result is averaged over
	 * several successive iterations of the Gibbs sampling process in log space to
	 * avoid underflow
	 */
	protected void recordProbabilitiesInLogScale() {
		// in this method, pkAccumulated stores the log sum
		if (this.pkAccumulated == null) {
			pkAccumulated = new double[nk.length];
			nPkAccumulated = 0;
		}

		for (int k = 0; k < pkAccumulated.length; k++) {

			if (nPkAccumulated == 0) {
				pkAccumulated[k] = this.pk[k];
			} else {
				pkAccumulated[k] = MathUtils.logadd(pkAccumulated[k], pk[k]);
			}
		}
		nPkAccumulated ++;

		if (children != null) {
			for (int c = 0; c < children.length; c++) {
				if (children[c] != null) {
					children[c].recordProbabilitiesInLogScale();
				}
			}
		}
	}
	
	public void averagePkAccumulatedProbabilitiesInLogSpace() {
		double sum = 0;
		for (int k = 0; k < this.pkAccumulated.length; k++) {
			pkAccumulated[k] = FastMath.exp(pkAccumulated[k]);
			sum += pkAccumulated[k];
		}

		for (int k = 0; k < this.pkAccumulated.length; k++) {
			pkAccumulated[k] /= sum;
		}

		if (children != null) {
			for (int c = 0; c < children.length; c++) {
				if (children[c] != null) {
					children[c].averagePkAccumulatedProbabilitiesInLogSpace();
				}
			}
		}
	}
}

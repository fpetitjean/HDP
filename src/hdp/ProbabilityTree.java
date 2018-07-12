package hdp;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.special.Gamma;
import org.apache.commons.math3.util.FastMath;

import tools.LogStirlingCache;
import tools.MathUtils;

public class ProbabilityTree {
	
	private int nIterGibbs;
	private int nBurnIn;
	private int frequencySamplingC;
	
	LogStirlingCache lgCache;
	protected RandomGenerator rng = new MersenneTwister(3071980);
	ProbabilityNode root;
	ArrayList<Concentration>concentrationsToSample;
	
	protected TyingStrategy concentrationTyingStrategy = TyingStrategy.LEVEL;

	int nValuesConditionedVariable;

	int[] nValuesContioningVariables;
	protected int nDatapoints;

	public ProbabilityTree(int nValuesConditionedVariable, int[] nValuesConditioningVariables, boolean createFullTree) {
		init(nValuesConditionedVariable,nValuesConditioningVariables,createFullTree,5000,TyingStrategy.LEVEL,5);
	}

	public ProbabilityTree(int nValuesConditionedVariable, int[] nValuesConditioningVariables, int m_Iterations, int m_Tying) {
		setConcentrationTyingStrategy(m_Tying);
		init(nValuesConditionedVariable, nValuesConditioningVariables, false, m_Iterations, this.concentrationTyingStrategy, 5);
	}
	
	public ProbabilityTree(int nValuesConditionedVariable, int[] nValuesConditioningVariables, boolean createFullTree,int m_Iterations, TyingStrategy m_Tying,int frequencySamplingC) {
		init(nValuesConditionedVariable,nValuesConditioningVariables,createFullTree,m_Iterations,m_Tying,frequencySamplingC);
	}
	
	protected void init(int nValuesConditionedVariable, int[] nValuesConditioningVariables, boolean createFullTree,int m_Iterations, TyingStrategy m_Tying,int frequencySamplingC){
		this.nValuesConditionedVariable = nValuesConditionedVariable;
		this.nValuesContioningVariables = nValuesConditioningVariables;
		this.nIterGibbs = m_Iterations;
		setConcentrationTyingStrategy(m_Tying);
		this.nBurnIn = Math.min(1000, nIterGibbs/10);
		this.frequencySamplingC = frequencySamplingC;
		root = new ProbabilityNode(this, 0, createFullTree);
	}

	public int getNXs() {
		return nValuesContioningVariables.length;
	}

	public double getRisingFact(double x, int n) {
		return FastMath.exp(MathUtils.logGammaRatio(x, n));
	}

	public double digamma(double d) {
		return Gamma.digamma(d);
	}

	protected double logScoreTree() {
		return root.logScoreSubTree();
	}

	/**
	 * Runs the Gibbs sampling for the whole tree with given discount and
	 * concentration parameters
	 * 
	 * @return the log likelihood of the optimized tree
	 */
	public double smooth() {
		//Creating and tying concentrations
		concentrationsToSample = new ArrayList<>();
		switch (concentrationTyingStrategy) {
		case NONE:
			for (int depth = getNXs(); depth > 0; depth--) {
				//tying all children of a node
				ArrayList<ProbabilityNode> nodes = getAllNodesAtDepth(depth);
				for (ProbabilityNode node : nodes) {
					Concentration c = new Concentration();
					concentrationsToSample.add(c);
					node.c = c;
					c.addNode(node);
				}
				
			}
			break;
		case SAME_PARENT:
			for (int depth = getNXs()-1; depth >= 0; depth--) {
				//tying all children of a node
				ArrayList<ProbabilityNode> nodes = getAllNodesAtDepth(depth);
				// System.out.println("depth="+depth+"\t"+nodes.size()+"
				// nodes");
				for (ProbabilityNode parent : nodes) {
					//creating concentration
					Concentration c = new Concentration();
					concentrationsToSample.add(c);
					for (int child = 0; child < parent.children.length; child++) {
						if(parent.children[child]!=null){
							parent.children[child].c = c;
							c.addNode(parent.children[child]);
						}
					}
				}
			}
			break;
		case LEVEL:
			for (int depth = getNXs(); depth > 0; depth--) {
				//tying all children of a node
				ArrayList<ProbabilityNode> nodes = getAllNodesAtDepth(depth);
				Concentration c = new Concentration();
				concentrationsToSample.add(c);
				for (ProbabilityNode node : nodes) {
					node.c = c;
					c.addNode(node);
				}
				
			}
			break;
		case SINGLE:
			Concentration c = new Concentration();
			concentrationsToSample.add(c);
			for (int depth = getNXs(); depth > 0; depth--) {
				//tying all children of a node
				ArrayList<ProbabilityNode> nodes = getAllNodesAtDepth(depth);
				for (ProbabilityNode node : nodes) {
					node.c = c;
					c.addNode(node);
				}
				
			}
			break;
		default:
			break;
		}
		
		//setting concentration for root
		root.c = new Concentration();
		
		
		root.prepareForSamplingTk();
		
		// Gibbs sampling of the tks, c and d
		for (int iter = 0; iter < nIterGibbs; iter++) {
//			System.out.println("Iter=" + iter + " score=" + logScoreTree());

			// sample tks once
			for (int depth = getNXs(); depth >= 0; depth--) {
				ArrayList<ProbabilityNode> nodes = getAllNodesAtDepth(depth);
				// System.out.println("depth="+depth+"\t"+nodes.size()+"
				// nodes");
				for (ProbabilityNode node : nodes) {
//					 System.out.println("Starting score before sampling tk="+logScoreTree());
					node.sampleTks();
//					 System.out.println("Score after sampling tk="+logScoreTree());
				}
			}

			
			// sample c
			if ((iter + frequencySamplingC / 2) % frequencySamplingC == 0) {
				for (Concentration c:concentrationsToSample) {
					c.sample(rng);
					
				}
			}

			if (iter >= nBurnIn ) {
				this.recordAndAverageProbabilities();
			}
			
		}
		double score = logScoreTree();
		return score;
	}

	private void recordAndAverageProbabilities() {
		root.computeProbabilities();
		root.recordAndAverageProbabilities();
	}

	public void createSyntheticTree() {
		RandomDataGenerator rdg = new RandomDataGenerator(rng);
		root.createSyntheticSubTree(rdg);
		
	}

	private ArrayList<ProbabilityNode> getAllNodesAtDepth(int depth) {
		return root.getAllNodesAtRelativeDepth(depth);
	}

	/**
	 * Add the observational data for the leaves
	 * 
	 * @param data
	 *                a dataset; first value is the value for the
	 *                conditioned variable; other values are for the
	 *                conditioning variables (in the order given in the
	 *                constructor)
	 */
	public void addDataset(int[][] data) {
		for (int[] datapoint : data) {
			root.addObservation(datapoint, 1);
		}
		lgCache = new LogStirlingCache(0.0, data.length);
		nDatapoints = data.length;
		this.smooth();
	}

	public void addObservation(int[] datapoint) {
		root.addObservation(datapoint, 1);
		nDatapoints++;
	}

	public void smoothTree() {
		if (lgCache == null)
			lgCache = new LogStirlingCache(0.0, nDatapoints);
		this.smooth();
	}

	public void setLogStirlingCache(LogStirlingCache cache) {
		this.lgCache = cache;
	}

	/**
	 * @param sample
	 * @return
	 */
	public double[] query(int[] sample) {
		ProbabilityNode node = root;
		for (int n = 0; n < sample.length; n++) {
			if (node.children[sample[n]] != null) {
				node = node.children[sample[n]];
			} else {
				break;
			}
		}
		return node.pkAccumulated;
	}

	protected double logStirling(double a, int n, int m) {
		if (a != lgCache.getA()) {
			lgCache = new LogStirlingCache(a, nDatapoints);
		}
		double res = lgCache.query(a, n, m);
		return res;

	}

	public String printNks() {
		return root.printNksRecursively("root");

	}

	public String printTks() {
		return root.printTksRecursively("root");

	}

	public String printTksAndNks() {
		return root.printTksAndNksRecursively("root");

	}

	public String printPks() {
		return root.printPksRecursively("root");
	}

	public String printFinalPks() {
		return root.printAccumulatedPksRecursively("root");
	}

	public static void main(String... args) {
		
		System.out.println(FastMath.exp(FastMath.log(Double.NEGATIVE_INFINITY)));
		int[] arities = new int[] { 3, 2, 2 };

		int m_Iterations = 5000;
		int m_Tying = 2;
		
		Random r = new Random(1);
		ProbabilityTree tree = new ProbabilityTree(2, arities, m_Iterations, m_Tying);
		int nDataPoints = 10000;
		int[][] data = new int[nDataPoints][];
		for (int i = 0; i < data.length; i++) {
			data[i] = new int[4];
			// sample xs uniformly
			for (int j = 1; j < data[i].length; j++) {
				data[i][j] = (int) (r.nextDouble() / (1.0 / arities[j - 1]));
			}
			if (r.nextDouble() < .1) {// noise
				data[i][0] = r.nextInt(2);
			} else {
				data[i][0] = (data[i][1] == 1 || (data[i][1] == 2 && data[i][2] == 0 && data[i][3] == 1)) ? 1 : 0;

			}
		}

		tree.addDataset(data);
		// System.out.println(tree.printTksAndNks());
		// System.out.println(tree.root.checkNkSumTks());
		// System.out.println("init likelihood
		// tree="+tree.logScoreTree());

		// System.out.println(tree.printNks()+"\n");
		// System.out.println(tree.printTks()+"\n");
		// System.out.println("optimized likelihood tree=" +
		// tree.logScoreTree());
		// System.out.println(tree.printTksAndNks() + "\n");

		double[] p = tree.query(new int[] { 1, 0, 1 });

		// System.out.println(tree.printPks());
		System.out.println(tree.printFinalPks());
		System.out.println(Arrays.toString(p));

	}

	public int[][] sampleDataset(int nDataPoints) throws NoSuchAlgorithmException {
		int[][] data = new int[nDataPoints][nValuesContioningVariables.length + 1];
		SecureRandom srg = SecureRandom.getInstance("SHA1PRNG");

		for (int i = 0; i < nDataPoints; i++) {

			// choose xs
			ProbabilityNode node = root;
			for (int x = 0; x < nValuesContioningVariables.length; x++) {
				// choose value of x
				int val = srg.nextInt(nValuesContioningVariables[x]);
				data[i][x + 1] = val;
				node = node.children[val];
			}

			// now choosing y given values of xs
			double rand = srg.nextDouble();
			int chosenValue = 0;
			double sumProba = node.pk[chosenValue];
			while (rand > sumProba) {
				chosenValue++;
				assert (chosenValue < node.pk.length);
				sumProba += node.pk[chosenValue];
			}
			data[i][0] = chosenValue;
		}

		return data;
	}
	
	public void setConcentrationTyingStrategy(TyingStrategy tyingStrategy){
		this.concentrationTyingStrategy = tyingStrategy;
	}

	public void setConcentrationTyingStrategy(int tyingStrategy) {
		if (tyingStrategy == 0) {
			this.concentrationTyingStrategy = TyingStrategy.NONE;
		} else if (tyingStrategy == 1) {
			this.concentrationTyingStrategy = TyingStrategy.SAME_PARENT;
		} else if (tyingStrategy == 2) {
			this.concentrationTyingStrategy = TyingStrategy.LEVEL;
		} else if (tyingStrategy == 3) {
			this.concentrationTyingStrategy = TyingStrategy.SINGLE;
		} 
	}
	
}

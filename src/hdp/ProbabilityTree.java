package hdp;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.special.Gamma;
import org.apache.commons.math3.util.FastMath;

import hdp.logStirling.LogStirlingFactory;
import hdp.logStirling.LogStirlingGenerator;
import hdp.logStirling.LogStirlingGenerator.CacheExtensionException;
import hdp.tools.MathUtils;

public class ProbabilityTree {

	private int nIterGibbs;
	private int nBurnIn;
	private int frequencySamplingC;

	LogStirlingGenerator lgCache;
	protected RandomGenerator rng = new MersenneTwister(3071980);
	ProbabilityNode root;
	ArrayList<Concentration> concentrationsToSample;
	ArrayList<HashMap<String, Integer>> valueToIndex;
	ArrayList<ArrayList<String>> indexToValue;

	protected TyingStrategy concentrationTyingStrategy = TyingStrategy.LEVEL;

	int nValuesConditionedVariable;

	int[] nValuesContioningVariables;
	protected int nDatapoints;
	boolean createFullTree = false;

	public ProbabilityTree() {
		init(false, 5000, TyingStrategy.LEVEL, 5);
	}
	public ProbabilityTree(boolean createFullTree) {
		init(createFullTree, 5000, TyingStrategy.LEVEL, 5);
	}

	public ProbabilityTree(int m_Iterations, int m_Tying) {
		setConcentrationTyingStrategy(m_Tying);
		init(false, m_Iterations, this.concentrationTyingStrategy, 5);
	}

	public ProbabilityTree(boolean createFullTree, int m_Iterations, TyingStrategy m_Tying, int frequencySamplingC) {
		init(createFullTree, m_Iterations, m_Tying, frequencySamplingC);
	}

	protected void init(boolean createFullTree, int m_Iterations, TyingStrategy m_Tying, int frequencySamplingC) {
		this.nIterGibbs = m_Iterations;
		setConcentrationTyingStrategy(m_Tying);
		this.nBurnIn = Math.min(1000, nIterGibbs / 10);
		this.frequencySamplingC = frequencySamplingC;
		this.createFullTree = false;
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
		// Creating and tying concentrations
		concentrationsToSample = new ArrayList<>();
		switch (concentrationTyingStrategy) {
		case NONE:
			for (int depth = getNXs(); depth > 0; depth--) {
				// tying all children of a node
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
			for (int depth = getNXs() - 1; depth >= 0; depth--) {
				// tying all children of a node
				ArrayList<ProbabilityNode> nodes = getAllNodesAtDepth(depth);
				// System.out.println("depth="+depth+"\t"+nodes.size()+"
				// nodes");
				for (ProbabilityNode parent : nodes) {
					// creating concentration
					Concentration c = new Concentration();
					concentrationsToSample.add(c);
					for (int child = 0; child < parent.children.length; child++) {
						if (parent.children[child] != null) {
							parent.children[child].c = c;
							c.addNode(parent.children[child]);
						}
					}
				}
			}
			break;
		case LEVEL:
			for (int depth = getNXs(); depth >= 0; depth--) {
				// tying all children of a node
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
				// tying all children of a node
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

		// setting concentration for root
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
				for (Concentration c : concentrationsToSample) {
					c.sample(rng);

				}
			}

			if (iter >= nBurnIn) {
				this.recordProbabilities();
			}

		}

		root.averageAccumulatedProbabilities();

		double score = logScoreTree();
		return score;
	}

	private ArrayList<ProbabilityNode> getAllNodesAtDepth(int depth) {
		return root.getAllNodesAtRelativeDepth(depth);
	}

	/**
	 * Add the observational data for the leaves Data is stored in a integer format
	 * where each number represents a categorical value from 0 to (nValues - 1)
	 * 
	 * @param data a dataset; first value is the value for the conditioned variable;
	 *             other values are for the conditioning variables (in the order
	 *             given in the constructor)
	 */
	public void addDataset(int[][] data) {
		if (data == null || data.length == 0) {
			throw new RuntimeException("Data is empty");
		}
		int nVariables = data[0].length;
		int nConditioningVariables = nVariables-1;
		int maxValueConditioned = 0;
		nValuesContioningVariables = new int[nConditioningVariables];
		
		for (int i = 0; i < data.length; i++) {
			if(data[i][0]>maxValueConditioned) {
				maxValueConditioned = data[i][0];
			}
			for (int j = 1; j < data[i].length; j++) {
				if(data[i][j]>nValuesContioningVariables[j-1]) {
					nValuesContioningVariables[j-1]=data[i][j];
				}
			}
		}
		nValuesConditionedVariable = maxValueConditioned+1;//indexing from 0
		for (int j = 0; j < nValuesContioningVariables.length; j++) {
			nValuesContioningVariables[j]++;
		}
		root = new ProbabilityNode(this, 0, createFullTree);
		
		for (int[] datapoint : data) {
			root.addObservation(datapoint, 1);
		}
		
		try {
			lgCache = LogStirlingFactory.newLogStirlingGenerator(data.length, 0.0);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			System.err.println("Log Stirling Cache Exception " + e.getMessage() );
			System.err.println("Throws as RuntimeException");
			throw new RuntimeException(e);
		}
		
		nDatapoints = data.length;
		this.smooth();
	}

	/**
	 * Add the observational data for the leaves Data is stored in a integer format
	 * where each number represents a categorical value from 0 to (nValues - 1)
	 * 
	 * @param data a dataset; first value is the value for the conditioned variable;
	 *             other values are for the conditioning variables (in the order
	 *             given in the constructor)
	 */
	public void addDataset(String[][] data) {
		if (valueToIndex != null) {
			System.out.println("Warning: using existing map of values to index");
		}
		if (data == null || data.length == 0) {
			throw new RuntimeException("Data is empty");
		}

		int nVariables = data[0].length;
		int nConditioningVariables = nVariables - 1;

		// now creating a mapping from String to integer
		valueToIndex = new ArrayList<>(nVariables);
		indexToValue = new ArrayList<>(nVariables);
		for (int i = 0; i < nVariables; i++) {
			valueToIndex.add(new HashMap<String, Integer>());
			indexToValue.add(new ArrayList<String>());
		}
		
		for (String[] datapoint : data) {
			for (int j = 0; j < datapoint.length; j++) {
				HashMap<String, Integer> map = valueToIndex.get(j);
				String val = datapoint[j];
				if (!map.containsKey(val)) {
					int nValuesForVariable = map.size();
					map.put(val, nValuesForVariable);
					indexToValue.get(j).add(val);
				}
			}
		}
		nValuesConditionedVariable = valueToIndex.get(0).size();
		nValuesContioningVariables = new int[nConditioningVariables];
		for (int j = 0; j < nConditioningVariables; j++) {
			nValuesContioningVariables[j] = valueToIndex.get(j + 1).size();
		}
		root = new ProbabilityNode(this, 0, createFullTree);

		int[] datapointInt = new int[nVariables];
		for (String[] datapoint : data) {
			for (int j = 0; j < datapoint.length; j++) {
				HashMap<String, Integer> map = valueToIndex.get(j);
				datapointInt[j] = map.get(datapoint[j]);
			}
			root.addObservation(datapointInt, 1);
		}
		
		
		try {
			lgCache = LogStirlingFactory.newLogStirlingGenerator(data.length,  0.0);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			System.err.println("Log Stirling Cache Exception " + e.getMessage() );
			System.err.println("Throws as RuntimeException");
			throw new RuntimeException(e);
		}
		
		
		nDatapoints = data.length;
		this.smooth();
	}

	public void smoothTree() {
		
		if (lgCache == null)
			try {
				lgCache = LogStirlingFactory.newLogStirlingGenerator(nDatapoints,  0.0);
			} catch (NoSuchFieldException | IllegalAccessException e) {
				System.err.println("Log Stirling Cache Exception " + e.getMessage() );
				System.err.println("Throws as RuntimeException");
				throw new RuntimeException(e);
			}
		
		this.smooth();
	}

	public void setLogStirlingCache(LogStirlingGenerator cache) {
		if(lgCache != null) {
			try {
				lgCache.close();
			} catch (Exception e) {
				System.err.println("Closing Log Stirling Cache Exception " + e.getMessage() );
				System.err.println("Throws as RuntimeException");
				throw new RuntimeException(e);
			}
		}
		this.lgCache = cache;
	}

	/**
	 * Get the probability estimated by the HDP process
	 * 
	 * @param sample a datapoint (without the target variable)
	 * @return it's probability distribution over the target variable
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

	/**
	 * Get the probability estimated by the HDP process
	 * 
	 * @param sample a datapoint (without the target variable)
	 * @return it's probability distribution over the target variable
	 */
	public double[] query(String... sample) {
		ProbabilityNode node = root;
		for (int j = 0; j < sample.length; j++) {
			//+1 because storing the target as well
			int index = valueToIndex.get(j+1).get(sample[j]);
			if (node.children[index] != null) {
				node = node.children[index];
			} else {
				break;
			}
		}
		return node.pkAccumulated;
	}

	protected double logStirling(double a, int n, int m) throws CacheExtensionException {
		
		if (a != lgCache.discountP) {
			try {
				// Do not forget to close to free resources!
				lgCache.close();
			} catch (Exception e) {
				System.err.println("Closing Log Stirling Cache Exception " + e.getMessage() );
				System.err.println("Throws as RuntimeException");
				throw new RuntimeException(e);
			}
			
			try {
				lgCache = LogStirlingFactory.newLogStirlingGenerator(nDatapoints,  a);
			} catch (NoSuchFieldException | IllegalAccessException e) {
				System.err.println("Log Stirling Cache Exception " + e.getMessage() );
				System.err.println("Throws as RuntimeException");
				throw new RuntimeException(e);
			}
		}
		
		double res = lgCache.query(n, m);
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

	public String printProbabilities() {
		return root.printAccumulatedPksRecursively("root");
	}

	/**
	 * This function samples a dataset from the learned conditional - really this shouldn't be used unless you have a very specific case
	 * @param nDataPoints number of datapoints to generate
	 * @return the generated dataset
	 * @throws NoSuchAlgorithmException
	 */
	public int[][] sampleDataset(int nDataPoints) throws NoSuchAlgorithmException {
		if(nValuesContioningVariables==null) {
			throw new RuntimeException("tree needs to be learnt before sampling a dataset from it");
		}
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

	public void setConcentrationTyingStrategy(TyingStrategy tyingStrategy) {
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
	
	public String[] getValuesTarget() {
		String[]values = new String[nValuesConditionedVariable];
		for (int j = 0; j < values.length; j++) {
			values[j]=indexToValue.get(0).get(j);
		}
		return values;
	}

	private void recordProbabilities() {
		root.computeProbabilities();
		root.addRecordedProbabilities();
	}

}

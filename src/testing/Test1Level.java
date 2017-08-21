package testing;

import hdp.ProbabilityTree;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

public class Test1Level {

	@Test
	public void testLearning1LevelTree() {
		int nDataPoints = 10000;
		int nValuesY = 2;
		int[] arities = new int[] {5 };
		String str = "Junit is working fine";
		Assert.assertEquals("Junit is working fine", str);

		ProbabilityTree tree = new ProbabilityTree(nValuesY, arities,true);
		tree.createSyntheticTree();
		System.out.println("Synth tree created as follows");
		System.out.println(tree.printPks());
		System.out.println("Generating data");
		int[][] sampledData=null;
		try {
			sampledData = tree.sampleDataset(nDataPoints);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		Assert.assertNotNull("Couldn't generate data",sampledData);
		System.out.println("Data generated: 10 first lines look like");
		for (int i = 0; i < Math.min(10, nDataPoints); i++) {
			System.out.println("\t"+Arrays.toString(sampledData[i]));
		}
		
		System.out.println("Learning from sampled data");
		
		int m_Iterations = 50000;
		int m_Tying = 3;
		ProbabilityTree learnedTree = new ProbabilityTree(nValuesY, arities, m_Iterations,m_Tying);
		learnedTree.addDataset(sampledData);
		
		System.out.println("Learnt tree looks like...");
		System.out.println(learnedTree.printTksAndNks());
		System.out.println(learnedTree.printPks());
		System.out.println(learnedTree.printFinalPks());
		
		
	}

}

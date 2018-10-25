package hdp.testing;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

import org.apache.commons.math3.random.RandomDataGenerator;

import hdp.ProbabilityTree;

public class Test1Level {

	public static void main(String...args) throws NoSuchAlgorithmException, NoSuchFieldException, IllegalAccessException {
		int nDataPoints = 10000;
		int nValuesY = 2;
		int[] arities = new int[] {5 };
		
		//generating synthetic data
		SecureRandom srg = SecureRandom.getInstance("SHA1PRNG");
		RandomDataGenerator rdg = new RandomDataGenerator();
		//generate random cpt for p(y|x_1)
		double [][] cptY = new double[arities[0]][nValuesY];
		
		
		for (int x1 = 0; x1 < cptY.length; x1++) {
			double sumPk = 0.0;
			for (int y = 0; y < cptY[x1].length; y++) {
				cptY[x1][y] = rdg.nextGamma(1.0, 1.0);//Dirichlet
				sumPk += cptY[x1][y];
			}
			for (int y = 0; y < cptY[x1].length; y++) {
				cptY[x1][y] /= sumPk;
			}
		}
		
		System.out.println("True p(y|x1)");
		for (int x1 = 0; x1 < cptY.length; x1++) {
			System.out.println("p(y | x1="+x1+") = "+Arrays.toString(cptY[x1]));
		}
		
		int[][]data = new int[nDataPoints][2];//2 for 1 target, 1 x
		for (int i = 0; i < data.length; i++) {
			//choosing x_1 first uniformly
			int x1 = srg.nextInt(arities[0]);
			data[i][1] = x1;
			
			// now choosing y given values of xs
			double rand = srg.nextDouble();
			int chosenValue = 0;
			double sumProba = cptY[x1][chosenValue];
			while (rand > sumProba) {
				chosenValue++;
				sumProba += cptY[x1][chosenValue];
			}
			data[i][0] = chosenValue;
		}
		
		//now data is generated
		ProbabilityTree tree = new ProbabilityTree();
		tree.addDataset(data);
		System.out.println("Learnt tree looks like...");
		System.out.println(tree.printProbabilities());
		
		
	}

}

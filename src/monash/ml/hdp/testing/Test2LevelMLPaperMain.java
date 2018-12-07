package monash.ml.hdp.testing;

import monash.ml.hdp.ProbabilityTree;
import monash.ml.hdp.TyingStrategy;

public class Test2LevelMLPaperMain {
	
	public static void main(String... args) throws NoSuchFieldException, IllegalAccessException {
		
		/*
		 * Data is 
		 *  x_1=0, y=0: 2
  		 *  x_1=1, y=0: 0
  		 *  x_1=0, y=1: 20
  		 *	x_1=1, y=1: 5
		 */
		int [][]data = { //given as (x,y), given that for NB, x will be the parent of y [ p(y|x)=p(y).p(x|y) ]
				{0,0},
				{0,0},
				{0,1},
				{0,1},
				{0,1},
				{0,1},
				{0,1},
				{0,1},
				{0,1},
				{0,1},
				{0,1},
				{0,1},
				{0,1},
				{0,1},
				{0,1},
				{0,1},
				{0,1},
				{0,1},
				{0,1},
				{0,1},
				{0,1},
				{0,1},
				{1,1},
				{1,1},
				{1,1},
				{1,1},
				{1,1}
		}; 
		
		ProbabilityTree tree = new ProbabilityTree(false,50000,TyingStrategy.NONE,1);
		tree.addDataset(data);
		System.out.println(tree.printProbabilities());
		System.out.println(tree.printTksAndNks());
		
		System.out.println("HDP estimates of p(x|y) are:");
		double[] pxgy0 = tree.query(new int[] {0});//p(x|y=0)
		double[] pxgy1 = tree.query(new int[] {1});//p(x|y=1)
		System.out.println("\tp(x=0|y=0)="+pxgy0[0]);
		System.out.println("\tp(x=1|y=0)="+pxgy0[1]);
		System.out.println("\tp(x=0|y=1)="+pxgy1[0]);
		System.out.println("\tp(x=1|y=1)="+pxgy1[1]);
		System.out.println();
		
		double [] mValues = new double[]{0.0,0.05,0.2,1,5,20}; 
		for(double m:mValues) {
			System.out.println("m-estimates (m="+m+") of p(x|y) are:");
			System.out.println("\tp(x=0|y=0)="+((2.0+m/2.0)/(2.0+m)));
			System.out.println("\tp(x=1|y=0)="+((0.0+m/2.0)/(2.0+m)));
			System.out.println("\tp(x=0|y=1)="+((20.0+m/2.0)/(25.0+m)));
			System.out.println("\tp(x=1|y=1)="+((5.0+m/2.0)/(25.0+m)));
		}
		
		
		
	}

}

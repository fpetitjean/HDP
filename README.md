# HDP
Accurate estimation of conditional categorical probability distributions using Hierarchical Dirichlet Processes 

This package offers an accurate parameter estimation technique for Bayesian Network classifiers. It uses a Hierarchical Dirichlet Process to estimate the parameters (using a collapsed Gibbs sampler). Note that the package is built in a generic way such that it can estimate any conditional probability distributions over categorical variables.

More information available at http://www.francois-petitjean.com/Research/

## Underlying research and scientific paper

This code is supporting [our paper in Machine Learning](https://doi.org/10.1007/s10994-018-5718-0) entitled "Accurate parameter estimation for Bayesian Network Classifiers using Hierarchical Dirichlet Processes". 

The paper is also available on [arXiv](https://arxiv.org/pdf/1708.07581).

When using this repository, please cite:

```
@ARTICLE{Petitjean2018-HDP,
  author = {Petitjean, Francois and Buntine, Wray and Webb, Geoffrey I. and Zaidi, Nayyar},
  title = {Accurate parameter estimation for Bayesian Network Classifiers using Hierarchical Dirichlet Processes},
  journal={Machine Learning},
  year={2018},
  volume={107},
  number={8},
  pages={1303--1331},
  year = 2018
  url = {https://doi.org/10.1007/s10994-018-5718-0}
}
```

## Compiling and launching
After cloning the repo, launch the following commands.
```
ant
java -Xmx1g -cp "bin:lib/*:lib/commons-math3-3.6.1/*"  monash.ml.hdp.testing.Test2LevelsExampleHeartAttack
```
This will run a simple example with a small toy dataset and then learning the probability distribution.

### Dependencies
* [Apache Common Maths 3 - 3.6.1](https://commons.apache.org/proper/commons-math/)
* [MLTools](https://github.com/HerrmannM/MLTools)

### Getting a cross-platform jar
Simply entering `ant` creates a jar file that you can execute in most environments in dist/HDP.jar.

### Memory Consumption
You may want to allow the JVM to use more memory if you are working with large models.
Use the `Xmx` flag to increase the JVM memory. For example, `java -Xmx4g`.


## Using it for your own library
The code available at `src/monash/ml/hdp/testing/Test2LevelsExampleHeartAttack.java`
gives a good idea on how to plug your own code with this library. 
Basically, you have to create a dataset in the form of a matrix of integers (`int[N][M+1]`) where `N` is the number of samples, and `M` the number of covariates (or features). `+1` is because the first column gives the values of the target variable you want to get a conditional estimate over. A cell `data[i][j]` represents the value taken by sample `i` for feature `x_{j-1}`. `data[i][0]` represents the value taken for the target variable. Things are coded over integers because this code is for categorical distributions. 

```java
String [][]data = {
    {"yes","heavy","tall"},
    {"no","light","short"},
    ...
    {"yes","heavy","med"}
};

ProbabilityTree hdp = new ProbabilityTree();
//learns p(target|x)
hdp.addDataset(data);
//print the tree
System.out.println(hdp.printProbabilities());
```

## Contributors
Original research and code by:
* [Dr François Petitjean](https://github.com/fpetitjean)
* [Pr. Wray Buntime](https://research.monash.edu/en/persons/wray-buntine)
* [Pr. Geoffrey I. Webb](https://research.monash.edu/en/persons/geoff-webb)
* [Dr Nayyar Zaidi](https://github.com/nayyarzaidi)

Work on the Stirling Number Generator:
 * [Dr Matthieu Herrmann](https://github.com/HerrmannM)

## Support
YourKit is supporting this open-source project with its full-featured Java Profiler.
YourKit is the creator of innovative and intelligent tools for profiling Java and .NET applications. http://www.yourkit.com 

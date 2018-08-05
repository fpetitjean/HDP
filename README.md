# Hierarchical Dirichlet Processes (HDP) for conditional distribution estimation
This package offers an accurate parameter estimation technique for Bayesian Network classifiers. It uses a Hierarchical Dirichlet Process to estimate the parameters (using a collapsed Gibbs sampler). Note that the package is built in a generic way such that it can estimate any conditional probability distributions over categorical variables. 

More information available at http://www.francois-petitjean.com/Research/

# Underlying research and scientific paper

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

# Prerequisites

This package requires Java 8 (to run) and Ant (to compile); other supporting library are providing in the `lib` folder (with associated licenses). 

# Installing

## Compiling HDP estimation
```
git clone https://github.com/fpetitjean/HDP
cd HDP
ant
``` 
## Getting a cross-platform jar
Simply entering `ant jar` will create a jar file that you can execute in most environments in `bin/jar/HDP.jar`. 
Normal execution would then look like
```java -jar -Xmx1g jar/HDP.jar```
Note that `Xmx1g` means that you are allowing the Java Virtual Machine to use 1GB - although this is ok for most datasets, please increase if your dataset is large. Note that the memory footprint increases linearly with the size of the tree, which in turns increases (in general) exponentially with the number of conditioning variables. 

## Running HDP estimation in command line
The `compile` command creates all `.class` files in the `bin/` directory. To execute the demos, simply run:
```
java -Xmx1g -classpath bin:lib/commons-math3-3.6.1.jar testing.Test2LevelsExampleHeartAttack
```
This will run a simple example with a small toy dataset and then learning the probability distribution. 

## Using it for your own library
The code available at `src/testing/testing.Test2LevelsExampleHeartAttack.java` gives a good idea on how to plug your own code with this library. 
Basically, you have to create a dataset in the form of a matrix of integers (`int[N][M+1]`) where `N` is the number of samples, and `M` the number of covariates (or features). `+1` is because the first column gives the values of the target variable you want to get a conditional estimate over. A cell `data[i][j]` represents the value taken by sample `i` for feature `x_{j-1}`. `data[i][0]` represents the value taken for the target variable. Things are coded over integers because this code is for categorical distributions. 
```
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
System.out.println(hdp.printFinalPks());
```

# Support
YourKit is supporting this open-source project with its full-featured Java Profiler.
YourKit is the creator of innovative and intelligent tools for profiling Java and .NET applications. http://www.yourkit.com 


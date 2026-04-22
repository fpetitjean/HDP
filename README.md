# HDP — Hierarchical Dirichlet Processes for Probability Estimation

**Accurate estimation of conditional categorical probability distributions using Hierarchical Dirichlet Processes.**

This Java library estimates conditional probabilities like *p(Y | X1, X2, ..., Xm)* for categorical variables. It was built for parameter estimation in Bayesian Network classifiers, but it is generic: anywhere you need to estimate a conditional probability table from data, this library can help — and it will do a better job than simply counting frequencies.

## The problem: why not just count?

Suppose you want to estimate the probability of a heart attack given a patient's weight and height. You have a small dataset:

| Heart attack? | Weight | Height |
|:---:|:---:|:---:|
| yes | heavy | tall |
| no | light | short |
| no | heavy | med |
| yes | heavy | short |
| ... | ... | ... |

The naive approach is to count: out of all the "heavy & tall" patients, how many had a heart attack? Divide, and you've got your probability.

This works great when you have thousands of examples for every combination of weight and height. But in practice, you often don't. Some combinations might appear only once or twice in your dataset — or not at all. What's the probability of a heart attack for a "light & tall" patient if you've never seen one?

The naive estimator would say "I don't know" (0/0) or give you an absurdly confident answer based on a single example (1/1 = 100%!). This is a real problem in machine learning, especially for classifiers that need probability estimates for every possible combination of feature values.

### The standard fix: smoothing

The traditional fix is called **Laplace smoothing** (or **m-estimation**): you pretend you've seen a few extra fake observations spread evenly across all categories. This pulls extreme estimates toward the middle. For instance, instead of 1/1 = 100%, you might get (1+1)/(1+2) = 67%.

But how many fake observations should you add? Too few, and rare combinations still get wild estimates. Too many, and you wash out real patterns in your data. The answer depends on how much data you have, how many categories there are, and how similar different parts of your data are — and it's different for every node in your probability table.

**This is the problem HDP solves.** Instead of picking a single smoothing strength, the Hierarchical Dirichlet Process *learns* the right amount of smoothing from the data itself, and it does so differently at different levels of the model. Where data is plentiful, it trusts the counts. Where data is scarce, it borrows strength from related, more populated categories.

## The key insight: sharing strength through a hierarchy

Here's the clever part. Consider estimating *p(heart attack | weight, height)*. The HDP builds a tree:

```
                    root: p(heart attack)
                   /                      \
      p(heart attack | heavy)      p(heart attack | light)
        /       |       \            /       |       \
    ...tall   ...med   ...short   ...tall  ...med  ...short
```

- At the **leaves**, you have specific estimates like *p(heart attack | heavy & tall)*.
- At **intermediate nodes**, you have partially conditioned estimates like *p(heart attack | heavy)*.
- At the **root**, you have the overall base rate *p(heart attack)*.

The HDP smooths each level toward its parent. So *p(heart attack | heavy & tall)* is pulled toward *p(heart attack | heavy)*, which is itself pulled toward the base rate *p(heart attack)*. How much pulling happens depends on how much data is available at each level — and this is learned automatically.

**Why is this better than a flat smoother?** Because the hierarchy lets related estimates help each other. If you have very few "heavy & tall" patients but many "heavy" patients overall, the estimate for "heavy & tall" borrows heavily from the "heavy" average. Meanwhile, "heavy & short" might have plenty of data and barely borrow at all. A flat smoother like Laplace can't make this distinction — it smooths everything by the same amount.

## How it works under the hood

### The Dirichlet Process (DP)

A Dirichlet Process is a distribution over distributions. Think of it as a way to say: "I believe the probabilities for this category follow some pattern, and here's how confident I am." It has one key parameter — the **concentration** *c* — which controls how much the estimate is pulled toward a prior (the parent in our tree).

- **Large *c***: the estimate is strongly pulled toward the parent. This is like heavy smoothing.
- **Small *c***: the estimate mostly trusts the local data. This is like light smoothing.

The "hierarchical" part means that each level's prior is the smoothed estimate from the level above, forming a chain of priors all the way up to the root.

### The Chinese Restaurant Franchise (CRF)

The algorithm uses an elegant metaphor from probability theory. Imagine each node in the tree is a restaurant:

- **Customers** are your data points. Each customer sits at a **table**.
- Multiple customers can share a table (they are "explained" by the same latent cause).
- The number of **tables** *t_k* at a restaurant (for outcome *k*) determines how much information flows up to the parent restaurant.

If 100 customers all sit at just 2 tables, the parent sees only "2 observations" — meaning the local data is highly concentrated and doesn't need much smoothing. If 100 customers sit at 80 tables, the parent sees "80 observations" — meaning the data is dispersed, and more smoothing is appropriate. The algorithm samples these table configurations to automatically discover the right smoothing strength.

### The Gibbs sampler

Finding the optimal table counts and concentration parameters analytically is intractable. Instead, the algorithm uses a **collapsed Gibbs sampler** — a Markov Chain Monte Carlo (MCMC) method that iteratively:

1. **Samples table counts** (*t_k*) for every node, from the bottom of the tree upward.
2. **Samples concentrations** (*c*) for each level, given the current table counts.
3. **Computes smoothed probabilities** from the current state.
4. **Averages** the probabilities across many iterations (after a burn-in period).

The default runs 5,000 iterations with a 500-iteration burn-in. The averaging over MCMC samples is key: it integrates over the uncertainty in the table counts and concentrations, giving you a probability estimate that accounts for the full posterior, not just a single point estimate.

### Stirling numbers: the mathematical backbone

Sampling the table counts requires evaluating the likelihood of different configurations. This involves **generalized Stirling numbers of the second kind** — combinatorial quantities that count the number of ways to partition *n* customers into *t* non-empty tables. Computing these is expensive, so the library includes a sophisticated caching system that computes them on demand, stores them in a two-tier cache (a triangular array for small values, a chunked dynamic structure for large ones), and extends the cache lazily using a golden-ratio growth strategy.

### Concentration tying strategies

The library supports four strategies for how concentration parameters are shared across nodes:

| Strategy | Description | When to use |
|:---|:---|:---|
| `NONE` | Every node gets its own *c* | Large datasets, maximum flexibility |
| `SAME_PARENT` | Siblings share a *c* | Moderate datasets |
| `LEVEL` (default) | All nodes at the same depth share *c* | Good general-purpose choice |
| `SINGLE` | All non-root nodes share one *c* | Small datasets, maximum sharing |

Tying reduces the number of parameters to estimate, which helps when data is scarce but limits flexibility when data is abundant.

## Quick start

### Compiling
```bash
ant
```

### Running the heart attack example
```bash
java -Xmx1g -cp "bin:lib/*:lib/commons-math3-3.6.1/*" hdp.testing.Test2LevelsExampleHeartAttack
```

### Using it in your code

**With string data** (the library handles encoding for you):
```java
String[][] data = {
    {"yes", "heavy", "tall"},
    {"no",  "light", "short"},
    {"no",  "heavy", "med"},
    // ... more rows ...
    {"yes", "heavy", "med"}
};

ProbabilityTree hdp = new ProbabilityTree();
hdp.addDataset(data);  // learns p(target | x1, x2, ...)

// query specific combinations
double[] probs = hdp.query("heavy", "short");
System.out.println(Arrays.toString(hdp.getValuesTarget()));  // [yes, no]
System.out.println(Arrays.toString(probs));                  // [0.32, 0.68]
```

**With integer-coded data** (first column is the target, values indexed from 0):
```java
int[][] data = {
    {0, 0, 2},  // target=0, x1=0, x2=2
    {1, 1, 0},  // target=1, x1=1, x2=0
    // ...
};

ProbabilityTree hdp = new ProbabilityTree();
hdp.addDataset(data);

// query: given x1=0, x2=1, what is p(target)?
double[] probs = hdp.query(new int[]{0, 1});
```

**With custom parameters:**
```java
ProbabilityTree hdp = new ProbabilityTree(
    true,                    // createFullTree (pre-allocate all nodes)
    10000,                   // Gibbs iterations (default: 5000)
    TyingStrategy.SAME_PARENT, // concentration tying
    5                        // concentration sampling frequency
);
hdp.addDataset(data);
```

## Beyond Bayesian Network classifiers

Although this library was built for Bayesian Network parameter estimation, the core capability — estimating conditional categorical distributions with intelligent smoothing — is useful in many other contexts:

- **Language models**: Estimating *p(next word | context)* in n-gram models. HDP smoothing is a principled alternative to Kneser-Ney, and the hierarchy naturally captures backoff (trigram → bigram → unigram → uniform).

- **Recommendation systems**: Estimating *p(item | user features)* when user-item combinations are sparse. The hierarchy shares information across similar user profiles.

- **Medical diagnosis**: Estimating *p(diagnosis | symptoms)* where some symptom combinations are rare. The hierarchy ensures you always get a reasonable estimate, even for unseen combinations.

- **Text classification**: Any Naive Bayes or Bayesian Network classifier benefits from better probability estimates. Replace your Laplace-smoothed tables with HDP-smoothed ones for better calibrated predictions.

- **Any conditional probability table**: If you have a CPT with sparse counts and want better estimates than "count + smooth", this library is a drop-in replacement.

The key requirement is that all variables are **categorical** (not continuous). If you have continuous features, you need to discretize them first.

## Dependencies

* [Apache Commons Math 3.6.1](https://commons.apache.org/proper/commons-math/) — for statistical distributions and special functions
* [MLTools](https://github.com/HerrmannM/MLTools) — for math utilities and unsafe array operations

### Getting a cross-platform jar

Running `ant` creates `dist/HDP.jar`.

### Memory

The Stirling number cache allocates ~118 MB on startup. For large models, increase JVM memory:
```bash
java -Xmx4g -cp "bin:lib/*:lib/commons-math3-3.6.1/*" your.MainClass
```

## Going deeper: the mathematics

### The generative model

The HDP defines a generative process for the probability tree. At each non-root node *j* at depth *d*, the probability vector **p**_j is drawn from a Dirichlet Process:

> **p**_j ~ DP(c_d, **p**_parent(j))

where *c_d* is the concentration parameter at depth *d*, and **p**_parent(j) is the probability vector at the parent node. At the root, the prior is uniform: **p**_root ~ DP(c_0, Uniform(1/K)).

### The collapsed representation

Rather than explicitly representing the infinite-dimensional DP draws, the algorithm works with the **Chinese Restaurant Franchise** representation. For each node *j* and outcome *k*:

- *n_jk* = number of observations (at leaves) or sum of children's table counts (at internal nodes)
- *t_jk* = number of "tables" serving outcome *k* (a latent variable)
- *N_j* = sum_k(n_jk), the marginal count
- *T_j* = sum_k(t_jk), the marginal table count

The constraint is: 1 <= *t_jk* <= *n_jk* whenever *n_jk* > 0.

### The predictive distribution

Given a state of the sampler (current *t_jk* and *c_d* values), the smoothed probability at node *j* is:

> p_jk = n_jk / (N_j + c_d) + c_d / (N_j + c_d) * p_parent(j),k

This is a weighted average between the local empirical distribution and the parent's smoothed estimate. The concentration *c_d* controls the interpolation: more concentration means more weight on the parent.

### Sampling the table counts

The full conditional for *t_jk* involves generalized Stirling numbers of the second kind. The unnormalized log-posterior for a candidate value *t* is:

> log P(t_jk = t | rest) ∝ log S(n_jk, t) + log (c_d)_T_j + log S(n_parent,k, t_parent,k) - log Gamma(c_parent + N_parent) + log Gamma(c_parent)

where *S(n, t)* is the (unsigned) Stirling number of the first kind and *(c)_T* is the Pochhammer symbol (rising factorial). The algorithm evaluates this over a window of candidate values and samples proportionally.

### Sampling the concentration

The concentration *c_d* is sampled using an auxiliary variable scheme (Escobar & West, 1995; Teh et al., 2006):

1. For each node *j* tied to *c_d*, sample an auxiliary variable: *q_j* ~ Beta(*c_d*, *N_j*)
2. Compute the posterior rate: rate = prior_rate + sum_j(log(1/q_j))
3. Sample: *c_d* ~ Gamma(sum_j(T_j) + prior_shape, 1/rate)

The prior on *c* is Gamma(2, 2/c_0), where *c_0* is the initial concentration.

## Underlying research and scientific paper

This code supports [our paper in Machine Learning](https://doi.org/10.1007/s10994-018-5718-0) entitled "Accurate parameter estimation for Bayesian Network Classifiers using Hierarchical Dirichlet Processes".

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
  url = {https://doi.org/10.1007/s10994-018-5718-0}
}
```

## Future work: Pitman-Yor Process support

The codebase is architecturally close to supporting the **Pitman-Yor Process** (PYP), a generalization of the Dirichlet Process that adds a *discount parameter* `d` (0 <= d < 1). Where the DP smoothing formula is:

> p_k = n_k / (N + c) + c / (N + c) * parent_k

the PYP formula becomes:

> p_k = (n_k - d * t_k) / (N + c) + (c + d * T) / (N + c) * parent_k

The discount takes a little mass from every observed category — proportionally more from rare ones — and redistributes it toward the parent. This produces **power-law tails** instead of the exponential tails of the DP, which better captures distributions with many rare categories (e.g., word frequencies, product catalogs, species counts).

For the typical Bayesian Network use case with small categorical variables (a handful of states each), the DP (`d=0`) is the right choice — there is no long tail to model. The PYP becomes interesting when the target or conditioning variables have **hundreds or thousands of possible values**, especially in open-ended vocabularies where genuinely unseen categories are expected.

### What's already in place

The hardest part — computing **generalized Stirling numbers** S_d(n, k) for arbitrary d — is fully implemented in `LogStirlingGenerator`. The `logPochhammerSymbol` function also already handles d > 0. The remaining work is plumbing:

1. Store `d` as a field (on `Concentration` or `ProbabilityTree`)
2. Replace the six hardcoded `0.0` discount values in `ProbabilityNode` with that field
3. Add the `d * t_k` terms to `computeProbabilities()`

This would be enough to support PYP with a **fixed discount** (set by the user, not learned). Sampling `d` from its posterior would require an additional Metropolis-Hastings step, but a fixed `d` is a reasonable starting point — in practice, values around 0.2-0.5 work well for many power-law distributed datasets.

## Contributors

Original research and code by:
* [Dr Francois Petitjean](https://github.com/fpetitjean)
* [Pr. Wray Buntine](https://research.monash.edu/en/persons/wray-buntine)
* [Pr. Geoffrey I. Webb](https://research.monash.edu/en/persons/geoff-webb)
* [Dr Nayyar Zaidi](https://github.com/nayyarzaidi)

Work on the Stirling Number Generator:
* [Dr Matthieu Herrmann](https://github.com/HerrmannM)

## Support
YourKit is supporting this open-source project with its full-featured Java Profiler.
YourKit is the creator of innovative and intelligent tools for profiling Java and .NET applications. http://www.yourkit.com

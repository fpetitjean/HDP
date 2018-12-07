package monash.ml.hdp;

/**
 * Represents different tying strategies for parameters of the PYP process
 * (concentration, maybe discount at some point)
 * 
 * @author Francois Petitjean
 */
public enum TyingStrategy {
	/**
	 * No tying: each node gets a concentration parameter
	 */
	NONE,
	/**
	 * All nodes sharing the same parents are tied.
	 */
	SAME_PARENT,

	/**
	 * All nodes at the same level are tied.
	 */
	LEVEL,
	/**
	 * All nodes share a single parameter
	 */
	SINGLE
}

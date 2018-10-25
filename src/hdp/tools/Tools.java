package hdp.tools;

public class Tools {

	/**
	 * Debug flag. The compiler should inline things, so it is ok to just do a "if"
	 * for a conditional compilation effect.
	 */
	public static final boolean DEBUG = false;

	/**
	 * To be put in cases we think should not happen. Print a message, the stack
	 * trace and exit the program.
	 */
	public static void shouldNotHappen(Object o) {
		System.err.flush();
		if (o != null) {
			System.err.println("SHOULD NOT HAPPEN: " + o);
		} else {
			System.err.println("SHOULD NOT HAPPEN: called with null object");
		}

		System.err.println("A situation that was believed not to happen... happened.");
		System.err.println("There is a bug in our program, please contact us!");
		(new RuntimeException()).printStackTrace();
		System.exit(2);
	}

}

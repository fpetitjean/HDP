package testing;

import org.junit.Assert;
import org.junit.Test;

import tools.LogStirlingCache;

public class TestStirlingCache {
	@Test
	public void testInit(){
		int N = 100000;
		int maxM = 2000;
		double a = 0.0;
		LogStirlingCache cache = new LogStirlingCache(a, N);
		for (int n = 0; n <= N; n++) {
			for (int m = 0; m <= maxM; m++) {
				double st = cache.query(a, n, m);
				Assert.assertTrue("Found NaN stirling for (a="+a+",n="+n+",m="+m+")", !Double.isNaN(st));
			}
		}
	}

}

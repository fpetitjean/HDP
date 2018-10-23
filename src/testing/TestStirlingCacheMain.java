package testing;

import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import logStirling.LogStirlingFactory;
import logStirling.LogStirlingGenerator;
import logStirling.LogStirlingGenerator.CacheExtensionException;
import tools.LogStirlingCache;

public class TestStirlingCacheMain {
	
    public static String toMinSecMilSecs(long duration){
    	long min = TimeUnit.MINUTES.convert(duration,  TimeUnit.NANOSECONDS);
    	long sec = TimeUnit.SECONDS.convert(duration,  TimeUnit.NANOSECONDS) - min;
    	long mis = TimeUnit.MILLISECONDS.convert(duration,  TimeUnit.NANOSECONDS) - sec;
        return String.format("%d min, %d sec, %d ms",min, sec, mis);
    }

	public static void main(String...args) throws NoSuchAlgorithmException, NoSuchFieldException, IllegalAccessException, CacheExtensionException {
		// Computation parameters
		final int N = 200000;
		final int maxM = 2000;
		final double a = 0.0;
		
		// Comparison threshold
		final double THRESHOLD = 0.0000001;
		
		// Time measurement
		long orig_start, orig_total=0;
		long new_start, new_total=0;
		
		orig_start = System.nanoTime();
		LogStirlingCache cache = new LogStirlingCache(a, N);
		orig_total += System.nanoTime() - orig_start;
		
		new_start = System.nanoTime();
		LogStirlingGenerator cache2 = LogStirlingFactory.newLogStirlingGenerator(N, a);
		new_total = System.nanoTime() - new_start;
		
		for (int n = 0; n <= N; n++) {
			for (int m = 0; m <= maxM; m++) {
				
				new_start = System.nanoTime();
				float st2 = cache2.query(n,  m);
				new_total += System.nanoTime() - new_start;
				
				orig_start = System.nanoTime();
				float st = (float)cache.query(a, n, m);
				orig_total += System.nanoTime() - orig_start;
				
				Assert.assertTrue("Found NaN stirling for (a="+a+",n="+n+",m="+m+")", !Double.isNaN(st));
				if(Math.abs(st-st2) > THRESHOLD) {
					System.err.println("S("+n+","+m+") = " + st2 + " !=  " + st);
					System.exit(1);
				}
				
			}
		}
		
		
		
		System.out.println("Total time for orig:" + toMinSecMilSecs(orig_total));
		System.out.println("Total time for new: " + toMinSecMilSecs(new_total));
		
		
	}

}

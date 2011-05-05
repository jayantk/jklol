package edu.cmu.ml.rtw.dirichlet;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Collection;

public class DirichletDistribution<T> implements PriorDistribution<T> {

    private List<T> baseItems;
    private Map<T, Integer> itemIndex;
    private int numDims;
    private double[] concentrationParams;

    private double[] posteriorConcentration; // To re-use memory.
    private double[] sampleParams;

    // Cache samples...
    private List<T> observationCache;
    private double[] sufficientStatistics;
    private double[] exactPosteriorParams;
    private double sumSufficientStatistics;


    public DirichletDistribution(List<T> items, double[] concentrationParams) {
	this.baseItems = items;
	itemIndex = new HashMap<T, Integer>();
	for (int i = 0; i < items.size(); i++) {
	    itemIndex.put(baseItems.get(i), i);
	}
	this.concentrationParams = concentrationParams;
	numDims = concentrationParams.length;
	posteriorConcentration = new double[numDims];
	sampleParams = new double[numDims];

	observationCache = null;
	sufficientStatistics = new double[numDims];
	exactPosteriorParams = new double[numDims];
    }

    public DirichletDistribution(List<T> items, double uniformConcentrationParam) {
	this.baseItems = items;
	itemIndex = new HashMap<T, Integer>();
	for (int i = 0; i < items.size(); i++) {
	    itemIndex.put(baseItems.get(i), i);
	}

	concentrationParams = new double[baseItems.size()];
	for (int i = 0; i < concentrationParams.length; i++) {
	    concentrationParams[i] = uniformConcentrationParam;
	}
	numDims = concentrationParams.length;
	posteriorConcentration = new double[numDims];
	sampleParams = new double[numDims];

	observationCache = null;
	sufficientStatistics = new double[numDims];
	exactPosteriorParams = new double[numDims];
    }

    public Multinomial<T> sample() {
	return new Multinomial(baseItems, sampleDirichlet(concentrationParams, null));
    }

    public double getDensity(Distribution<T> point) {
	throw new UnsupportedOperationException();
    }

    public double getDensity(LabeledVector<Distribution<T>> iidPoints) {
	throw new UnsupportedOperationException();
    }

    public Multinomial<T> samplePosterior(List<T> observed) {

	for (int i = 0; i < concentrationParams.length; i++) {
	    posteriorConcentration[i] = concentrationParams[i];
	}
	for (T o : observed) {
	    posteriorConcentration[itemIndex.get(o)] += 1;
	}

	return new Multinomial(baseItems, sampleDirichlet(posteriorConcentration, null));
    }

    public Multinomial<T> samplePosterior(List<T> allObserved, Collection<Integer> indices) {
	for (int i = 0; i < concentrationParams.length; i++) {
	    posteriorConcentration[i] = concentrationParams[i];
	}
	for (Integer i : indices) {
	    T o = allObserved.get(i);
	    posteriorConcentration[itemIndex.get(o)] += 1;
	}

	return new Multinomial(baseItems, sampleDirichlet(posteriorConcentration, null));
    }

    // Warning: This only works if you sample exactly one item from the posterior!
    public Multinomial<T> exactPosterior(List<T> allObserved, int excludeIndex) {

	if (allObserved != observationCache) {
	    for (int i = 0; i < concentrationParams.length; i++) {
		sufficientStatistics[i] = concentrationParams[i];
	    }
	    for (T o : allObserved) {
		sufficientStatistics[itemIndex.get(o)] += 1;
	    }
	    sumSufficientStatistics = 0.0;
	    for (int i = 0; i < concentrationParams.length; i++) {
		sumSufficientStatistics += sufficientStatistics[i];
	    }
	    observationCache = allObserved;
	}

	for (int i = 0; i < numDims; i++) {
	    if (i == excludeIndex) {
		exactPosteriorParams[i] = (sufficientStatistics[i] - 1) / (sumSufficientStatistics - 1);
	    } else {
		exactPosteriorParams[i] = sufficientStatistics[i] / (sumSufficientStatistics - 1);
	    }
	}

	return new Multinomial(baseItems, exactPosteriorParams);
    }



    public Multinomial<T> samplePosteriorCollection(List<LabeledVector<T>> observed) {
	for (int i = 0; i < concentrationParams.length; i++) {
	    posteriorConcentration[i] = concentrationParams[i];
	}

	for (LabeledVector<T> o : observed) {
	    for (T item : o.getContexts()) {
		posteriorConcentration[itemIndex.get(item)] += o.getCount(item);
	    }
	}
	return new Multinomial(baseItems, sampleDirichlet(posteriorConcentration, null));
    }


    /**
     * Draw a random vector from the Dirichlet distribution.
     * If arrayToUse is non-null, the output vector is placed into arrayToUse.
     */
    public static double[] sampleDirichlet(double[] concentration, double[] arrayToUse) {
	double[] out = arrayToUse;
	if (arrayToUse == null) {
	    out = new double[concentration.length];
	} else {
	    assert arrayToUse.length == concentration.length;
	}

	double sum = 0.0;
	for (int i = 0; i < concentration.length; i++) {
	    out[i] = rGamma(concentration[i], 1.0);
	    sum += out[i];
	}

	for (int i = 0; i < concentration.length; i++) {
	    out[i] = out[i] / sum;
	}
	return out;
    }

	   
    // The following code is taken from Mahout: 
    // http://search-lucene.com/c/Mahout:/core/src/main/java/org/apache/mahout/clustering/dirichlet/UncommonDistributions.java||+%25223+4%2522+%25224+5%2522+%25224+5%2522
    // =============== start of BSD licensed code. 
    /**
     * Returns a double sampled according to this distribution. Uniformly fast for all k > 0. (Reference:
     * Non-Uniform Random Variate Generation, Devroye http://cgm.cs.mcgill.ca/~luc/rnbookindex.html) Uses
     * Cheng's rejection algorithm (GB) for k>=1, rejection from Weibull distribution for 0 < k < 1.
     */
    public static double rGamma(double k, double lambda) {
	boolean accept = false;
	if (k >= 1.0) {
	    // Cheng's algorithm
	    double b = k - Math.log(4.0);
	    double c = k + Math.sqrt(2.0 * k - 1.0);
	    double lam = Math.sqrt(2.0 * k - 1.0);
	    double cheng = 1.0 + Math.log(4.5);
	    double x;
	    do {
	        double u = Math.random();
	        double v = Math.random();
	        double y = 1.0 / lam * Math.log(v / (1.0 - v));
	        x = k * Math.exp(y);
	        double z = u * v * v;
	        double r = b + c * y - x;
	        if ((r >= 4.5 * z - cheng) || (r >= Math.log(z))) {
		    accept = true;
	        }
	    } while (!accept);
	    return x / lambda;
	} else {
	    // Weibull algorithm
	    double c = 1.0 / k;
	    double d = (1.0 - k) * Math.pow(k, k / (1.0 - k));
	    double x;
	    do {
	        double u = Math.random();
	        double v = Math.random();
	        double z = -Math.log(u);
	        double e = -Math.log(v);
	        x = Math.pow(z, c);
	        if (z + e >= d + x) {
		    accept = true;
	        }
	    } while (!accept);
	    return x / lambda;
	}
    }
    
    // ============= end of BSD licensed code
    

}
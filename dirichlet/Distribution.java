package edu.cmu.ml.rtw.dirichlet;


/**
 * A Distribution represents a probability distribution over objects of type T (which are points).
 */
public interface Distribution<T> {

    /**
     * Draw a sample from this probability distribution.
     */
    public T sample();
    
    /**
     * Return the probability density at the provided point.
     */
    public double getDensity(T point);

    /**
     * Get the likelihood of a bag of draws from the distribution.
     */ 
    public double getDensity(LabeledVector<T> iidPoints);

}
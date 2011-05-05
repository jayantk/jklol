package edu.cmu.ml.rtw.dirichlet;

import java.util.List;
import java.util.Collection;

/**
 * A PriorDistribution represents a probability distribution over objects of type T 
 * which also allows you to sample from a posterior.
 */
public interface PriorDistribution<T> extends Distribution<Distribution<T>> {

    public Distribution<T> samplePosterior(List<T> observations);

    public Distribution<T> samplePosterior(List<T> allObservations, Collection<Integer> indices);

    public Distribution<T> exactPosterior(List<T> allObservations, int excludeIndex);

    public Distribution<T> samplePosteriorCollection(List<LabeledVector<T>> observations);


}
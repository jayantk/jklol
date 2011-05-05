package edu.cmu.ml.rtw.dirichlet;

import org.apache.log4j.Logger;

import java.util.List;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import java.util.LinkedList;
import java.util.HashSet;
import java.util.Map;
import java.util.ArrayList;

/**
 * A DirichletProcess is a distribution on distributions of type T.
 */ 
public class DirichletProcess<T> implements PriorDistribution<T> {

    private static Logger log = Logger.getLogger(DirichletProcess.class);

    private final int MAX_TOPICS = 10000;

    private PriorDistribution<T> baseMeasure;
    private double concentration;

    public DirichletProcess(PriorDistribution<T> baseMeasure, double concentration) {
	this.baseMeasure = baseMeasure;
	this.concentration = concentration;
    }

    public DrawnDiscreteMeasure<T> sample() {
	throw new RuntimeException();
    }

    public DrawnDiscreteMeasure<T> samplePosterior(List<T> observations) {
	throw new RuntimeException();
    }

    public DrawnDiscreteMeasure<T> samplePosterior(List<T> observations, Collection<Integer> indices) {
	throw new RuntimeException();
    }

    public DrawnDiscreteMeasure<T> exactPosterior(List<T> observations, int excludeIndex) {
	throw new RuntimeException();
    }


    public DrawnDiscreteMeasure<T> samplePosteriorCollection(List<LabeledVector<T>> observations) {
	throw new RuntimeException();
    }

    public double getDensity(Distribution<T> point) {
	    throw new RuntimeException();
    }

    public double getDensity(LabeledVector<Distribution<T>> point) {
	    throw new RuntimeException();
    }

    public void gibbsSample(int numIterations, List<T> observations) {
	Map<Integer, Integer> topicIndex = new HashMap<Integer, Integer>();
	List<Set<Integer>> topicObservations = new ArrayList<Set<Integer>>();
	List<Distribution<T>> topicDistributions = new ArrayList<Distribution<T>>();

	

	// Compute the number of elements assigned to each topic so far.
	double[] likelihoods = new double[MAX_TOPICS];
	double likelihoodSum = 0.0;
	double[] binCounts =   new double[MAX_TOPICS];
	for (int i = 0; i < binCounts.length; i++) {
	    binCounts[i] = 0;
	}
	LinkedList<Integer> unusedInds = new LinkedList<Integer>();

	for (int iterCount = 0; iterCount < numIterations; iterCount++) {
	    for (int obsIndex = 0; obsIndex < observations.size(); obsIndex++) {
		// Figure out the index of the next unused topic in the array.
		int newTopicInd = -1;
		if (unusedInds.size() > 0) { 
		    newTopicInd = unusedInds.removeFirst();
		    // FIXME!
		    topicDistributions.set(newTopicInd, baseMeasure.exactPosterior(observations, obsIndex));
		} else {
		    newTopicInd = topicDistributions.size();
		    topicDistributions.add(baseMeasure.sample());
		    topicObservations.add(new HashSet<Integer>());
		}
		binCounts[newTopicInd] = concentration;

		if (topicIndex.containsKey(obsIndex)) {
		    binCounts[topicIndex.get(obsIndex)]--;
		    topicObservations.get(topicIndex.get(obsIndex)).remove(obsIndex);

		    if (binCounts[topicIndex.get(obsIndex)] == 0) {
			unusedInds.add(topicIndex.get(obsIndex));
		    }
		}
		// resample the topic index that generated this observation.
		// P(Z_i | x, y,\ theta, \gamma, \alpha)
		likelihoodSum = 0.0;
		for (int i = 0; i < topicDistributions.size(); i++) {
		    likelihoods[i] = binCounts[i] * topicDistributions.get(i).getDensity(observations.get(obsIndex));
		    likelihoodSum += likelihoods[i];
		}
		
		int drawnIndex = -1;
		double theDraw = Math.random() * likelihoodSum;
		double curSum = 0.0;
		for (int i = 0; i < topicDistributions.size(); i++) {
		    curSum += likelihoods[i];
		    if (theDraw <= curSum) {
			drawnIndex = i;
			break;
		    }
		}

		if (drawnIndex != newTopicInd) {
		    // Drew an already existing topic. Add to its count, then remove
		    // the newly drawn topic from consideration.
		    binCounts[drawnIndex] += 1;
		    unusedInds.add(newTopicInd);
		    binCounts[newTopicInd] = 0;
		} else {
		    // Drew the new topic so don't delete it.
		    binCounts[drawnIndex] = 1;
		    // Sample the topic from the posterior distribution...
		    topicDistributions.set(newTopicInd, baseMeasure.samplePosterior(observations.subList(obsIndex, obsIndex + 1)));
		}

		topicIndex.put(obsIndex, drawnIndex);
		topicObservations.get(drawnIndex).add(obsIndex);
	    }
	    for (int i = 0; i < topicDistributions.size(); i++) {
		if (binCounts[i] > 0) {
		    topicDistributions.set(i, baseMeasure.samplePosterior(observations, topicObservations.get(i)));
		}
	    }

	    if (iterCount % 100 == 0) {
		log.info("*** ITERATION " + iterCount + " ***");
		for (int i = 0; i < topicDistributions.size(); i++) {
		    if (binCounts[i] > 0) {
			log.info("Topic " + i + ": " + binCounts[i]);
			/*
			StringBuilder sb = new StringBuilder();
			sb.append("[");
			sb.append(topicObservations.get(i).size());
			sb.append(", ");			
			for (Integer obsIndex : topicObservations.get(i)) {
			    sb.append(observations.get(obsIndex).toString());
			    sb.append(", ");
			}
			sb.append("]");
			*/
			log.info(topicDistributions.get(i).toString());
		    }
		}
	    }
	}

    }

    /*
    public void gibbsSample(int numIterations, Collection<LabeledVector<T>> observations) {
	
	Map<LabeledVector<T>, Integer> topicIndex = new HashMap<LabeledVector<T>, Integer>();
	List<Set<LabeledVector<T>>> topicObservations = new ArrayList<Set<LabeledVector<T>>>();
	List<Distribution<T>> topicDistributions = new ArrayList<Distribution<T>>();

	// Compute the number of elements assigned to each topic so far.
	double[] likelihoods = new double[MAX_TOPICS];
	double likelihoodSum = 0.0;
	double[] binCounts =   new double[MAX_TOPICS];
	for (int i = 0; i < binCounts.length; i++) {
	    binCounts[i] = 0;
	}
	LinkedList<Integer> unusedInds = new LinkedList<Integer>();

	for (int iterCount = 0; iterCount < numIterations; iterCount++) {
	    for (LabeledVector<T> o : observations) {

		// Figure out the index of the next unused topic in the array.
		int newTopicInd = -1;
		if (unusedInds.size() > 0) { 
		    newTopicInd = unusedInds.removeFirst();
		    topicDistributions.set(newTopicInd, baseMeasure.sample());
		} else {
		    newTopicInd = topicDistributions.size();
		    topicDistributions.add(baseMeasure.sample());
		    topicObservations.add(new HashSet<LabeledVector<T>>());
		}
		binCounts[newTopicInd] = concentration;

		if (topicIndex.containsKey(o)) {
		    binCounts[topicIndex.get(o)]--;
		    topicObservations.get(topicIndex.get(o)).remove(o);

		    if (binCounts[topicIndex.get(o)] == 0) {
			unusedInds.add(topicIndex.get(o));
		    }
		}		
		
		// resample the topic index that generated this observation.
		// P(Z_i | x, y, \theta, \gamma, \alpha)
		likelihoodSum = 0.0;
		for (int i = 0; i < topicDistributions.size(); i++) {
		    likelihoods[i] = binCounts[i] * topicDistributions.get(i).getDensity(o);
		    likelihoodSum += likelihoods[i];
		}
		
		int drawnIndex = -1;
		double theDraw = Math.random() * likelihoodSum;
		double curSum = 0.0;
		for (int i = 0; i < topicDistributions.size(); i++) {
		    curSum += likelihoods[i];
		    if (theDraw <= curSum) {
			drawnIndex = i;
			break;
		    }
		}
		
		if (drawnIndex != newTopicInd) {
		    // Drew an already existing topic. Add to its count, then remove
		    // the newly drawn topic from consideration.
		    binCounts[drawnIndex] += 1;
		    unusedInds.add(newTopicInd);
		} else {
		    // Drew the new topic so don't delete it.
		    binCounts[drawnIndex] = 1;
		}
		
		topicIndex.put(o, drawnIndex);
		topicObservations.get(drawnIndex).add(o);
	    }
	    
	    for (int i = 0; i < topicDistributions.size(); i++) {
		if (binCounts[i] > 0) {
		    topicDistributions.set(i, baseMeasure.samplePosteriorCollection(topicObservations.get(i)));
		}
	    }

	    if (iterCount % 10 == 0) {
		log.info("*** ITERATION " + iterCount + " ***");
		for (int i = 0; i < topicDistributions.size(); i++) {
		    if (binCounts[i] > 0) {
			log.info("Topic " + i + ": " + binCounts[i]);
			StringBuilder sb = new StringBuilder();
			sb.append("[");
			for (LabeledVector<T> v : topicObservations.get(i)) {
			    sb.append(v.getLabel());
			    sb.append(", ");
			}
			sb.append("]");
			log.info(sb.toString());
		    }
		}
	    }
	}
    }
    */

}


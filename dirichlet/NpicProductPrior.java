package edu.cmu.ml.rtw.dirichlet;

import java.util.HashSet;
import java.util.List;
import java.util.Collection;
import java.util.ArrayList;

public class NpicProductPrior implements PriorDistribution<Npic> {

    private PriorDistribution<String> npPrior;
    private PriorDistribution<String> contextPrior;

    private List<String> nps = new ArrayList<String>();
    private List<String> contexts = new ArrayList<String>();
    private List<Npic> cachedAllObservations = null;


    public NpicProductPrior(PriorDistribution<String> npPrior, PriorDistribution<String> contextPrior) {
	this.npPrior = npPrior;
	this.contextPrior = contextPrior;

	nps = new ArrayList<String>();
	contexts = new ArrayList<String>();
	cachedAllObservations = null;
    }

    public NpicProductMeasure samplePosterior(List<Npic> observations) {
	List<String> curNps = new ArrayList<String>();
	for (Npic npic : observations) {
	    curNps.add(npic.getNp());
	}
	
	List<String> curContexts = new ArrayList<String>();
	for (Npic npic : observations) {
	    curContexts.add(npic.getContext());
	}

	Distribution<String> npPosterior = npPrior.samplePosterior(curNps);
	Distribution<String> contextPosterior = contextPrior.samplePosterior(curContexts);

	return new NpicProductMeasure(npPosterior, contextPosterior);
    }

    public NpicProductMeasure samplePosterior(List<Npic> allObservations, Collection<Integer> indices) {
	if (allObservations != cachedAllObservations) {
	    nps.clear();
	    contexts.clear();
	    for (Npic npic : allObservations) {
		nps.add(npic.getNp());
		contexts.add(npic.getContext());
	    }
	    cachedAllObservations = allObservations;
	}

	Distribution<String> npPosterior = npPrior.samplePosterior(nps, indices);
	Distribution<String> contextPosterior = contextPrior.samplePosterior(contexts, indices);

	return new NpicProductMeasure(npPosterior, contextPosterior);
    }

    public NpicProductMeasure exactPosterior(List<Npic> allObservations, int excludeIndex) {
	if (allObservations != cachedAllObservations) {
	    nps.clear();
	    contexts.clear();
	    for (Npic npic : allObservations) {
		nps.add(npic.getNp());
		contexts.add(npic.getContext());
	    }
	    cachedAllObservations = allObservations;
	}

	Distribution<String> npPosterior = npPrior.exactPosterior(nps, excludeIndex);
	Distribution<String> contextPosterior = contextPrior.exactPosterior(contexts, excludeIndex);

	return new NpicProductMeasure(npPosterior, contextPosterior);
    }



    public NpicProductMeasure samplePosteriorCollection(List<LabeledVector<Npic>> observations) {
	throw new RuntimeException("");
    }


    public NpicProductMeasure sample() {
	return new NpicProductMeasure(npPrior.sample(), contextPrior.sample());
    }


    public double getDensity(Distribution<Npic> point) {
	throw new UnsupportedOperationException();
    }

    public double getDensity(LabeledVector<Distribution<Npic>> iidPoints) {
	throw new UnsupportedOperationException();
    }


}

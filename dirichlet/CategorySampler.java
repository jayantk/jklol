package edu.cmu.ml.rtw.dirichlet;

import org.apache.log4j.Logger;

import edu.cmu.ml.rtw.util.Properties;

import edu.cmu.ml.rtw.course.TheoKB;
import edu.cmu.ml.rtw.kb.KbManipulation;
import edu.cmu.ml.rtw.kb.KbUtility;

import edu.cmu.ml.rtw.tinkertoy.TinkerToy;
import edu.cmu.ml.rtw.tinkertoy.TinkerToyServices;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import edu.cmu.ml.rtw.util.AllPairsGrep;
import edu.cmu.ml.rtw.util.TrieKeyFilter;
import edu.cmu.ml.rtw.util.KeyFilter;
import edu.cmu.ml.rtw.util.MutualInformationAccumulator;
import edu.cmu.ml.rtw.util.AllPairsVectorStore;
import java.util.Iterator;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;


public class CategorySampler implements TinkerToy {

    private static Logger log = Logger.getLogger(CategorySampler.class);
    private Properties properties;

    TheoKB theoKB = new TheoKB();

    private String tinkerToyName = "CategorySampler";


    public CategorySampler() {
	properties = Properties.loadFromClassName(CategorySampler.class.getName());
    }

    public void tinkerToyRun(TinkerToyServices tts) throws Exception {
	theoKB.initializeTheo();
	theoKB.useTinkerToyServices(tts);

	String categoryName = "concept:sportsteam";
	String categoryAllPairData = properties.getProperty("categoryAllPairs");
	double concentrationValue = 1.0;
	int numIterations = 1000;
	int numContexts = 100;
	int minCount = 5;

	double concentrationParameter = 1.0;

	// List<LabeledVector<String>> observations = loadObservations(categoryName, categoryAllPairData, numContexts, minCount);
	// List<LabeledVector<String>> observations = syntheticData();

	List<Npic> observations = syntheticDataNpic();

	Set<String> contexts = new HashSet<String>();
	for (Npic o : observations) {
	    contexts.add(o.getContext());
	}
	List<String> numberedContexts = new ArrayList<String>(contexts);

	Set<String> nps = new HashSet<String>();
	for (Npic o : observations) {
	    nps.add(o.getNp());
	}
	List<String> numberedNps = new ArrayList<String>(nps);

	log.info(observations.size() + " vectors");

	DirichletDistribution<String> contextPrior = new DirichletDistribution<String>(numberedContexts, concentrationValue);
	DirichletDistribution<String> npPrior = new DirichletDistribution<String>(numberedNps, concentrationValue);
	NpicProductPrior npicPrior = new NpicProductPrior(npPrior, contextPrior);
	DirichletProcess<Npic> dp = new DirichletProcess<Npic>(npicPrior, concentrationParameter);
	dp.gibbsSample(numIterations, observations);
    }

    public List<Npic> syntheticDataNpic() {
	List<LabeledVector<String>> vectors = syntheticData();
	
	List<Npic> observations = new ArrayList<Npic>();
	for (LabeledVector<String> v : vectors) {
	    for (String context : v.getContexts()) {
		Npic npic = new Npic(v.getLabel(), context);
		for (int i =0; i < v.getCount(context); i++) {
		    observations.add(npic);
		}
	    }
	}
	return observations;
    }

    public List<LabeledVector<String>> syntheticData() {

	int numDims = 2;
	double concentrationVal = 1.0;
	int numClusters = 2;
	int numDraws = 100;
	int numSamples = 4;

	List<String> dimNames = new ArrayList<String>();
	double[] concentration = new double[numDims];
	for (int i = 0; i < numDims; i++) {
	    dimNames.add("dim-" + i);
	    concentration[i] = concentrationVal;
	}

	DirichletDistribution<String> prior = new DirichletDistribution(dimNames, concentration);
	List<Multinomial<String>> multinomials = new ArrayList<Multinomial<String>>();
	for (int i = 0; i < numClusters; i++) {
	    multinomials.add(prior.sample());
	    log.info(multinomials.get(multinomials.size() - 1));
	}

	List<LabeledVector<String>> vecs = new ArrayList<LabeledVector<String>>();
	for (int i = 0; i < numSamples; i++) {
	    Map<String, Integer> countVec = new HashMap<String, Integer>();
	    for (int j = 0; j < numDraws; j++) {
		String draw = multinomials.get(i % numClusters).sample();
		if (!countVec.containsKey(draw)) {
		    countVec.put(draw, 0);
		}
		countVec.put(draw, countVec.get(draw) + 1);
	    }
	    vecs.add(LabeledVector.fromIntCounts("pt-" + i, countVec));
	}
	return vecs;
    }


    public List<LabeledVector<String>> loadObservations(String categoryName, 
	    String categoryAllPairsFile, int numContexts, int minCount) {

	// Get all of the literal strings in the KB to find contexts with high
	// mutual information with each category that we care about.
	Set<String> literals = new HashSet<String>();
	Iterator<String> entIter = KbUtility.getEntityIterator("concept:everypromotedthing");
	int i = 0;
	while (entIter.hasNext()) {
	  String nextEnt = entIter.next();
	  if (KbUtility.isCategory(nextEnt)) {
	    continue;
	  }
	  literals.addAll(KbUtility.getLiteralStrings(nextEnt, null));

	  i++;
	  if (i % 1000 == 0) {
	    log.info(i + " concepts");
	  }
	}

	Set<String> categories = new HashSet<String>();
	categories.add(categoryName);

	KeyFilter literalFilter = new TrieKeyFilter(literals);
	MutualInformationAccumulator mutualInformation = new MutualInformationAccumulator(new CategoryKeyTransform(categories), 
		false, false, minCount);
	AllPairsGrep apg = new AllPairsGrep();
	apg.performExtractionFromFiles(literalFilter, categoryAllPairsFile, mutualInformation);

	Set<String> bestContexts = new HashSet<String>();
	for (String category : categories) {
	    bestContexts.addAll(mutualInformation.getMostInformativeContexts(numContexts, category));
	}

	// Gather all of the literals for the categories that we care about:
	Set<String> tokenEntities = new HashSet<String>();
	Set<String> categoryLiterals = new HashSet<String>();
	for (String category : categories) {
	    log.info("Gathering literals for " + category);
	    entIter = KbUtility.getEntityIterator(category);
	    while (entIter.hasNext()) {
		String nextEnt = entIter.next();
		if (KbUtility.isCategory(nextEnt)) {
		    continue;
		}
		categoryLiterals.addAll(KbUtility.getLiteralStrings(nextEnt, null));
		tokenEntities.add(nextEnt);
	    }
	}
	AllPairsVectorStore vecStore = apg.getVectors(categoryLiterals, categoryAllPairsFile, true, minCount);
	    
	List<LabeledVector<String>> vectors = new ArrayList<LabeledVector<String>>();
	try {	    
	    for (String tokenEnt : tokenEntities) {
		Map<String, Integer> tokenRow = vecStore.getTokenRow(tokenEnt);
		Map<String, Double> newRow = new HashMap<String, Double>();
		for (String context : tokenRow.keySet()) {
		    if (bestContexts.contains(context)) {
			newRow.put(context, (double) tokenRow.get(context));
		    }
		}
		LabeledVector v = new LabeledVector(tokenEnt, newRow);
		vectors.add(v);
		log.info(v);
	    }
	} catch (IOException e) {
	    throw new RuntimeException(e);
	}
	
	return vectors;
    }


    /*
     * Helper method to sort a key/value map by value.
     */
    private static Map sortByValue(Map map) {
	List list = new ArrayList(map.entrySet());
	Collections.sort(list, new Comparator() {
		public int compare(Object o1, Object o2) {
		    return -1 * ((Comparable) ((Map.Entry) (o1)).getValue())
			    .compareTo(((Map.Entry) (o2)).getValue());
		}
	    });
	
	Map result = new LinkedHashMap();
	for (Iterator it = list.iterator(); it.hasNext();) {
	    Map.Entry entry = (Map.Entry)it.next();
	    result.put(entry.getKey(), entry.getValue());
	}
	return result;
    }



    public String getName() {
	return tinkerToyName;
    }

    public static void main(String[] args) throws Exception {

        // TinkerToyServices provides a standard main implementation that parses a standard set of
        // commandline arguments and then invokes your tinkerToyRun method. It even does some
        // not-lazy things likes set a reasonable default exception handler for threads and catches
        // and reports exceptions thrown from your tinkerToyRun method.
        //
        TinkerToyServices.runTinkerToy(new CategorySampler(), args);
        System.exit(0);
    }
}
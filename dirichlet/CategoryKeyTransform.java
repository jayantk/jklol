package edu.cmu.ml.rtw.dirichlet;

import org.apache.log4j.Logger;
import java.util.Set;
import java.util.HashMap;
import edu.cmu.ml.rtw.kb.KbUtility;
import edu.cmu.ml.rtw.kb.KbManipulation;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import edu.cmu.ml.rtw.util.KeyTransform;

/**
 * Labels each literalString with all of the concept categories that it
 * is a positive/negative example of.
 */
public class CategoryKeyTransform implements KeyTransform {

    private static Logger log = Logger.getLogger(CategoryKeyTransform.class);

    private Set<String> categories;
    private Map<String, Set<String>> mutexes;

    private Map<String, String> tokenConceptCategoryMap;

    /**
     * Argument is the set of concept categories for which labels will be returned.
     */
    public CategoryKeyTransform(Set<String> categories) {
	this.categories = categories;

	// Cache the mapping between token categories and concept categories.
	tokenConceptCategoryMap = new HashMap<String, String>();
	for (String conceptCat : KbUtility.getConceptCategories(false)) {
	    String tokenCat = KbManipulation.getValue("tokenCategory", conceptCat).asString();
	    tokenConceptCategoryMap.put(tokenCat, conceptCat);
	}

	// Cache which categories are mutually exclusive with each of the categories that 
	// we care about.
	mutexes = new HashMap<String, Set<String>>();
	for (String category : categories) {
	    for (String mutexCat : getAllDescendantCategories(KbUtility.getMutexPredicates(category))) {
		if (!mutexes.containsKey(mutexCat)) {
		    mutexes.put(mutexCat, new HashSet<String>());
		}
		mutexes.get(mutexCat).add(category);
	    }
	}
    }

    public Set<String> getPositiveLabels(String key) {
	Set<String> toReturn = getConceptCategories(KbUtility.getAncestors(key));
	toReturn.retainAll(categories);
	return toReturn;
    }

    public Set<String> getNegativeLabels(String key) {
	Set<String> negativeLabels = new HashSet<String>();
	for (String ancestor : getConceptCategories(KbUtility.getAncestors(key))) {
	    if (mutexes.containsKey(ancestor)) {
		negativeLabels.addAll(mutexes.get(ancestor));
	    }
	}
	return negativeLabels;
    }


    private Set<String> getAllDescendantCategories(Set<String> categories) {
	Set<String> finalCategories = new HashSet<String>(categories);
	for (String category : categories) {
	    Iterator<String> catIter = KbUtility.getCategoryIterator(category);
	    while (catIter.hasNext()) {
		finalCategories.add(catIter.next());
	    }
	}
	return finalCategories;
    }

    private Set<String> getConceptCategories(Set<String> tokenCategories) {
	Set<String> conceptCategories = new HashSet<String>();
	for (String tokenCategory : tokenCategories) {
	    conceptCategories.add(tokenConceptCategoryMap.get(tokenCategory));
	}
	return conceptCategories;
    }

}
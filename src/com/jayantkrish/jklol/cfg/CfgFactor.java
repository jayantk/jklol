package com.jayantkrish.jklol.cfg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.bayesnet.CptFactor;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A CfgFactor embeds a context-free grammar in a Bayes Net. The factor defines a distribution over
 * terminal productions conditioned on the root production.
 *
 * You can run exact inference in a Bayes Net containing a CfgFactor provided that the terminal
 * productions are conditioned on.
 */
public class CfgFactor extends DiscreteFactor implements CptFactor {

	private int parentVarNum;
	private DiscreteVariable parentVar;
	private int childVarNum;
	private DiscreteVariable childVar;

	private CptProductionDistribution productionDist;
	private CfgParser parser;

	private DiscreteFactor multipliedWith;

	/**
	 * This factor should always be instantiated over exactly two variables: the parent variable is
	 * a distribution over Productions, the child variable is a distribution over Lists of
	 * Productions. Sadly, the Java type system makes enforcing this requirement very difficult, 
	 * so just know that if you violate this requirement, CfgFactor will break in terrible and unexpected ways. 
	 */
	public CfgFactor(DiscreteVariable parentVar, DiscreteVariable childVar,
			int parentVarNum, int childVarNum, Grammar grammar, 
			CptProductionDistribution productionDist) {
		super(new VariableNumMap(Arrays.asList(new Integer[] {parentVarNum, childVarNum}), 
				Arrays.asList(new DiscreteVariable[] {parentVar, childVar})));

		this.parentVarNum = parentVarNum;
		this.parentVar = parentVar;
		this.childVarNum = childVarNum;
		this.childVar = childVar;		

		this.productionDist = productionDist;
		this.parser = new CfgParser(grammar, productionDist);

		this.multipliedWith = null;
	}

	/*
	 * For implementing products of factors.
	 */
	private CfgFactor(DiscreteVariable parentVar, DiscreteVariable childVar,
			int parentVarNum, int childVarNum, CptProductionDistribution productionDist, 
			CfgParser parser, DiscreteFactor multipliedWith) {
		super(new VariableNumMap(Arrays.asList(new Integer[] {parentVarNum, childVarNum}), 
				Arrays.asList(new DiscreteVariable[] {parentVar, childVar})));

		this.parentVarNum = parentVarNum;
		this.parentVar = parentVar;
		this.childVarNum = childVarNum;
		this.childVar = childVar;		

		this.productionDist = productionDist;
		this.parser = parser;

		this.multipliedWith = multipliedWith;
	}


	public CfgParser getParser() {
		return parser;
	}

	/////////////////////////////////////////////////////////////
	// Required methods for Factor 
	/////////////////////////////////////////////////////////////

	public Iterator<Assignment> outcomeIterator() {
		return new AllAssignmentIterator(getVars());
	}

	public double getUnnormalizedProbability(Assignment a) {
		throw new UnsupportedOperationException("");
		/*
	List<Production> childVarValue = childVar.getValue(a.getVarValue(childVarNum));
	ParseChart c = parser.parseMarginal(childVarValue);
	Map<Production, Double> rootDist = c.getRootDistribution();
	Production rootVal = parentVar.getValue(a.getVarValue(parentVarNum));
	if (!rootDist.containsKey(rootVal)) {
	    return 0.0;
	}
	return rootDist.get(rootVal);
		 */
	}

	/////////////////////////////////////////////////////////////
	// CPT Factor methods
	/////////////////////////////////////////////////////////////

	public void clearCpt() {
		productionDist.clearCpts();
	}

	public void addUniformSmoothing(double virtualCounts) {
		productionDist.addUniformSmoothing(virtualCounts);
	}

	public void incrementOutcomeCount(Assignment assignment, double count) {
		throw new UnsupportedOperationException("");
	}

	public void incrementOutcomeCount(Factor marginal, double count) {
		if (marginal instanceof ChartFactor) {
			ChartFactor cf = (ChartFactor) marginal;
			ParseChart chart = cf.getChart();
			if (!chart.getOutsideCalculated()) {
				Map<Production, Double> rootMarginal = cf.getRootMarginal();
				Map<Production, Double> rootInside = chart.getInsideEntries(0, chart.chartSize() - 1);
				Map<Production, Double> rootOutside = new HashMap<Production, Double>();
				for (Production p : rootMarginal.keySet()) {
					if (rootInside.containsKey(p)) {
						rootOutside.put(p, rootMarginal.get(p) / rootInside.get(p));
					}
				}
				chart = parser.parseOutsideMarginal(chart, rootOutside);
			}
			// Update binary/terminal rule counts
			productionDist.incrementBinaryCpts(chart.getBinaryRuleExpectations(), count / chart.getPartitionFunction());
			productionDist.incrementTerminalCpts(chart.getTerminalRuleExpectations(), count / chart.getPartitionFunction());
		} else {
			throw new RuntimeException();
		}
	}


	/////////////////////////////////////////////////////////////
	// Inference stuff
	////////////////////////////////////////////////////////////

	/**
	 * Re-implement sum-product to use the CFG parser effectively.
	 */
	public DiscreteFactor sumProduct(List<DiscreteFactor> inboundMessages, Collection<Integer> variablesToRetain) {
		throw new UnsupportedOperationException();
		/*
	assert variablesToRetain.size() <= 2;

	List<Factor> parentFactors = new ArrayList<Factor>();
	List<Factor> childFactors = new ArrayList<Factor>();

	for (Factor message : inboundMessages) {
	    // This test shouldn't actually be necessary, but I'm not sure how to avoid it
	    // at the moment.
	    assert message.getVarNums().size() == 1;	    
	    if (message.getVarNums().contains(parentVarNum)) {
		parentFactors.add(message);
	    } 

	    if (message.getVarNums().contains(childVarNum)) {
		childFactors.add(message);
	    }
	}

	// Convert from factors to a distribution that the CFG parser understands.
	TableFactor parentProductFactor = TableFactor.productFactor(parentFactors);
	TableFactor childProductFactor = TableFactor.productFactor(childFactors);

	Map<List<Production>, Double> childDist = new HashMap<List<Production>, Double>();
	Map<Production, Double> parentDist = new HashMap<Production, Double>();

	Iterator<Assignment> parentIter = parentProductFactor.outcomeIterator();
	while (parentIter.hasNext()) {
	    Assignment a = parentIter.next();
	    Production val = parentVar.getValue(a.getVarValuesInKeyOrder().get(0));
	    parentDist.put(val, parentProductFactor.getUnnormalizedProbability(a));
	}

	Iterator<Assignment> childIter = childProductFactor.outcomeIterator();
	while (childIter.hasNext()) {
	    Assignment a = childIter.next();
	    List<Production> val = childVar.getValue(a.getVarValuesInKeyOrder().get(0));
	    childDist.put(val, childProductFactor.getUnnormalizedProbability(a));
	}

	ParseChart c = parser.parseMarginal(childDist, parentDist);

	return new ChartFactor(c, parentVar, childVar, parentVarNum, childVarNum);
		 */
	}

	public DiscreteFactor product(List<DiscreteFactor> factors) {
		for (DiscreteFactor f : factors) {
			assert f.getVars().size() == 1 && f.getVars().containsVariableNum(parentVarNum);
		}

		if (multipliedWith == null) {
			return new CfgFactor(parentVar, childVar, parentVarNum, childVarNum, productionDist,
					parser, TableFactor.productFactor(factors));
		} else {
			List<DiscreteFactor> allFactors = new ArrayList<DiscreteFactor>(factors);
			allFactors.add(multipliedWith);
			return new CfgFactor(parentVar, childVar, parentVarNum, childVarNum, productionDist,
					parser, TableFactor.productFactor(allFactors));
		}
	}

	public DiscreteFactor conditional(Assignment a) {
		VariableNumMap intersection = getVars().intersection(new HashSet<Integer>(a.getVarNumsSorted()));

		if (intersection.size() == 0) {
			return this;
		}

		TableFactor returnFactor = new TableFactor(getVars());
		Assignment subAssignment = a.subAssignment(intersection.getVariableNums());
		if (intersection.size() == getVars().size()) {
			returnFactor.setWeight(subAssignment, 
					getUnnormalizedProbability(subAssignment));
			return returnFactor;
		}

		// Use the parser to get a conditional distribution.
		if (intersection.containsVariableNum(parentVarNum)) {
			// Can't handle this case.
			throw new RuntimeException("Cannot condition on parent");
		}

		// Child is being conditioned on.
		List<Production> childVarValue = (List<Production>) a.getVarValue(childVarNum);
		ParseChart c = parser.parseInsideMarginal(childVarValue, true);

		Map<List<Production>, Double> childDist = new HashMap<List<Production>, Double>();
		childDist.put(childVarValue, 1.0);

		ChartFactor chartFactor = new ChartFactor(c, parentVar, childVar, parentVarNum, childVarNum, childDist);
		if (multipliedWith != null) {
			return chartFactor.sumProduct(Arrays.asList(new DiscreteFactor[] {multipliedWith}),
					Arrays.asList(new Integer[] {parentVarNum, childVarNum}));
		}
		return chartFactor;
	}

	public DiscreteFactor marginalize(Collection<Integer> varNumsToEliminate) {
		throw new UnsupportedOperationException();
		/*
	Set<Integer> varsToRetain = new HashSet<Integer>();
	varsToRetain.addAll(getVarNums());
	varsToRetain.removeAll(varNumsToEliminate);

	if (varsToRetain.size() == 2) {
	    return this;
	}

	return sumProduct(Collections.EMPTY_LIST, varsToRetain);
		 */
	}

	//////////////////////////////////////////
	// Misc 
	//////////////////////////////////////////

	public String toString() {
		return parser.toString();
	}
}

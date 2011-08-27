package com.jayantkrish.jklol.cfg;

import java.util.Map;

import com.jayantkrish.jklol.models.bayesnet.SufficientStatistics;

/**
 * CptProductionDistribution is the standard generative model
 * for pCFGs. Each production has a conditional probability distribution
 * over its children.
 *
 * A CptProductionDistribution also maintains sufficient statistics for each rule.
 */
public interface CptProductionDistribution extends ProductionDistribution, SufficientStatistics {

	/**
	 * Updates sufficient statistics of the binary CPTs.
	 */ 
	public void incrementBinaryCpts(Map<BinaryProduction, Double> binaryRuleExpectations, double count);

	/**
	 * Updates sufficient statistics of terminal CPTs.
	 */
	public void incrementTerminalCpts(Map<TerminalProduction, Double> terminalRuleExpectations, double count);
	
	/**
	 * Returns a copy of {@code this} distribution with an 
	 * empty sufficient statistics vector. All event counts
	 * in the returned vector are 0.
	 */
	public CptProductionDistribution emptyCopy();
}

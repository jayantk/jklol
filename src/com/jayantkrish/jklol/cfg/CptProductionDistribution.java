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
	 * Update sufficient statistics of the binary CPTs 
	 */ 
	public void incrementBinaryCpts(Map<BinaryProduction, Double> binaryRuleExpectations, double count);

	/**
	 * Update sufficient statistics of terminal CPTs
	 */
	public void incrementTerminalCpts(Map<TerminalProduction, Double> terminalRuleExpectations, double count);
	
	/**
	 * 
	 */
	public void CptProductionDistribution newCopy();
}

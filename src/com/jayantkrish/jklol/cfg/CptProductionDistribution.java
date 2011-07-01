package com.jayantkrish.jklol.cfg;

import java.util.Map;

/**
 * CptProductionDistribution is the standard generative model
 * for pCFGs. Each production has a conditional probability distribution
 * over its children.
 *
 * A CptProductionDistribution also maintains sufficient statistics for each rule.
 */
public interface CptProductionDistribution extends ProductionDistribution {

	/**
	 * Uniformly smooths the CPTs of all production rules.
	 */
	public void addUniformSmoothing(double virtualCount);

	/**
	 * Delete all sufficient statistics accumulated and stored in the CPTs.
	 */ 
	public void clearCpts();

	/**
	 * Update sufficient statistics of the binary CPTs 
	 */ 
	public void incrementBinaryCpts(Map<BinaryProduction, Double> binaryRuleExpectations, double count);

	/**
	 * Update sufficient statistics of terminal CPTs
	 */
	public void incrementTerminalCpts(Map<TerminalProduction, Double> terminalRuleExpectations, double count);


}

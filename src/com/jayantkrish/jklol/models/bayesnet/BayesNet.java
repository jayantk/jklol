package com.jayantkrish.jklol.models.bayesnet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Represents a Bayesian Network, a directed graphical model parameterized by
 * conditional probability tables. The direction of the model is implicitly
 * represented in the {@link CptFactor}s contained in a {@code BayesNet}.
 * {@code BayesNet} actually allows a combination of directed and undirected
 * factors; however, the undirected factors must be constant (i.e., not
 * parameterized).
 * 
 * <p>
 * As the parameters of Bayesian Networks can be trivially estimated from the
 * occurrence counts of certain outcomes, {@code BayesNet} is parameterized by a
 * {@code SufficientStatistics} vector.
 * 
 * @author jayantk
 */
public class BayesNet extends FactorGraph {

  private List<CptFactor> cptFactors;

  public BayesNet(FactorGraph factorGraph, List<CptFactor> cptFactors) {
    super(factorGraph);
    this.cptFactors = new ArrayList<CptFactor>(cptFactors);
  }

  /**
   * Gets the factors in this model which are parameterized by conditional
   * probability tables.
   * 
   * @return
   */
  public List<CptFactor> getCptFactors() {
    return Collections.unmodifiableList(cptFactors);
  }

  /**
   * Gets the parameters of this {@code BayesNet} as a vector of sufficient
   * statistics for parent/child outcomes. Note that the statistics are
   * unnormalized -- within a CPT, the sum of child probabilities add up to the
   * parent probability, which is not necessarily 1. Multiple
   * {@code SufficientStatistics} may result in the same probability
   * distribution.
   * 
   * @return
   */
  public SufficientStatistics getCurrentParameters() {
    List<SufficientStatistics> sufficientStatistics = Lists.newArrayList();
    for (CptFactor factor : getCptFactors()) {
      sufficientStatistics.add(factor.getCurrentParameters());
    }
    return new ListSufficientStatistics(sufficientStatistics);
  }

  /**
   * Sets the parameters of this {@code BayesNet} from a vector of sufficient
   * statistics.
   * 
   * @return
   */
  public void setCurrentParameters(SufficientStatistics newParameters) {
    Preconditions.checkArgument(newParameters instanceof ListSufficientStatistics);
    List<SufficientStatistics> newStatistics = ((ListSufficientStatistics) newParameters).getStatistics();
    Preconditions.checkArgument(newStatistics.size() == cptFactors.size());
    for (int i = 0; i < cptFactors.size(); i++) {
      cptFactors.get(i).setCurrentParameters(newStatistics.get(i));
    }
  }

  /**
   * Gets a new vector of sufficient statistics. The returned vector may be
   * passed as an argument to methods like
   * {@link #setCurrentParameters(SufficientStatistics)}, which require a
   * {@code SufficientStatistics}. All outcome counts in the returned vector are
   * 0.
   * 
   * @return
   */
  public SufficientStatistics getNewSufficientStatistics() {
    List<SufficientStatistics> sufficientStatistics = Lists.newArrayList();
    for (CptFactor factor : getCptFactors()) {
      sufficientStatistics.add(factor.getNewSufficientStatistics());
    }
    return new ListSufficientStatistics(sufficientStatistics);
  }

  /**
   * Computes the sufficient statistics associated with the marginal
   * distribution {@code marginals}. {@code count} is the number of times
   * {@code marginals} has been observed, and acts as a multiplier for the
   * sufficient statistics.
   * 
   * @param marginals
   * @param count
   * @return
   */
  public SufficientStatistics computeSufficientStatistics(MarginalSet marginals, double count) {
    List<SufficientStatistics> sufficientStatistics = Lists.newArrayList();
    for (CptFactor factor : getCptFactors()) {
      Factor marginal = marginals.getMarginal(factor.getVars().getVariableNums());
      sufficientStatistics.add(factor.getSufficientStatisticsFromMarginal(
          marginal, count, marginals.getPartitionFunction()));
    }
    return new ListSufficientStatistics(sufficientStatistics);
  }

  /**
   * Computes the sufficient statistics associated with {@code assignment},
   * which is assumed to contain a value for every variable in {@code this}.
   * {@code count} is the number of times {@code assignment} has been observed,
   * and acts as a multiplier for the sufficient statistics.
   * 
   * @param marginals
   * @param count
   * @return
   */
  public SufficientStatistics computeSufficientStatistics(Assignment assignment, double count) {
    List<SufficientStatistics> sufficientStatistics = Lists.newArrayList();
    for (CptFactor factor : getCptFactors()) {
      sufficientStatistics.add(
          factor.getSufficientStatisticsFromAssignment(assignment, count));
    }
    return new ListSufficientStatistics(sufficientStatistics);
  }
}

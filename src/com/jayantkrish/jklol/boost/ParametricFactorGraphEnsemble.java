package com.jayantkrish.jklol.boost;

import java.io.Serializable;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.Factors;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.DynamicVariableSet;
import com.jayantkrish.jklol.models.dynamic.PlateFactor;
import com.jayantkrish.jklol.models.dynamic.ReplicatedFactor;
import com.jayantkrish.jklol.models.dynamic.VariablePattern;
import com.jayantkrish.jklol.models.dynamic.VariablePattern.VariableMatch;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.Pair;

public class ParametricFactorGraphEnsemble implements Serializable {
  private static final long serialVersionUID = 1L;

  private final DynamicFactorGraph baseFactorGraph;

  private final List<BoostingFactorFamily> boostingFamilies;
  private final List<VariablePattern> factorPatterns;
  private final List<Factor> baseFactors;
  private final IndexedList<String> factorNames;

  public ParametricFactorGraphEnsemble(DynamicFactorGraph baseFactorGraph, 
      List<BoostingFactorFamily> boostingFamilies, List<VariablePattern> factorPatterns,
      List<Factor> baseFactors, IndexedList<String> factorNames) {
    this.baseFactorGraph = Preconditions.checkNotNull(baseFactorGraph);
    this.boostingFamilies = Preconditions.checkNotNull(boostingFamilies);
    this.factorPatterns = Preconditions.checkNotNull(factorPatterns);
    this.baseFactors = Preconditions.checkNotNull(baseFactors);
    this.factorNames = Preconditions.checkNotNull(factorNames);
    
    Preconditions.checkState(boostingFamilies.size() == factorPatterns.size());
    Preconditions.checkState(boostingFamilies.size() == baseFactors.size());
    Preconditions.checkState(boostingFamilies.size() == factorNames.size());
  }
  
  public DynamicVariableSet getVariables() {
    return baseFactorGraph.getVariables();
  }

  public SufficientStatisticsEnsemble getNewSufficientStatistics() {
    List<SufficientStatistics> statistics = Lists.newArrayList();
    for (BoostingFactorFamily family : boostingFamilies) {
      statistics.add(family.getNewSufficientStatistics());
    }

    SufficientStatistics fgStatistics = new ListSufficientStatistics(
        factorNames.items(), statistics);
    return new SufficientStatisticsEnsemble(Lists.newArrayList(fgStatistics),
        Lists.newArrayList(1.0));
  }
  
  public FunctionalGradient getNewFunctionalGradient() {
    List<FunctionalGradient> functionalGradientList = Lists.newArrayList();
    for (BoostingFactorFamily family : boostingFamilies) {
      functionalGradientList.add(family.getNewFunctionalGradient());
    }
    return new ListFunctionalGradient(factorNames, functionalGradientList); 
  }

  public DynamicFactorGraph getModelFromParameters(SufficientStatisticsEnsemble parameters) {
    List<SufficientStatistics> ensembleParameters = parameters.getStatistics();
    List<Double> ensembleWeights = parameters.getStatisticWeights();
    List<PlateFactor> plateFactors = Lists.newArrayList();
    for (int i = 0; i < boostingFamilies.size(); i++) {
      BoostingFactorFamily family = boostingFamilies.get(i);

      List<Factor> factors = Lists.newArrayList();
      if (baseFactors.get(i) != null) {
        factors.add(baseFactors.get(i));
      }

      for (int j = 0; j < ensembleParameters.size(); j++) {
        factors.add(family.getModelFromParameters(ensembleParameters.get(j).coerceToList().getStatistics().get(i)));
      }

      VariableNumMap conditionalVars = family.getConditionalVariables();
      Factor result = null;
      if (conditionalVars.size() == 0) {
        // Incorporate ensemble weights.
        List<Factor> reweightedFactors = Lists.newArrayList();
        for (int j = 0; j < factors.size(); j++) {
          DiscreteFactor reweightedFactor = factors.get(j).coerceToDiscrete();
          Tensor logWeights = reweightedFactor.getWeights().elementwiseLog().elementwiseProduct(ensembleWeights.get(j));
          reweightedFactors.add(new TableFactor(reweightedFactor.getVars(), logWeights.elementwiseExp()));
        }
        result = Factors.product(factors);
      } else {
        // Conditional factors must have the ensemble weights incorporated lazily,
        // after conditioning on an input.
        result = new EnsembleConditionalFactor(family.getVariables(), factors, Doubles.toArray(ensembleWeights));
      }

      Preconditions.checkState(result != null);
      plateFactors.add(new ReplicatedFactor(result, factorPatterns.get(i)));
    }

    return baseFactorGraph.addPlateFactors(plateFactors, factorNames.items());
  }

  public void incrementFunctionalGradient(FunctionalGradient gradient, MarginalSet inputMarginals,
      MarginalSet outputMarginals, double count) {
    Preconditions.checkArgument(inputMarginals.getVariables().equals(outputMarginals.getVariables()));

    List<Integer> inputConditionedVariables = inputMarginals.getConditionedValues().getVariableNums();
    List<Integer> outputConditionedVariables = outputMarginals.getConditionedValues().getVariableNums();
    for (int i = 0; i < factorPatterns.size(); i++) {
      VariablePattern pattern = factorPatterns.get(i);
      BoostingFactorFamily family = boostingFamilies.get(i);
      FunctionalGradient familyGradient = ((ListFunctionalGradient) gradient).getGradientList().get(i);
      List<VariableMatch> matches = pattern.matchVariables(inputMarginals.getVariables());
      for (VariableMatch match : matches) {
        Pair<Factor, Assignment> inputTargets = getRegressableFactorForFamily(family, match,
            inputConditionedVariables, inputMarginals);
        Pair<Factor, Assignment> outputTargets = getRegressableFactorForFamily(family, match,
            outputConditionedVariables, outputMarginals);
        
        Preconditions.checkState(inputTargets.getLeft().getVars().equals(outputTargets.getLeft().getVars()));
        Preconditions.checkState(inputTargets.getRight().equals(outputTargets.getRight()));

        Factor regressionTargets = outputTargets.getLeft().add(inputTargets.getLeft().product(-1.0));
        Assignment regressionAssignment = inputTargets.getRight();

        family.incrementGradient(familyGradient, regressionTargets, regressionAssignment);
      }
    }
  }

  /**
   * Converts the marginal distributions in {@code marginals} into a
   * set of regressable weights for {@code family}.
   * 
   * @param family
   * @param match
   * @param conditionedVariables
   * @param marginals
   * @return
   */
  private Pair<Factor, Assignment> getRegressableFactorForFamily(BoostingFactorFamily family, VariableMatch match,
      List<Integer> conditionedVariables, MarginalSet marginals) {
    VariableNumMap marginalVars = match.getMatchedVariables().removeAll(conditionedVariables);
    VariableNumMap fixedVars = match.getMatchedVariables().intersection(conditionedVariables);
    Factor factorMarginal = marginals.getMarginal(marginalVars.getVariableNums())
        .relabelVariables(match.getMappingToTemplate());
    factorMarginal = factorMarginal.product(1.0 / marginals.getPartitionFunction());

    Assignment assignment = marginals.getConditionedValues().intersection(fixedVars)
        .mapVariables(match.getMappingToTemplate().getVariableIndexReplacementMap());

    VariableNumMap outerProductVars = family.getUnconditionalVariables().removeAll(factorMarginal.getVars());
    if (outerProductVars.size() > 0) {
      factorMarginal = factorMarginal.outerProduct(TableFactor.pointDistribution(outerProductVars,
          assignment.intersection(outerProductVars)));
      assignment = assignment.removeAll(outerProductVars.getVariableNums());
    }

    return Pair.of(factorMarginal, assignment);
  }

  public SufficientStatistics projectFunctionalGradient(FunctionalGradient gradient) {
    List<SufficientStatistics> statistics = Lists.newArrayList();
    List<FunctionalGradient> factorGradients = ((ListFunctionalGradient) gradient).getGradientList();
    for (int i = 0; i < factorNames.size(); i++) {
      BoostingFactorFamily factorFamily = boostingFamilies.get(i);
      statistics.add(factorFamily.projectGradient(factorGradients.get(i)));
    }
    return new ListSufficientStatistics(factorNames.items(), statistics);
  }

  public String getParameterDescription(SufficientStatisticsEnsemble parameters) {
    return getParameterDescription(parameters, -1);
  }

  public String getParameterDescription(SufficientStatisticsEnsemble parameters, int numFeatures) {
    List<SufficientStatistics> ensembleParameters = parameters.getStatistics();
    //List<Double> ensembleWeights = parameters.getStatisticWeights();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < ensembleParameters.size(); i++) {
      for (int j = 0; j < boostingFamilies.size(); j++)  {
        BoostingFactorFamily family = boostingFamilies.get(j);
        sb.append(family.getParameterDescription(ensembleParameters.get(i)
            .coerceToList().getStatistics().get(j), numFeatures));
      }
    }
    return sb.toString();
  }
}

package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Arrays;
import java.util.Iterator;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.AbstractParametricFactor;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;

public class ParametricLogicalFormFactor extends AbstractParametricFactor {
  private static final long serialVersionUID = 1L;

  // Distribution over lfVar and otherVars, given wordVar.
  private final VariableNumMap wordVar;
  private final VariableNumMap lfVar;
  private final VariableNumMap otherVars;

  private final VariableNumMap featureVar;
  // Over lfVar and featureVar
  private final Tensor lfFeatures;

  private final VariableNumMap lfTemplateVar;
  // Over lfVar and lfTemplateVar, each lf mapped to
  // exactly 1 template.
  private final Tensor lfToTemplateMap;

  // True/false variable for word skipping. 
  private final VariableNumMap trueFalseVar;
  private final Assignment trueAssignment;
  private final Assignment falseAssignment;
  private final Assignment skipAssignment;

  private final String TEMPLATE_PARAMETER_NAME = "templateParameters"; 
  private final String WORD_LF_PARAMETER_NAME = "wordLfParameters";
  private final String SKIP_PARAMETER_NAME = "skipParameters";
  
  public ParametricLogicalFormFactor(VariableNumMap wordVar, VariableNumMap lfVar, VariableNumMap otherVars,
      VariableNumMap featureVar, DiscreteFactor lfFeatures, VariableNumMap lfTemplateVar, DiscreteFactor lfToTemplateFactor,
      Assignment skipAssignment) {
    super(VariableNumMap.unionAll(wordVar, lfVar, otherVars));
    this.wordVar = wordVar;
    this.lfVar = lfVar;
    this.otherVars = otherVars;

    this.featureVar = featureVar;
    this.lfFeatures = lfFeatures.getWeights();
    
    this.lfTemplateVar = lfTemplateVar;
    this.lfToTemplateMap = lfToTemplateFactor.getWeights();

    int trueFalseVarNum = wordVar.getOnlyVariableNum() + 1;
    this.trueFalseVar = VariableNumMap.singleton(trueFalseVarNum, "true/false",
        new DiscreteVariable("true/false", Arrays.asList("F", "T")));
    this.trueAssignment = trueFalseVar.outcomeArrayToAssignment("T");
    this.falseAssignment = trueFalseVar.outcomeArrayToAssignment("F");
    
    this.skipAssignment = skipAssignment;
  }

  @Override
  public Factor getModelFromParameters(SufficientStatistics parameters) {
    TensorSufficientStatistics templateStats = (TensorSufficientStatistics) parameters
        .coerceToList().getStatisticByName(TEMPLATE_PARAMETER_NAME);
    TensorSufficientStatistics wordLfStats = (TensorSufficientStatistics) parameters
        .coerceToList().getStatisticByName(WORD_LF_PARAMETER_NAME);
    TensorSufficientStatistics skipStats = (TensorSufficientStatistics) parameters
        .coerceToList().getStatisticByName(SKIP_PARAMETER_NAME);

    Tensor skipCounts = skipStats.get();
    Tensor skipTotalCounts = skipCounts.sumOutDimensions(trueFalseVar.getVariableNumsArray());
    Tensor skipProbs = skipCounts.elementwiseProduct(skipTotalCounts.elementwiseInverse());
    
    Tensor templateCounts = templateStats.get();
    Tensor normalization = templateCounts.sumOutDimensions(templateCounts.getDimensionNumbers());
    Tensor templateProbs = templateCounts.elementwiseProduct(1.0 / normalization.getByDimKey());

    Tensor wordLfCounts = wordLfStats.get();
    Tensor wordLfNormalization = wordLfCounts.sumOutDimensions(featureVar.getVariableNumsArray());
    Tensor wordLfFeatureLogProbs = wordLfCounts.elementwiseProduct(wordLfNormalization.elementwiseInverse())
        .elementwiseLog();

    DenseTensorBuilder builder = new DenseTensorBuilder(getVars().getVariableNumsArray(), getVars().getVariableSizes());
    Tensor noSkipProbs = skipProbs.slice(trueFalseVar.getVariableNumsArray(),
        trueFalseVar.assignmentToIntArray(falseAssignment));
    builder.incrementOuterProductWithMultiplier(noSkipProbs, lfToTemplateMap.innerProduct(templateProbs), 1.0);

    int[] curDims = lfFeatures.getDimensionNumbers();
    Tensor relabeled = lfFeatures.relabelDimensions(new int[] {curDims[1] + 1, curDims[1]});
    
    Tensor wordLfProbs = wordLfFeatureLogProbs.matrixInnerProduct(relabeled).elementwiseExp();
    int[] wordLfDims = Arrays.copyOf(wordLfProbs.getDimensionNumbers(), wordLfProbs.getDimensionNumbers().length);
    wordLfDims[wordLfDims.length - 1] = curDims[0];
    Tensor wordLfProbsRelabeled = wordLfProbs.relabelDimensions(wordLfDims);
    
    Tensor result = builder.build().elementwiseProduct(wordLfProbsRelabeled);
    
    TableFactorBuilder resultBuilder = new TableFactorBuilder(getVars(), DenseTensorBuilder.getFactory());
    resultBuilder.incrementWeight(new TableFactor(getVars(), result));
    
    TableFactor skipFactor = new TableFactor(wordVar, skipProbs.slice(trueFalseVar.getVariableNumsArray(),
        trueFalseVar.assignmentToIntArray(trueAssignment)));
    Iterator<Assignment> iter = new AllAssignmentIterator(wordVar);
    while (iter.hasNext()) {
      Assignment a = iter.next();
      resultBuilder.setWeight(a.union(skipAssignment), skipFactor.getUnnormalizedProbability(a));
    }

    return resultBuilder.build();
  }

  @Override
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics statistics,
      SufficientStatistics currentParameters, Assignment assignment, double count) {

    TensorSufficientStatistics templateStats = (TensorSufficientStatistics) statistics.coerceToList().getStatisticByName(TEMPLATE_PARAMETER_NAME);
    TensorSufficientStatistics wordLfStats = (TensorSufficientStatistics) statistics.coerceToList().getStatisticByName(WORD_LF_PARAMETER_NAME);
    TensorSufficientStatistics skipStats = (TensorSufficientStatistics) statistics.coerceToList().getStatisticByName(SKIP_PARAMETER_NAME);

    Assignment lfAssignment = assignment.intersection(lfVar.getVariableNumsArray());
    Assignment wordAssignment = assignment.intersection(wordVar.getVariableNumsArray());
    if (lfAssignment.equals(skipAssignment)) {
      skipStats.incrementFeature(wordAssignment.union(trueAssignment), count);
    } else {
      skipStats.incrementFeature(wordAssignment.union(falseAssignment), count);
      
      Tensor templateCounts = lfToTemplateMap.slice(lfVar.getVariableNumsArray(),
          lfVar.assignmentToIntArray(lfAssignment));
      templateStats.increment(templateCounts, count);

      Tensor wordIndicator = TableFactor.pointDistribution(wordVar, wordAssignment).getWeights();
      Tensor featureCounts = lfFeatures.slice(lfVar.getVariableNumsArray(),
          lfVar.assignmentToIntArray(lfAssignment));
      wordLfStats.incrementOuterProduct(wordIndicator, featureCounts, count);
    }
  }

  @Override
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics statistics,
      SufficientStatistics currentParameters, Factor marginal, Assignment conditionalAssignment,
      double count, double partitionFunction) {
    Preconditions.checkArgument(conditionalAssignment.contains(wordVar.getOnlyVariableNum()));
    Preconditions.checkArgument(marginal.getVars().containsAll(otherVars.union(lfVar)));
    
    TensorSufficientStatistics templateStats = (TensorSufficientStatistics) statistics.coerceToList().getStatisticByName(TEMPLATE_PARAMETER_NAME);
    TensorSufficientStatistics wordLfStats = (TensorSufficientStatistics) statistics.coerceToList().getStatisticByName(WORD_LF_PARAMETER_NAME);
    TensorSufficientStatistics skipStats = (TensorSufficientStatistics) statistics.coerceToList().getStatisticByName(SKIP_PARAMETER_NAME);
    
    Assignment wordAssignment = conditionalAssignment.intersection(wordVar);
    double skipProb = marginal.getUnnormalizedProbability(skipAssignment) / partitionFunction;
    double noSkipProb = 1.0 - skipProb;

    skipStats.incrementFeature(wordAssignment.union(trueAssignment), skipProb * count);
    skipStats.incrementFeature(wordAssignment.union(falseAssignment), noSkipProb * count);

    Tensor templateCounts = lfToTemplateMap.innerProduct(marginal.coerceToDiscrete().getWeights());
    templateStats.increment(templateCounts, count / noSkipProb);

    Tensor wordIndicator = TableFactor.pointDistribution(wordVar, wordAssignment).getWeights();
    Tensor featureCounts = lfFeatures.innerProduct(marginal.coerceToDiscrete().getWeights());
    wordLfStats.incrementOuterProduct(wordIndicator, featureCounts, count / noSkipProb);
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    SufficientStatistics templateStats = TensorSufficientStatistics.createDense(lfTemplateVar);
    SufficientStatistics wordLfStats = TensorSufficientStatistics.createDense(featureVar.union(wordVar));
    SufficientStatistics skipStats = TensorSufficientStatistics.createDense(trueFalseVar.union(wordVar));
    
    return new ListSufficientStatistics(Arrays.asList(TEMPLATE_PARAMETER_NAME, WORD_LF_PARAMETER_NAME, SKIP_PARAMETER_NAME),
        Arrays.asList(templateStats, wordLfStats, skipStats));
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    // TODO Auto-generated method stub
    return null;
  }

}

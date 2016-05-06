package com.jayantkrish.jklol.experiments.p3;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.DefaultCcgFeatureFactory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.lexicon.ParametricFactorLexiconScorer;
import com.jayantkrish.jklol.ccg.lexicon.ParametricLexiconScorer;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;

public class P3CcgFeatureFactory extends DefaultCcgFeatureFactory {
  
  private final Collection<LexiconEntry> entityEntries;
  
  private static final String ENTITY_NAME_MATCH = "entity-name-match";
  
  public P3CcgFeatureFactory(boolean usePosFeatures, boolean allowWordSkipping,
      Collection<LexiconEntry> entityEntries) {
    super(usePosFeatures, allowWordSkipping);
    this.entityEntries = entityEntries;
  }
  
  @Override
  public List<ParametricLexiconScorer> getLexiconScorers(VariableNumMap terminalWordVar,
      VariableNumMap ccgCategoryVar, VariableNumMap terminalPosVar,
      VariableNumMap terminalSyntaxVar) {
    List<ParametricLexiconScorer> scorers = super.getLexiconScorers(terminalWordVar,
        ccgCategoryVar, terminalPosVar, terminalSyntaxVar);
    
    if (entityEntries != null) {
      VariableNumMap scorerVars = terminalWordVar.union(ccgCategoryVar);
      DiscreteVariable featureDictionary = new DiscreteVariable(ENTITY_NAME_MATCH,
          Arrays.asList(ENTITY_NAME_MATCH));
      VariableNumMap featureVar = VariableNumMap.singleton(
          Ints.max(scorerVars.getVariableNumsArray()) + 1, "lexicon features", featureDictionary);
      TableFactorBuilder builder = new TableFactorBuilder(scorerVars.union(featureVar),
          SparseTensorBuilder.getFactory());
      VariableNumMap builderVars = builder.getVars();

      for (LexiconEntry entry : entityEntries) {
        List<String> words = entry.getWords();
        CcgCategory category = entry.getCategory();
        Assignment a = builderVars.outcomeArrayToAssignment(words, category, ENTITY_NAME_MATCH);        
        builder.incrementWeight(a, words.size());
      }

      DiscreteLogLinearFactor f = new DiscreteLogLinearFactor(terminalWordVar.union(ccgCategoryVar),
          featureVar, builder.build());
      scorers.add(new ParametricFactorLexiconScorer(terminalWordVar, ccgCategoryVar, f));
    }
    return scorers;
  }
}

package com.jayantkrish.jklol.ccg.lexicon;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.util.Assignment;

public class FactorLexiconScorer implements LexiconScorer {
  private static final long serialVersionUID = 5L;

  private final VariableNumMap vars;
  private final DiscreteFactor weights;
  
  public FactorLexiconScorer(VariableNumMap wordVar, VariableNumMap categoryVar,
      DiscreteFactor weights) {
    Preconditions.checkArgument(wordVar.getOnlyVariableNum() < categoryVar.getOnlyVariableNum());
    this.vars = wordVar.union(categoryVar);
    this.weights = weights;
  }
  
  @Override
  public double getCategoryWeight(int spanStart, int spanEnd, AnnotatedSentence sentence,
      CcgCategory category) {
    List<String> words = sentence.getWords().subList(spanStart, spanEnd + 1);

    Assignment a = vars.outcomeArrayToAssignment(words, category);
    if (vars.isValidAssignment(a)) {
      return weights.getUnnormalizedProbability(a);
    } else {
      return 1.0;
    }
  }
}

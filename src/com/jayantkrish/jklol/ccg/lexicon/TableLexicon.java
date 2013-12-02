package com.jayantkrish.jklol.ccg.lexicon;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.supertag.WordAndPos;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

public class TableLexicon extends AbstractCcgLexicon {
  private static final long serialVersionUID = 1L;

  // Weights and pos tag -> syntactic category mappings for the
  // lexicon (terminals).
  private final VariableNumMap terminalPosVar;
  private final VariableNumMap terminalSyntaxVar;
  private final DiscreteFactor terminalPosDistribution;

  // Weights for word -> syntactic category mappings for
  // the lexicon. This factor is defined over terminalVar
  // and terminalSyntaxVar, and provides backoff weights
  // for different semantic realizations of the same word.
  private final DiscreteFactor terminalSyntaxDistribution;

  public TableLexicon(VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      DiscreteFactor terminalDistribution, VariableNumMap terminalPosVar, VariableNumMap terminalSyntaxVar,
      DiscreteFactor terminalPosDistribution, DiscreteFactor terminalSyntaxDistribution) {
    super(terminalVar, ccgCategoryVar, terminalDistribution, null);

    this.terminalPosVar = Preconditions.checkNotNull(terminalPosVar);
    this.terminalSyntaxVar = Preconditions.checkNotNull(terminalSyntaxVar);
    this.terminalPosDistribution = Preconditions.checkNotNull(terminalPosDistribution);
    VariableNumMap expectedTerminalPosVars = terminalPosVar.union(terminalSyntaxVar);
    Preconditions.checkArgument(expectedTerminalPosVars.equals(terminalPosDistribution.getVars()));

    this.terminalSyntaxDistribution = Preconditions.checkNotNull(terminalSyntaxDistribution);
    VariableNumMap expectedTerminalSyntaxVars = terminalVar.union(terminalSyntaxVar);
    Preconditions.checkArgument(expectedTerminalSyntaxVars.equals(terminalSyntaxDistribution.getVars()));
  }

  @Override
  protected double getCategoryWeight(List<String> originalWords, List<String> preprocessedWords,
      List<String> pos, List<WordAndPos> ccgWordList, List<Tensor> featureVectors, int spanStart,
      int spanEnd, List<String> terminals, CcgCategory category) {
    double posProb = getTerminalPosProbability(pos.get(spanEnd), category.getSyntax());
    double syntaxProb = getTerminalSyntaxProbability(terminals, category.getSyntax());
    return posProb * syntaxProb;
  }

  public double getTerminalPosProbability(String posTag, HeadedSyntacticCategory syntax) {
    Assignment posAssignment = terminalPosVar.outcomeArrayToAssignment(posTag);
    Assignment syntaxAssignment = terminalSyntaxVar.outcomeArrayToAssignment(syntax);
    // TODO: this check should be made unnecessary by preprocessing.
    if (terminalPosVar.isValidAssignment(posAssignment)) {
      return terminalPosDistribution.getUnnormalizedProbability(posAssignment.union(syntaxAssignment));
    } else {
      return 1.0;
    }
  }

  public double getTerminalSyntaxProbability(List<String> terminal, HeadedSyntacticCategory syntax) {
    VariableNumMap terminalVar = getTerminalVar();
    if (terminalVar.isValidAssignment(terminalVar.outcomeArrayToAssignment(terminal))) {
      return terminalSyntaxDistribution.getUnnormalizedProbability(terminal, syntax);
    } else {
      return 1.0;
    }
  }
}

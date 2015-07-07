package com.jayantkrish.jklol.ccg.lexicon;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Scores lexicon entries on the basis of pos/syntactic category 
 * and word/syntactic category pairs.
 * 
 * @author jayant
 */
public class SyntaxLexiconScorer implements LexiconScorer {
  private static final long serialVersionUID = 1L;

  private final VariableNumMap terminalVar;
  private final VariableNumMap terminalPosVar;
  private final VariableNumMap terminalSyntaxVar;

  // Weights for pos tag / syntactic category pairs.
  private final DiscreteFactor terminalPosDistribution;

  // Weights for word / syntactic category pairs.
  // This factor is defined over terminalVar
  // and terminalSyntaxVar, and provides backoff weights
  // for different semantic realizations of the same word.
  private final DiscreteFactor terminalSyntaxDistribution;
  
  public static final String TERMINAL_POS_PARAMETERS = "terminalPos";
  public static final String TERMINAL_SYNTAX_PARAMETERS = "terminalSyntax";
  
  public SyntaxLexiconScorer(VariableNumMap terminalVar, VariableNumMap terminalPosVar,
      VariableNumMap terminalSyntaxVar, DiscreteFactor terminalPosDistribution,
      DiscreteFactor terminalSyntaxDistribution) {
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.terminalPosVar = Preconditions.checkNotNull(terminalPosVar);
    this.terminalSyntaxVar = Preconditions.checkNotNull(terminalSyntaxVar);
    this.terminalPosDistribution = Preconditions.checkNotNull(terminalPosDistribution);
    VariableNumMap expectedTerminalPosVars = terminalPosVar.union(terminalSyntaxVar);
    Preconditions.checkArgument(expectedTerminalPosVars.equals(terminalPosDistribution.getVars()));

    this.terminalSyntaxDistribution = Preconditions.checkNotNull(terminalSyntaxDistribution);
    VariableNumMap expectedTerminalSyntaxVars = terminalVar.union(terminalSyntaxVar);
    Preconditions.checkArgument(expectedTerminalSyntaxVars.equals(terminalSyntaxDistribution.getVars()));
  }
  
  public VariableNumMap getTerminalVar() {
    return terminalVar;
  }
  
  public VariableNumMap getTerminalPosVar() {
    return terminalPosVar;
  }

  public VariableNumMap getTerminalSyntaxVar() {
    return terminalSyntaxVar;
  }

  public DiscreteFactor getTerminalPosDistribution() {
    return terminalPosDistribution;
  }

  public DiscreteFactor getTerminalSyntaxDistribution() {
    return terminalSyntaxDistribution;
  }

  @Override
  public double getCategoryWeight(int spanStart, int spanEnd, AnnotatedSentence sentence,
      List<String> wordSequence, List<String> posTags, CcgCategory category) {
    double posProb = getTerminalPosProbability(posTags.get(posTags.size() - 1), category.getSyntax());
    double syntaxProb = getTerminalSyntaxProbability(wordSequence, category.getSyntax());
    return posProb * syntaxProb;
  }

  private double getTerminalPosProbability(String posTag, HeadedSyntacticCategory syntax) {
    Assignment posAssignment = terminalPosVar.outcomeArrayToAssignment(posTag);
    Assignment syntaxAssignment = terminalSyntaxVar.outcomeArrayToAssignment(syntax);
    if (terminalPosVar.isValidAssignment(posAssignment)) {
      return terminalPosDistribution.getUnnormalizedProbability(posAssignment.union(syntaxAssignment));
    } else {
      return 1.0;
    }
  }

  private double getTerminalSyntaxProbability(List<String> terminal, HeadedSyntacticCategory syntax) {
    VariableNumMap terminalVar = getTerminalVar();
    if (terminalVar.isValidAssignment(terminalVar.outcomeArrayToAssignment(terminal))) {
      return terminalSyntaxDistribution.getUnnormalizedProbability(terminal, syntax);
    } else {
      return 1.0;
    }
  }
}

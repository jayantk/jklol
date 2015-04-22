package com.jayantkrish.jklol.ccg.lexicon;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.supertag.WordAndPos;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

public class TableLexicon extends AbstractCcgLexicon {
  private static final long serialVersionUID = 2L;
  
  private final VariableNumMap terminalVar;
  private final VariableNumMap ccgCategoryVar;
  private final DiscreteFactor terminalDistribution;

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
    super(terminalVar, null);
    
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.ccgCategoryVar = Preconditions.checkNotNull(ccgCategoryVar);
    this.terminalDistribution = Preconditions.checkNotNull(terminalDistribution);
    VariableNumMap expectedTerminalVars = terminalVar.union(ccgCategoryVar);
    Preconditions.checkArgument(expectedTerminalVars.equals(terminalDistribution.getVars()));

    this.terminalPosVar = Preconditions.checkNotNull(terminalPosVar);
    this.terminalSyntaxVar = Preconditions.checkNotNull(terminalSyntaxVar);
    this.terminalPosDistribution = Preconditions.checkNotNull(terminalPosDistribution);
    VariableNumMap expectedTerminalPosVars = terminalPosVar.union(terminalSyntaxVar);
    Preconditions.checkArgument(expectedTerminalPosVars.equals(terminalPosDistribution.getVars()));

    this.terminalSyntaxDistribution = Preconditions.checkNotNull(terminalSyntaxDistribution);
    VariableNumMap expectedTerminalSyntaxVars = terminalVar.union(terminalSyntaxVar);
    Preconditions.checkArgument(expectedTerminalSyntaxVars.equals(terminalSyntaxDistribution.getVars()));
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
  public List<LexiconEntry> getLexiconEntries(List<String> wordSequence) {
    return AbstractCcgLexicon.getLexiconEntriesFromFactor(wordSequence,
        terminalDistribution, terminalVar, ccgCategoryVar);
  }

  @Override
  public double getCategoryWeight(List<String> originalWords, List<String> preprocessedWords,
      List<String> pos, List<WordAndPos> ccgWordList, List<Tensor> featureVectors, int spanStart,
      int spanEnd, List<String> terminals, CcgCategory category) {
    double entryProb = getTerminalProbability(terminals, category);
    double posProb = getTerminalPosProbability(pos.get(spanEnd), category.getSyntax());
    double syntaxProb = getTerminalSyntaxProbability(terminals, category.getSyntax());
    return entryProb * posProb * syntaxProb;
  }
  
  public double getTerminalProbability(List<String> terminal, CcgCategory category) {
    Assignment terminalAssignment = terminalVar.outcomeArrayToAssignment(terminal);
    Assignment categoryAssignment = ccgCategoryVar.outcomeArrayToAssignment(category);
    Assignment a = terminalAssignment.union(categoryAssignment);
    if (terminalDistribution.getVars().isValidAssignment(a)) {
      return terminalDistribution.getUnnormalizedProbability(a);
    } else {
      return 1.0;
    }
  }

  public double getTerminalPosProbability(String posTag, HeadedSyntacticCategory syntax) {
    Assignment posAssignment = terminalPosVar.outcomeArrayToAssignment(posTag);
    Assignment syntaxAssignment = terminalSyntaxVar.outcomeArrayToAssignment(syntax);
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

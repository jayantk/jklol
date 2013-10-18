package com.jayantkrish.jklol.ccg.lexicon;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.chart.CcgChart;
import com.jayantkrish.jklol.ccg.chart.ChartEntry;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;

public class CcgTableLexicon extends AbstractCcgLexicon {

  // Weights and word -> ccg category mappings for the
  // lexicon (terminals).
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

  public CcgTableLexicon(VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      DiscreteFactor terminalDistribution, VariableNumMap terminalPosVar, VariableNumMap terminalSyntaxVar,
      DiscreteFactor terminalPosDistribution, DiscreteFactor terminalSyntaxDistribution) {
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.ccgCategoryVar = Preconditions.checkNotNull(ccgCategoryVar);
    this.terminalDistribution = Preconditions.checkNotNull(terminalDistribution);
    VariableNumMap expectedTerminalVars = VariableNumMap.unionAll(terminalVar, ccgCategoryVar);
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
  
  @Override
  public VariableNumMap getTerminalVar() {
    return terminalVar;
  }

  @Override
  public VariableNumMap getTerminalPosVar() {
    return terminalPosVar;
  }

  /**
   * Initializes the parse chart with entries from the CCG lexicon for
   * {@code terminals}.
   * 
   * @param terminals
   * @param chart
   */
  @Override
  public void initializeChartTerminals(List<String> terminals, List<String> posTags, CcgChart chart,
      CcgParser parser) {
    initializeChartWithDistribution(terminals, posTags, chart, terminalVar, ccgCategoryVar,
        terminalDistribution, true, parser);
  }
  
  public boolean isPossibleLexiconEntry(List<String> originalWords, List<String> posTags, HeadedSyntacticCategory category) {
    Preconditions.checkArgument(originalWords.size() == posTags.size());

    List<String> words = preprocessInput(originalWords);
    List<List<String>> terminalOutcomes = Lists.newArrayList();
    terminalOutcomes.add(words);
    if (words.size() == 1) {
      List<String> posTagBackoff = preprocessInput(Arrays.asList(CcgLexicon.UNKNOWN_WORD_PREFIX + posTags.get(0)));
      terminalOutcomes.add(posTagBackoff);
    }

    for (List<String> terminalOutcome : terminalOutcomes) {
      if (terminalVar.isValidOutcomeArray(terminalOutcome)) {
        Assignment assignment = terminalVar.outcomeArrayToAssignment(terminalOutcome);

        Iterator<Outcome> iterator = terminalDistribution.outcomePrefixIterator(assignment);
        while (iterator.hasNext()) {
          Outcome bestOutcome = iterator.next();
          CcgCategory lexicalEntry = (CcgCategory) bestOutcome.getAssignment().getValue(
              ccgCategoryVar.getOnlyVariableNum());
          if (lexicalEntry.getSyntax().equals(category)) {
            return true;
          }
        }
      }
    }
    // System.out.println("No such lexicon entry: " + words + " -> " +
    // category);
    return false;
  }

  private static List<String> preprocessInput(List<String> terminals) {
    List<String> preprocessedTerminals = Lists.newArrayList();
    for (String terminal : terminals) {
      preprocessedTerminals.add(terminal.toLowerCase());
    }
    return preprocessedTerminals;
  }

  /**
   * This method is a hack.
   * 
   * @param terminals
   * @param posTags
   * @param chart
   * @param terminalVar
   * @param ccgCategoryVar
   * @param terminalDistribution
   * @param useUnknownWords
   * @param parser
   */
  public void initializeChartWithDistribution(List<String> terminals, List<String> posTags, CcgChart chart,
      VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      DiscreteFactor terminalDistribution, boolean useUnknownWords, CcgParser parser) {
    Preconditions.checkArgument(terminals.size() == posTags.size());

    List<String> preprocessedTerminals = preprocessInput(terminals);
    for (int i = 0; i < preprocessedTerminals.size(); i++) {
      for (int j = i; j < preprocessedTerminals.size(); j++) {
        List<String> terminalValue = preprocessedTerminals.subList(i, j + 1);
        String posTag = posTags.get(j);
        int numAdded = addChartEntriesForTerminal(terminalValue, posTag, i, j, chart,
            terminalVar, ccgCategoryVar, terminalDistribution, parser);
        if (numAdded == 0 && i == j && useUnknownWords) {
          // Backoff to POS tags if the input is unknown.
          terminalValue = preprocessInput(Arrays.asList(CcgLexicon.UNKNOWN_WORD_PREFIX + posTags.get(i)));
          addChartEntriesForTerminal(terminalValue, posTag, i, j, chart, terminalVar,
              ccgCategoryVar, terminalDistribution, parser);
        }
      }
    }
  }

  private int addChartEntriesForTerminal(List<String> terminalValue, String posTag,
      int spanStart, int spanEnd, CcgChart chart, VariableNumMap terminalVar,
      VariableNumMap ccgCategoryVar, DiscreteFactor terminalDistribution, CcgParser parser) {
    int ccgCategoryVarNum = ccgCategoryVar.getOnlyVariableNum();
    Assignment assignment = terminalVar.outcomeArrayToAssignment(terminalValue);
    if (!terminalVar.isValidAssignment(assignment)) {
      return 0;
    }

    Iterator<Outcome> iterator = terminalDistribution.outcomePrefixIterator(assignment);
    int numEntries = 0;
    while (iterator.hasNext()) {
      Outcome bestOutcome = iterator.next();
      CcgCategory category = (CcgCategory) bestOutcome.getAssignment().getValue(ccgCategoryVarNum);

      // Look up how likely this syntactic entry is to occur with
      // this part of speech.
      double posProb = getTerminalPosProbability(posTag, category.getSyntax());
      double syntaxProb = getTerminalSyntaxProbability(terminalValue, category.getSyntax());

      // Add all possible chart entries to the ccg chart.
      ChartEntry entry = parser.ccgCategoryToChartEntry(terminalValue, category, spanStart, spanEnd);
      chart.addChartEntryForSpan(entry, bestOutcome.getProbability() * posProb * syntaxProb,
          spanStart, spanEnd, parser.getSyntaxVarType());
      numEntries++;
    }
    chart.doneAddingChartEntriesForSpan(spanStart, spanEnd);
    return numEntries;
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
    if (terminalVar.isValidAssignment(terminalVar.outcomeArrayToAssignment(terminal))) {
      return terminalSyntaxDistribution.getUnnormalizedProbability(terminal, syntax);
    } else {
      return 1.0;
    }
  }
  
    /**
   * Gets the possible lexicon entries for {@code wordSequence} that
   * can be used in this parser. The returned entries do not include
   * lexicon entries for unknown words, which may occur in the parse
   * if {@code wordSequence} is unrecognized.
   * 
   * @param wordSequence
   * @return
   */
  public List<LexiconEntry> getLexiconEntries(List<String> wordSequence) {
    return getLexiconEntriesFromFactor(wordSequence, terminalDistribution, terminalVar, ccgCategoryVar);
  }
  
  public List<LexiconEntry> getLexiconEntriesWithUnknown(List<String> originalWords, List<String> posTags) {
    Preconditions.checkArgument(originalWords.size() == posTags.size());
    List<String> words = preprocessInput(originalWords);    
    if (terminalVar.isValidOutcomeArray(words)) {
      return getLexiconEntries(words);
    } else if (words.size() == 1) {
      List<String> posTagBackoff = preprocessInput(Arrays.asList(CcgLexicon.UNKNOWN_WORD_PREFIX + posTags.get(0)));
      return getLexiconEntries(posTagBackoff);
    } else {
      return Collections.emptyList();
    }
  }

  /**
   * Gets the possible lexicon entries for {@code wordSequence} from
   * {@code terminalDistribution}, a distribution over CCG categories
   * given word sequences.
   * 
   * @param wordSequence
   * @param terminalDistribution
   * @param terminalVar
   * @param ccgCategoryVar
   * @return
   */
  public static List<LexiconEntry> getLexiconEntriesFromFactor(List<String> wordSequence,
      DiscreteFactor terminalDistribution, VariableNumMap terminalVar, VariableNumMap ccgCategoryVar) {
    List<LexiconEntry> lexiconEntries = Lists.newArrayList();
    if (terminalVar.isValidOutcomeArray(wordSequence)) {
      Assignment assignment = terminalVar.outcomeArrayToAssignment(wordSequence);

      Iterator<Outcome> iterator = terminalDistribution.outcomePrefixIterator(assignment);
      while (iterator.hasNext()) {
        Outcome bestOutcome = iterator.next();
        CcgCategory ccgCategory = (CcgCategory) bestOutcome.getAssignment().getValue(
            ccgCategoryVar.getOnlyVariableNum());

        lexiconEntries.add(new LexiconEntry(wordSequence, ccgCategory));
      }
    }
    return lexiconEntries;
  }
}

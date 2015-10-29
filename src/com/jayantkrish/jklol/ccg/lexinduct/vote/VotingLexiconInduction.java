package com.jayantkrish.jklol.ccg.lexinduct.vote;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgBeamSearchInference;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgLoglikelihoodOracle;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.LexiconEntryInfo;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;

/**
 * Implementation of the global voting lexicon induction
 * algorithm from:
 * 
 * Learning Compact Lexicons for CCG Semantic Parsing
 * Yoav Artzi, Dipanjan Das and Slav Petrov. EMNLP 2014.
 * 
 * @author jayantk
 *
 */
public class VotingLexiconInduction {

  private final int iterations;
  private final double l2Regularization;
  private final double initialStepSize;

  private final CcgBeamSearchInference inference;
  private final ExpressionComparator comparator;
  
  private final VotingStrategy voter;

  public VotingLexiconInduction(int iterations, double l2Regularization, double initialStepSize,
      CcgBeamSearchInference inference, ExpressionComparator comparator,
      VotingStrategy voter) {
    this.iterations = iterations;
    this.l2Regularization = l2Regularization;
    this.initialStepSize = initialStepSize;
    this.inference = inference;
    this.comparator = comparator;
    this.voter = voter;
  }

  public ParserInfo train(LexiconInductionCcgParserFactory factory, Genlex genlex,
      Collection<LexiconEntry> initialLexicon, List<CcgExample> examples) {
    Set<LexiconEntry> currentLexicon = Sets.newHashSet(initialLexicon);
    SufficientStatistics currentParameters = createParser(factory, null, currentLexicon).getParameters();

    for (int i = 0; i < iterations; i++) {
      List<Set<LexiconEntry>> exampleProposals = Lists.newArrayList();
      for (int j = 0; j < examples.size(); j++) {
        exampleProposals.add(genEntries(examples.get(j), currentParameters, currentLexicon,
            genlex, factory));
      }
      
      System.out.println(i + " PRE-VOTE: " + currentLexicon);
      currentLexicon = voter.vote(currentLexicon, exampleProposals);
      System.out.println(i + " VOTED: " + currentLexicon);
      ParserInfo parserInfo = createParser(factory, currentParameters, currentLexicon);
      currentParameters = parserInfo.getParameters();
      CcgParser parser = parserInfo.getParser();
      ParametricCcgParser family = parserInfo.getFamily();
      SufficientStatistics gradient = family.getNewSufficientStatistics();
      
      Set<LexiconEntry> usedEntries = Sets.newHashSet();
      for (int j = 0; j < examples.size(); j++) {
        CcgExample example = examples.get(j);
        List<CcgParse> parses = inference.beamSearch(parser, example.getSentence(), null, null);
        System.out.println(example.getSentence());
        for (CcgParse parse : parses){
          System.out.println("   " + parse.getLogicalForm());
        }
        
        List<CcgParse> correctParses = CcgLoglikelihoodOracle.filterParsesByLogicalForm(
            example.getLogicalForm(), comparator, parses);
        List<CcgParse> correctMaxParses = filterToMaxScoring(correctParses);
        
        // Track which lexicon entries are used in order to prune
        // entries that are unused across the whole data set. 
        usedEntries.addAll(getLexiconEntriesFromParses(correctMaxParses));

        if (correctParses.size() == 0) {
          // Don't update the gradient if we have a search error.
          continue;
        }

        // Increment the gradient as if this were optimizing loglikelihood: 
        AnnotatedSentence sentence = example.getSentence();
        // Subtract the unconditional expected feature counts.
        double unconditionalPartitionFunction = getPartitionFunction(parses);
        for (CcgParse parse : parses) {
          family.incrementSufficientStatistics(gradient, currentParameters, sentence, parse, -1.0 * 
              parse.getSubtreeProbability() / unconditionalPartitionFunction);
        }
        // Add conditional expected feature counts.
        double conditionalPartitionFunction = getPartitionFunction(correctParses);
        for (CcgParse parse : correctParses) {
          family.incrementSufficientStatistics(gradient, currentParameters, sentence, parse,
              parse.getSubtreeProbability() / conditionalPartitionFunction);
        }
      }
      
      // This update is currently using a fixed step size, but could probably
      // use a decaying step size.
      double currentStepSize = initialStepSize;
      currentParameters.multiply(1.0 - (currentStepSize * l2Regularization));
      currentParameters.increment(gradient, currentStepSize);

      // Keep all of the lexicon entries that were used in at least one example's parse.
      currentLexicon.retainAll(usedEntries);
    }
    
    return createParser(factory, currentParameters, currentLexicon);
  }
  
  private Set<LexiconEntry> genEntries(CcgExample example,
      SufficientStatistics currentParameters, Collection<LexiconEntry> currentLexicon,
      Genlex genlex, LexiconInductionCcgParserFactory factory) {
    // System.out.println("genEntries: " + example.getSentence() + " " + example.getLogicalForm());

    Set<LexiconEntry> exampleLexicon = Sets.newHashSet(currentLexicon);
    exampleLexicon.addAll(genlex.genlex(example));

    // Generate the set of max-scoring parses.
    CcgParser currentParser = createParser(factory, currentParameters, exampleLexicon).getParser();
    List<CcgParse> parses = inference.beamSearch(currentParser, example.getSentence(), null, null);
    List<CcgParse> correctParses = CcgLoglikelihoodOracle.filterParsesByLogicalForm(example.getLogicalForm(),
        comparator, parses);
    
    /*
    for (CcgParse parse : parses) {
      System.out.println("  " + parse.getLogicalForm());
    }
    System.out.println(correctParses);
     */

    List<CcgParse> correctMaxParses = filterToMaxScoring(correctParses);

    return getLexiconEntriesFromParses(correctMaxParses);
  }

  /**
   * Creates a CCG parser given parameters and a lexicon.
   * 
   * @param factory
   * @param currentParameters
   * @param currentLexicon
   * @return
   */
  private ParserInfo createParser(LexiconInductionCcgParserFactory factory,
      SufficientStatistics currentParameters, Collection<LexiconEntry> currentLexicon) {
    ParametricCcgParser family = factory.getParametricCcgParser(currentLexicon);
    SufficientStatistics newParameters = family.getNewSufficientStatistics();
    
    if (currentParameters != null) {
      newParameters.transferParameters(currentParameters);
    }

    return new ParserInfo(currentLexicon, family, newParameters, family.getModelFromParameters(newParameters));
  }

  /**
   * Returns the parses from {@code parses} whose scores are
   * equal to the highest score.
   * 
   * @param parses
   * @return
   */
  private List<CcgParse> filterToMaxScoring(List<CcgParse> parses) {
    List<CcgParse> maxParses = Lists.newArrayList();
    if (parses.size() > 0) {
      double bestScore = parses.get(0).getSubtreeProbability();
      for (CcgParse parse : parses) {
        if (parse.getSubtreeProbability() >= bestScore) {
          maxParses.add(parse);
        }
      }
    }
    return maxParses;
  }

  /**
   * Gets all of the lexicon entries used in {@code parses}.
   * 
   * @param parses
   * @return
   */
  private Set<LexiconEntry> getLexiconEntriesFromParses(Collection<CcgParse> parses) {
    // Generate candidate lexicon entries from the correct max parses.
    Set<LexiconEntry> candidateEntries = Sets.newHashSet();
    for (CcgParse correctMaxParse : parses) {
      for (LexiconEntryInfo info : correctMaxParse.getSpannedLexiconEntries()) {
        CcgCategory category = info.getCategory();
        List<String> words = (List<String>) info.getLexiconTrigger();
        candidateEntries.add(new LexiconEntry(words, category));
      }
    }
    return candidateEntries;
  }

  /**
   * Gets the sum total probability assigned to all parses.
   * 
   * @param parses
   * @return
   */
  private double getPartitionFunction(List<CcgParse> parses) {
    double partitionFunction = 0.0;
    for (CcgParse parse : parses) {
      partitionFunction += parse.getSubtreeProbability();
    }
    return partitionFunction;
  }

  
  public static class ParserInfo {
    private final Set<LexiconEntry> lexicon;
    private final ParametricCcgParser family;
    private final SufficientStatistics parameters;
    private final CcgParser parser;

    public ParserInfo(Collection<LexiconEntry> lexicon, ParametricCcgParser family,
        SufficientStatistics parameters, CcgParser parser) {
      this.lexicon = Sets.newHashSet(lexicon);
      this.family = family;
      this.parameters = parameters;
      this.parser = parser;
    }
    
    public Set<LexiconEntry> getLexiconEntries() {
      return lexicon;
    }

    public ParametricCcgParser getFamily() {
      return family;
    }

    public SufficientStatistics getParameters() {
      return parameters;
    }

    public CcgParser getParser() {
      return parser;
    }
  }  
}

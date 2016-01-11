package com.jayantkrish.jklol.ccg.lexinduct.vote;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgCkyInference;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgLoglikelihoodOracle;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.LexiconEntryInfo;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lexicon.SkipLexicon.SkipTrigger;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.parallel.MapReduceConfiguration;
import com.jayantkrish.jklol.parallel.MapReduceExecutor;
import com.jayantkrish.jklol.parallel.Mapper;
import com.jayantkrish.jklol.training.LogFunction;

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

  private final CcgCkyInference inference;
  private final ExpressionComparator comparator;
  
  private final VotingStrategy voter;

  public VotingLexiconInduction(int iterations, double l2Regularization, double initialStepSize,
      CcgCkyInference inference, ExpressionComparator comparator,
      VotingStrategy voter) {
    this.iterations = iterations;
    this.l2Regularization = l2Regularization;
    this.initialStepSize = initialStepSize;
    this.inference = inference;
    this.comparator = comparator;
    this.voter = voter;
  }

  public ParserInfo train(LexiconInductionCcgParserFactory factory, Genlex genlex,
      Collection<LexiconEntry> initialLexicon, List<CcgExample> examples, LogFunction log) {
    Set<LexiconEntry> currentLexicon = Sets.newHashSet(initialLexicon);
    Set<LexiconEntry> allSeenEntries = Sets.newHashSet(currentLexicon);
    SufficientStatistics currentParameters = createParser(factory, null, currentLexicon).getParameters();

    MapReduceExecutor executor = MapReduceConfiguration.getMapReduceExecutor();
    
    for (int i = 0; i < iterations; i++) {
      log.notifyIterationStart(i);
      
      log.startTimer("gen_entries");
      GenEntriesMapper mapper = new GenEntriesMapper(currentParameters, currentLexicon, genlex, factory, inference, comparator);
      List<Set<LexiconEntry>> exampleProposals = executor.map(examples, mapper);
      log.stopTimer("gen_entries");

      log.startTimer("vote");
      System.out.println(i + " PRE-VOTE: " + currentLexicon);
      currentLexicon = voter.vote(currentLexicon, exampleProposals);
      allSeenEntries.addAll(currentLexicon);
      System.out.println(i + " VOTED: " + currentLexicon);
      log.stopTimer("vote");

      currentParameters = createParser(factory, currentParameters, allSeenEntries).getParameters();
      
      ParserInfo parserInfo = createParser(factory, currentParameters, currentLexicon);
      CcgParser parser = parserInfo.getParser();
      ParametricCcgParser family = parserInfo.getFamily();
      SufficientStatistics parserParameters = parserInfo.getParameters();
      SufficientStatistics gradient = family.getNewSufficientStatistics();
      
      for (LexiconEntry entry : parserInfo.getLexiconEntries()) {
        System.out.println(entry);
      }
      
      log.startTimer("compute_gradient");
      int searchErrors = 0;
      Set<LexiconEntry> usedEntries = Sets.newHashSet();
      for (int j = 0; j < examples.size(); j++) {
        CcgExample example = examples.get(j);
        List<CcgParse> parses = inference.beamSearch(parser, example.getSentence(), null, null);
        System.out.println(example.getSentence());

        List<CcgParse> correctParses = CcgLoglikelihoodOracle.filterParsesByLogicalForm(
            example.getLogicalForm(), comparator, parses);
        List<CcgParse> correctMaxParses = filterToMaxScoring(correctParses);
        System.out.println("correct parses: "+ correctParses.size() + " max: "+ correctMaxParses.size());
        
        // Track which lexicon entries are used in order to prune
        // entries that are unused across the whole data set. 
        usedEntries.addAll(getLexiconEntriesFromParses(correctMaxParses));

        if (correctParses.size() == 0) {
          // Don't update the gradient if we have a search error.
          searchErrors += 1;
          continue;
        }

        // Increment the gradient as if this were optimizing loglikelihood: 
        AnnotatedSentence sentence = example.getSentence();
        // Subtract the unconditional expected feature counts.
        double unconditionalPartitionFunction = getPartitionFunction(parses);
        for (CcgParse parse : parses) {
          family.incrementSufficientStatistics(gradient, parserParameters, sentence, parse, -1.0 * 
              parse.getSubtreeProbability() / unconditionalPartitionFunction);
        }
        // Add conditional expected feature counts.
        double conditionalPartitionFunction = getPartitionFunction(correctParses);
        for (CcgParse parse : correctParses) {
          family.incrementSufficientStatistics(gradient, parserParameters, sentence, parse,
              parse.getSubtreeProbability() / conditionalPartitionFunction);
        }
      }
      log.stopTimer("compute_gradient");
      log.logStatistic(i, "search errors", searchErrors);

      // This update is currently using a fixed step size, but could probably
      // use a decaying step size.
      double currentStepSize = initialStepSize;
      currentParameters.multiply(1.0 - (currentStepSize * l2Regularization));
      gradient.multiply(currentStepSize);
      currentParameters.transferParameters(gradient);

      // Keep all of the lexicon entries that were used in at least one example's parse.
      currentLexicon.retainAll(usedEntries);
      log.notifyIterationEnd(i);
    }

    return createParser(factory, currentParameters, currentLexicon);
  }
  
  private static Set<LexiconEntry> genEntries(CcgExample example,
      SufficientStatistics currentParameters, Collection<LexiconEntry> currentLexicon,
      Genlex genlex, LexiconInductionCcgParserFactory factory, CcgCkyInference inference,
      ExpressionComparator comparator) {

    Set<LexiconEntry> exampleLexicon = Sets.newHashSet(currentLexicon);
    exampleLexicon.addAll(genlex.genlex(example));

    // Generate the set of max-scoring parses.
    CcgParser currentParser = createParser(factory, currentParameters, exampleLexicon).getParser();
    List<CcgParse> parses = inference.beamSearch(currentParser, example.getSentence(), null, null);
    List<CcgParse> correctParses = CcgLoglikelihoodOracle.filterParsesByLogicalForm(example.getLogicalForm(),
        comparator, parses);

    System.out.println("genEntries: " + example.getSentence() + " " + example.getLogicalForm());
    System.out.println("  numcorrect: " + correctParses.size());

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
  private static ParserInfo createParser(LexiconInductionCcgParserFactory factory,
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
  private static List<CcgParse> filterToMaxScoring(List<CcgParse> parses) {
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
  private static Set<LexiconEntry> getLexiconEntriesFromParses(Collection<CcgParse> parses) {
    // Generate candidate lexicon entries from the correct max parses.
    Set<LexiconEntry> candidateEntries = Sets.newHashSet();
    for (CcgParse correctMaxParse : parses) {
      for (LexiconEntryInfo info : correctMaxParse.getSpannedLexiconEntries()) {
        CcgCategory category = info.getCategory();
        Object trigger = info.getLexiconTrigger();
        List<String> words = null;
        if (trigger instanceof SkipTrigger) {
          words = (List<String>) ((SkipTrigger) trigger).getTrigger();
        } else {
          words = (List<String>) trigger;
        }
        
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
  
  private static class GenEntriesMapper extends Mapper<CcgExample, Set<LexiconEntry>> {
    
    private final SufficientStatistics currentParameters;
    private final Set<LexiconEntry> currentLexicon;
    private final Genlex genlex;
    private final LexiconInductionCcgParserFactory factory;
    private final CcgCkyInference inference;
    private final ExpressionComparator comparator;

    public GenEntriesMapper(SufficientStatistics currentParameters,
        Set<LexiconEntry> currentLexicon, Genlex genlex, LexiconInductionCcgParserFactory factory,
        CcgCkyInference inference, ExpressionComparator comparator) {
      super();
      this.currentParameters = currentParameters;
      this.currentLexicon = currentLexicon;
      this.genlex = genlex;
      this.factory = factory;
      this.inference = inference;
      this.comparator = comparator;
    }

    @Override
    public Set<LexiconEntry> map(CcgExample item) {
      return genEntries(item, currentParameters, currentLexicon, genlex, factory, inference, comparator);
    }
  }
}

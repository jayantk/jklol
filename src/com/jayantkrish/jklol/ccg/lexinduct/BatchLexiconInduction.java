package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgBinaryRule;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgFeatureFactory;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.CcgLoglikelihoodOracle;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.CcgUnaryRule;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.SyntacticCategory.Direction;
import com.jayantkrish.jklol.ccg.lambda.ApplicationExpression;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.LambdaExpression;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.NullLogFunction;

/**
 * Induces a lexicon for a CCG semantic parser directly from 
 * sentences with annotated logical forms.
 *  
 * @author jayant
 */
public class BatchLexiconInduction {
   
  private final int numIterations;
  private final boolean allowComposition;
  private final boolean allowWordSkipping;
  private final boolean normalFormOnly;
  private final CcgFeatureFactory featureFactory;

  private final List<CcgBinaryRule> binaryRules;
  private final List<CcgUnaryRule> unaryRules;

  private final CcgInference inferenceAlg;

  private final GradientOptimizer trainer;
  
  public BatchLexiconInduction(int numIterations, boolean allowComposition,
      boolean allowWordSkipping, boolean normalFormOnly, CcgFeatureFactory featureFactory,
      List<CcgBinaryRule> binaryRules, List<CcgUnaryRule> unaryRules, 
      CcgInference inferenceAlg, GradientOptimizer trainer) {
    this.numIterations = numIterations;
    this.allowComposition = allowComposition;
    this.allowWordSkipping = allowWordSkipping;
    this.normalFormOnly = normalFormOnly;
    this.featureFactory = Preconditions.checkNotNull(featureFactory);

    this.binaryRules = Preconditions.checkNotNull(binaryRules);
    this.unaryRules = Preconditions.checkNotNull(unaryRules);

    this.inferenceAlg = Preconditions.checkNotNull(inferenceAlg);
    
    this.trainer = Preconditions.checkNotNull(trainer);
  }
  
  public CcgParser induceLexicon(List<CcgExample> examples) {
    Set<LexiconEntry> currentLexicon = Sets.newHashSet();
    SufficientStatistics parameters = null;
    CcgParser parser = null;
    // Get all of the part-of-speech tags used in the examples.
    Set<String> posTags = CcgExample.getPosTagVocabulary(examples);

    for (int i = 0; i < numIterations; i++) {
      Set<LexiconEntry> proposedEntries = Sets.newHashSet();
      for (CcgExample example : examples) {
        System.out.println("example: " + example.getSentence().getWords());
        Set<LexiconEntry> exampleProposals = proposeLexiconEntries(example, parser);
        for (LexiconEntry exampleProposal : exampleProposals) {
          System.out.println(exampleProposal);
        }
        proposedEntries.addAll(exampleProposals);
      }

      currentLexicon.addAll(proposedEntries);

      ParametricCcgParser parserFamily = ParametricCcgParser.parseFromLexicon(currentLexicon,
          binaryRules, unaryRules, featureFactory, posTags, allowComposition, null,
          allowWordSkipping, normalFormOnly);
      SufficientStatistics newParameters = parserFamily.getNewSufficientStatistics();
      if (parameters != null) {
        newParameters.transferParameters(parameters);
      }
      parameters = newParameters;

      // Train the parser with the current parameters.
      GradientOracle<CcgParser, CcgExample> oracle = new CcgLoglikelihoodOracle(parserFamily, 100);
      parameters = trainer.train(oracle, parameters, examples);
      parser = parserFamily.getModelFromParameters(parameters);

      System.out.println(parserFamily.getParameterDescription(parameters));
    }

    return parser;
  }
  
  private Set<LexiconEntry> proposeLexiconEntries(CcgExample example, CcgParser parser) {
    if (parser == null) {
      Preconditions.checkArgument(example.hasLogicalForm());
      HeadedSyntacticCategory sentenceCat = HeadedSyntacticCategory.parseFrom("N{0}");
      return Collections.singleton(createLexiconEntry(example.getSentence().getWords(),
          sentenceCat, example.getLogicalForm()));
    } else {
      CcgParse bestParse = inferenceAlg.getBestConditionalParse(parser, example.getSentence(),
          null, new NullLogFunction(), null, null, example.getLogicalForm());
      Set<LexiconEntry> lexiconEntries = Sets.newHashSet();
      if (bestParse != null) {
        System.out.println(bestParse + " " + bestParse.getLogicalForm());
        proposeAllSplits(bestParse, lexiconEntries);
      } 
      return lexiconEntries;
    }
  }

  private void proposeAllSplits(CcgParse parse, Set<LexiconEntry> accumulator) {
    if (parse.isTerminal()) {
      accumulator.addAll(proposeSplit(parse));
    } else {
      proposeAllSplits(parse.getLeft(), accumulator);
      proposeAllSplits(parse.getRight(), accumulator);
    }
  }

  /**
   * Proposes lexicon entries that can be combined to produce
   * {@code parse}, which is a terminal parse tree.
   * 
   * @param parse
   * @return
   */
  private Set<LexiconEntry> proposeSplit(CcgParse parse) {
    Preconditions.checkArgument(parse.isTerminal());
    List<String> words = parse.getLexiconTriggerWords();
    HeadedSyntacticCategory rootCat = parse.getHeadedSyntacticCategory();
    Expression rootLf = parse.getLogicalForm();
    HeadedSyntacticCategory nounCat = HeadedSyntacticCategory.parseFrom("N{0}");

    Set<LexiconEntry> lexiconEntries = Sets.newHashSet();
    for (int splitIndex = 1; splitIndex < words.size(); splitIndex++) {
      List<String> leftWords = words.subList(0, splitIndex);
      List<String> rightWords = words.subList(splitIndex, words.size());

      for (int i = 0; i < 2; i++) {
        Direction direction = (i == 0) ? Direction.LEFT : Direction.RIGHT;
        List<String> argWords = (i == 0) ? leftWords : rightWords;
        List<String> funcWords = (i == 0) ? rightWords : leftWords;

        HeadedSyntacticCategory funcCat = rootCat.addArgument(nounCat, direction, 0);
        HeadedSyntacticCategory argCat = nounCat;

        List<Expression> funcAccumulator = Lists.newArrayList();
        List<Expression> argAccumulator = Lists.newArrayList();
        generateExpressions(rootLf, argAccumulator, funcAccumulator);

        for (int j = 0; j < argAccumulator.size(); j++) {
          Expression funcLf = funcAccumulator.get(j);
          Expression argLf = argAccumulator.get(j);
          lexiconEntries.add(createLexiconEntry(funcWords, funcCat, funcLf));
          lexiconEntries.add(createLexiconEntry(argWords, argCat, argLf));
        }
      }
    }
    return lexiconEntries;
  }

  private void generateExpressions(Expression expression, List<Expression> argAccumulator,
      List<Expression> funcAccumulator) {
    Expression body = expression;
    List<ConstantExpression> arguments = Lists.newArrayList();
    if (expression instanceof LambdaExpression) {
      LambdaExpression lambdaExpression = (LambdaExpression) expression;
      arguments = lambdaExpression.getArguments();
      body = lambdaExpression.getBody();
    }

    ConstantExpression newArg = new ConstantExpression("$" + arguments.size());
    // Add the logical form where one of the splits 
    // just applies the other split.
    List<ConstantExpression> newArgs = Lists.newArrayList(newArg);
    newArgs.addAll(arguments);
    argAccumulator.add(body);
    if (newArgs.size() > 1) {
      funcAccumulator.add(new LambdaExpression(newArgs, new ApplicationExpression(newArgs)));
    } else {
      funcAccumulator.add(new LambdaExpression(newArgs, newArgs.get(0)));
    }

    // Try splitting up the current function application. 
    if (body instanceof ApplicationExpression) {
      ApplicationExpression applicationBody = (ApplicationExpression) body;
      List<Expression> subexpressions = applicationBody.getSubexpressions();
      
      for (int i = 0; i < subexpressions.size(); i++) {
        if (!arguments.contains(subexpressions.get(i))) {
          argAccumulator.add(subexpressions.get(i));
          List<Expression> newBodyList = Lists.newArrayList(subexpressions);
          newBodyList.set(i, newArg);
          Expression newBody = new ApplicationExpression(newBodyList);
          if (arguments.size() > 0) {
            newBody = new LambdaExpression(arguments, newBody);
          }

          funcAccumulator.add(new LambdaExpression(Arrays.asList(newArg), newBody));
        }
      }
    }
  }

  private LexiconEntry createLexiconEntry(List<String> words, HeadedSyntacticCategory cat,
      Expression logicalForm) {
    return new LexiconEntry(words, createCcgCategory(cat, logicalForm));
  }

  private CcgCategory createCcgCategory(HeadedSyntacticCategory cat, Expression logicalForm) {
    List<Set<String>> assignment = Lists.newArrayList();
    for (int i = 0; i < cat.getUniqueVariables().length; i++) {
      assignment.add(Sets.<String>newHashSet());
    }

    return new CcgCategory(cat, logicalForm, Collections.<String>emptyList(),
        Collections.<Integer>emptyList(), Collections.<Integer>emptyList(), assignment);
  }
}

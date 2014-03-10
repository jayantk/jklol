package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.SyntacticCategory.Direction;
import com.jayantkrish.jklol.ccg.lambda.ApplicationExpression;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.LambdaExpression;
import com.jayantkrish.jklol.training.NullLogFunction;

public class UnificationLexiconInductionStrategy implements LexiconInductionStrategy {
  
  private final CcgInference inferenceAlg;

  public UnificationLexiconInductionStrategy(CcgInference inferenceAlg) {
    this.inferenceAlg = Preconditions.checkNotNull(inferenceAlg);
  }

  public Set<LexiconEntry> proposeLexiconEntries(CcgExample example, CcgParser parser) {
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
        // System.out.println(bestParse + " " + bestParse.getLogicalForm());
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

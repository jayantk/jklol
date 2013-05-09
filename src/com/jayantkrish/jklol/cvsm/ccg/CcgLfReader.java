package com.jayantkrish.jklol.cvsm.ccg;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.ccg.lambda.ApplicationExpression;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.LambdaExpression;
import com.jayantkrish.jklol.cvsm.ccg.ConvertCncToCvsm.Span;
import com.jayantkrish.jklol.util.Pair;

public class CcgLfReader {

  private final List<CategoryPattern> patterns;

  private static final Set<String> twoArgumentFunctions = Sets.newHashSet("fa", "ba", "rp", "lp", "bc", "fc", "gbx", "bx", "conj", "funny", "ltc", "rtc");
  private static final Set<String> oneArgumentFunctions = Sets.newHashSet("lex", "tr");

  public CcgLfReader(List<CategoryPattern> patterns) {
    this.patterns = ImmutableList.copyOf(patterns);
  }

  public static CcgLfReader parseFrom(Iterable<String> patternStrings) {
    List<CategoryPattern> patterns = Lists.newArrayList();
    for (String line : patternStrings) {
      patterns.add(SemTypePattern.parseFrom(line));
    }

    return new CcgLfReader(patterns);
  }

  /**
   * Finds the lowest parse tree node in {@code ccgExpression} that
   * spans {@code spanStart} (inclusive) to {@code spanEnd}
   * (exclusive). The parse tree node must have an atomic type.
   * 
   * @param ccgExpression
   * @param spanStart
   * @param spanEnd
   * @return
   */
  public Expression findSpanningExpression(Expression ccgExpression, int spanStart, int spanEnd) {
    List<Expression> spanningExpression = Lists.newArrayList();
    spanningExpression.add(null);
    findSpanningExpressionHelper(ccgExpression, spanStart, spanEnd, spanningExpression);
    return spanningExpression.get(0);
  }

  private static Pair<Integer, Integer> findSpanningExpressionHelper(Expression ccgExpression,
      int spanStart, int spanEnd, List<Expression> spanningExpression) {
    Preconditions.checkState(ccgExpression instanceof ApplicationExpression,
        "Illegal expression type: " + ccgExpression);
    ApplicationExpression app = (ApplicationExpression) ccgExpression;

    Pair<Integer, Integer> currentSpan = null;

    String name = ((ConstantExpression) app.getFunction()).getName();
    List<Expression> arguments = app.getArguments();
    int lastArg = arguments.size() - 1;
    if (name.equals("lf")) {
      int wordInd = Integer.parseInt(((ConstantExpression) arguments.get(1)).getName()) - 1;
      currentSpan = Pair.of(wordInd, wordInd + 1);
    } else if (twoArgumentFunctions.contains(name)) {
      Pair<Integer, Integer> leftSpan = findSpanningExpressionHelper(arguments.get(lastArg - 1),
          spanStart, spanEnd, spanningExpression);
      Pair<Integer, Integer> rightSpan = findSpanningExpressionHelper(arguments.get(lastArg),
          spanStart, spanEnd, spanningExpression);
      currentSpan = Pair.of(leftSpan.getLeft(), rightSpan.getRight());
    } else if (oneArgumentFunctions.contains(name)) {
      currentSpan = findSpanningExpressionHelper(arguments.get(lastArg),
          spanStart, spanEnd, spanningExpression);
    } else {
      throw new IllegalArgumentException("Unknown function type: " + name);
    }
    SyntacticCategory syntax = getSyntacticCategory(ccgExpression);

    if (currentSpan.getLeft() <= spanStart && currentSpan.getRight() >= spanEnd &&
        spanningExpression.get(0) == null && syntax.isAtomic()) {
      spanningExpression.set(0, ccgExpression);
    }
    return currentSpan;
  }

  public Pair<Integer, Integer> getExpressionSpan(Expression ccgExpression) {
    List<Expression> spanningExpression = Lists.newArrayList();
    spanningExpression.add(null);
    return findSpanningExpressionHelper(ccgExpression, -1, -1, spanningExpression);
  }

  public Expression pruneModifiers(Expression ccgExpression, List<Span> mentionSpans) {
    ApplicationExpression app = (ApplicationExpression) ccgExpression;
    String name = ((ConstantExpression) app.getFunction()).getName();
    List<Expression> arguments = app.getArguments();
    int lastArg = arguments.size() - 1;
    if (name.equals("lf")) {
      return ccgExpression;
    } else if (oneArgumentFunctions.contains(name)) {
      List<Expression> newArguments = Lists.newArrayList(arguments);
      newArguments.set(lastArg, pruneModifiers(arguments.get(lastArg), mentionSpans));
      return new ApplicationExpression(app.getFunction(), newArguments);
    } else if (name.equals("fa") || name.equals("ba")) {
      Expression left = arguments.get(lastArg - 1);
      Expression right = arguments.get(lastArg);

      SyntacticCategory syntax = null;
      Expression toSimplify = null;
      Pair<Integer, Integer> expressionSpan = null;
      if (name.equals("fa")) {
        syntax = getSyntacticCategory(left);
        expressionSpan = getExpressionSpan(left);
        toSimplify = right;
      } else {
        syntax = getSyntacticCategory(right);
        expressionSpan = getExpressionSpan(right);
        toSimplify = left;
      }

      if (!syntax.isAtomic() && (syntax.getArgument().isUnifiableWith(syntax.getReturn()) ||
          syntax.isUnifiableWith(SyntacticCategory.parseFrom("NP[1]/N")))) {
        boolean noMentionInSpan = true;
        for (Span mentionSpan : mentionSpans) {
          if (!(mentionSpan.getEnd() <= expressionSpan.getLeft() || mentionSpan.getStart() >= expressionSpan.getRight())) {
            noMentionInSpan = false;
          }
        }
        if (noMentionInSpan) {
          return pruneModifiers(toSimplify, mentionSpans);
        }
      }
    } else if (name.equals("lp")) {
      return pruneModifiers(arguments.get(lastArg), mentionSpans);
    } else if (name.equals("rp")) {
      return pruneModifiers(arguments.get(lastArg - 1), mentionSpans);
    }

    if (twoArgumentFunctions.contains(name)) {
      Expression left = arguments.get(lastArg - 1);
      Expression right = arguments.get(lastArg);

      List<Expression> newArguments = Lists.newArrayList(arguments);
      newArguments.set(lastArg - 1, pruneModifiers(left, mentionSpans));
      newArguments.set(lastArg, pruneModifiers(right, mentionSpans));
      return new ApplicationExpression(app.getFunction(), newArguments);
    } else {
      throw new IllegalArgumentException("Unknown function type: " + name);
    }
  }

  public List<String> getWordsInCcgParse(Expression ccgParse, List<Expression> wordExpressions) {
    List<String> words = Lists.newArrayList();
    getWordsInCcgParseHelper(ccgParse, wordExpressions, words);
    return words;
  }

  public void getWordsInCcgParseHelper(Expression ccgExpression, List<Expression> wordExpressions, List<String> words) {
    ApplicationExpression app = (ApplicationExpression) ccgExpression;
    String name = ((ConstantExpression) app.getFunction()).getName();
    List<Expression> arguments = app.getArguments();
    int lastArg = arguments.size() - 1;
    if (name.equals("lf")) {
      int wordInd = Integer.parseInt(((ConstantExpression) arguments.get(1)).getName()) - 1;
      ApplicationExpression wordExpression = (ApplicationExpression) wordExpressions.get(wordInd);
      words.add(((ConstantExpression) wordExpression.getArguments().get(2)).getName().replaceAll("^\"(.*)\"", "$1"));
    } else if (oneArgumentFunctions.contains(name)) {
      getWordsInCcgParseHelper(arguments.get(lastArg), wordExpressions, words);
    } else if (twoArgumentFunctions.contains(name)) {
      getWordsInCcgParseHelper(arguments.get(lastArg - 1), wordExpressions, words);
      getWordsInCcgParseHelper(arguments.get(lastArg), wordExpressions, words);
    }
  }

  private static SyntacticCategory getSyntacticCategory(Expression ccgExpression) {
    Preconditions.checkState(ccgExpression instanceof ApplicationExpression,
        "Illegal expression type: " + ccgExpression);
    ApplicationExpression app = (ApplicationExpression) ccgExpression;
    String name = ((ConstantExpression) app.getFunction()).getName();
    List<Expression> arguments = app.getArguments();
    int lastArg = arguments.size() - 1;
    String syntaxString = null;
    if (name.equals("lf")) {
      syntaxString = ((ConstantExpression) arguments.get(lastArg)).getName();
    } else if (twoArgumentFunctions.contains(name)) {
      syntaxString = ((ConstantExpression) arguments.get(lastArg - 2)).getName();
    } else if (oneArgumentFunctions.contains(name)) {
      syntaxString = ((ConstantExpression) arguments.get(lastArg - 1)).getName();
    } else {
      throw new IllegalArgumentException("Unknown function type: " + name);
    }

    syntaxString = syntaxString.replaceAll("^\"(.*)\"$", "$1");
    return SyntacticCategory.parseFrom(syntaxString);
  }

  public List<Expression> findAtomicSubexpressions(Expression ccgExpression) {
    List<Expression> subexpressions = Lists.newArrayList();
    findAtomicSubexpressionsHelper(ccgExpression, subexpressions);
    return subexpressions;
  }

  private void findAtomicSubexpressionsHelper(Expression ccgExpression, List<Expression> accumulator) {
    SyntacticCategory syntax = getSyntacticCategory(ccgExpression);
    if (syntax.isAtomic()) {
      accumulator.add(ccgExpression);
    }

    ApplicationExpression app = (ApplicationExpression) ccgExpression;
    String name = ((ConstantExpression) app.getFunction()).getName();
    List<Expression> arguments = app.getArguments();
    int lastArg = arguments.size() - 1;
    if (name.equals("lf")) {
      // Base case: do nothing.
    } else if (twoArgumentFunctions.contains(name)) {
      findAtomicSubexpressionsHelper(arguments.get(lastArg - 1), accumulator);
      findAtomicSubexpressionsHelper(arguments.get(lastArg), accumulator);
    } else if (oneArgumentFunctions.contains(name)) {
      findAtomicSubexpressionsHelper(arguments.get(lastArg), accumulator);
    } else {
      throw new IllegalArgumentException("Unknown function type: " + name);
    }
  }

  public Expression parse(Expression ccgExpression, List<Expression> words) {
    int numWords = words.size();
    List<Expression> wordExpressions = Lists.newArrayList(Collections.<Expression> nCopies(numWords, null));

    for (Expression expression : words) {
      ApplicationExpression app = (ApplicationExpression) expression;
      String functionName = ((ConstantExpression) app.getFunction()).getName();
      Preconditions.checkArgument(functionName.equals("w"));
      int wordNum = Integer.parseInt(((ConstantExpression) app.getArguments().get(1)).getName());
      String word = ((ConstantExpression) app.getArguments().get(2)).getName().toLowerCase().replaceAll("\"", "");
      SyntacticCategory wordSyntax = SyntacticCategory.parseFrom(((ConstantExpression)
          app.getArguments().get(7)).getName().replaceAll("\"", ""));
      wordExpressions.set(wordNum - 1, getExpressionForWord(word, wordSyntax));
    }

    return recursivelyTransformCcgParse(ccgExpression, wordExpressions, words);
  }

  private Expression getExpressionForWord(String word, SyntacticCategory syntax) {
    for (CategoryPattern pattern : patterns) {
      if (pattern.matches(Arrays.<String> asList(word), syntax)) {
        return pattern.getLogicalForm(Arrays.<String> asList(word), syntax);
      }
    }
    return null;
  }

  private Expression recursivelyTransformCcgParse(Expression ccgExpression,
      List<Expression> wordExpressions, List<Expression> words) {
    Preconditions.checkState(ccgExpression instanceof ApplicationExpression,
        "Illegal expression type: " + ccgExpression);
    ApplicationExpression app = (ApplicationExpression) ccgExpression;

    String name = ((ConstantExpression) app.getFunction()).getName();
    List<Expression> arguments = app.getArguments();
    if (name.equals("lf")) {
      int wordInd = Integer.parseInt(((ConstantExpression) arguments.get(1)).getName());
      Expression wordExpression = wordExpressions.get(wordInd - 1);
      if (wordExpression == null) {
        throw new LogicalFormConversionError("No lexicon template for word: " + words.get(wordInd - 1));
      }
      return wordExpression;
    } else if (name.equals("lex")) {
      // Lex is a type-changing rule. Only rules which maintain the
      // same semantic type specification are supported.
      SyntacticCategory origCategory = SyntacticCategory.parseFrom(((ConstantExpression) arguments.get(0)).getName().replaceAll("\"", ""));
      SyntacticCategory newCategory = SyntacticCategory.parseFrom(((ConstantExpression) arguments.get(1)).getName().replaceAll("\"", ""));
      if (!SemTypePattern.hasSameSemanticType(origCategory, newCategory)) {
        throw new LogicalFormConversionError("Used type changing rule: " + origCategory + " to " + newCategory);
      }
      return recursivelyTransformCcgParse(arguments.get(2), wordExpressions, words);
    } else if (name.equals("fa")) {
      Expression left = recursivelyTransformCcgParse(arguments.get(1), wordExpressions, words);
      Expression right = recursivelyTransformCcgParse(arguments.get(2), wordExpressions, words);
      return new ApplicationExpression(left, Arrays.asList(right));
    } else if (name.equals("ba")) {
      Expression left = recursivelyTransformCcgParse(arguments.get(1), wordExpressions, words);
      Expression right = recursivelyTransformCcgParse(arguments.get(2), wordExpressions, words);
      return new ApplicationExpression(right, Arrays.asList(left));
    } else if (name.equals("rp")) {
      Preconditions.checkState(arguments.size() == 3, "rp arguments: " + arguments);
      return recursivelyTransformCcgParse(arguments.get(1), wordExpressions, words);
    } else if (name.equals("lp")) {
      Preconditions.checkState(arguments.size() == 3);
      return recursivelyTransformCcgParse(arguments.get(2), wordExpressions, words);
    } else if (name.equals("bx")) {
      Expression left = recursivelyTransformCcgParse(arguments.get(1), wordExpressions, words);
      Expression right = recursivelyTransformCcgParse(arguments.get(2), wordExpressions, words);

      SyntacticCategory function = getSyntacticCategory(arguments.get(2));
      SyntacticCategory argument = getSyntacticCategory(arguments.get(1));

      int depth = getCompositionDepth(function, argument);
      return buildCompositionExpression(left, right, depth);
    }

    throw new LogicalFormConversionError("Unknown function type: " + name);
  }
  
  private Expression buildCompositionExpression(Expression functionLogicalForm,
      Expression argumentLogicalForm, int numArgsToKeep) {
    // Composition.
    LambdaExpression functionAsLambda = (LambdaExpression) (functionLogicalForm.simplify());
    LambdaExpression argumentAsLambda = (LambdaExpression) (argumentLogicalForm.simplify());
    List<ConstantExpression> remainingArgs = argumentAsLambda.getArguments().subList(0, numArgsToKeep);
    List<ConstantExpression> remainingArgsRenamed = ConstantExpression.generateUniqueVariables(remainingArgs.size());

    List<Expression> functionArguments = Lists.newArrayList();
    functionArguments.add(new ApplicationExpression(argumentAsLambda, remainingArgsRenamed));
    List<ConstantExpression> newFunctionArgs = ConstantExpression.generateUniqueVariables(functionAsLambda.getArguments().size() - 1);
    functionArguments.addAll(newFunctionArgs);

    Expression result = new ApplicationExpression(functionAsLambda, functionArguments);
    if (newFunctionArgs.size() > 0) {
      result = new LambdaExpression(newFunctionArgs, result);
    }
    result = new LambdaExpression(remainingArgsRenamed, result);
    return result;
  }
  
  private int getCompositionDepth(SyntacticCategory function, SyntacticCategory argument) {
    SyntacticCategory functionArgument = function.getArgument();
    // Depth is the number of arguments of argument which must be passed in before
    // function can be applied to argument.
    int depth = 0;
    while (!functionArgument.isUnifiableWith(argument)) {
      depth++;
      argument = argument.getReturn();
    }
    return depth;
  }

  public class LogicalFormConversionError extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public LogicalFormConversionError(String cause) {
      super(cause);
    }
  }
}

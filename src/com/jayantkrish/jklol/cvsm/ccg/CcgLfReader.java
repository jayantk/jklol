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
    String syntaxString = null;
    
    String name = ((ConstantExpression) app.getFunction()).getName();
    List<Expression> arguments = app.getArguments();
    int lastArg = arguments.size() - 1;
    if (name.equals("lf")) {
      int wordInd = Integer.parseInt(((ConstantExpression) arguments.get(1)).getName()) - 1;
      currentSpan = Pair.of(wordInd, wordInd + 1);
      syntaxString = ((ConstantExpression) arguments.get(lastArg)).getName();
      
    } else if (twoArgumentFunctions.contains(name)) {
      Pair<Integer, Integer> leftSpan = findSpanningExpressionHelper(arguments.get(lastArg - 1),
          spanStart, spanEnd, spanningExpression);
      Pair<Integer, Integer> rightSpan = findSpanningExpressionHelper(arguments.get(lastArg),
          spanStart, spanEnd, spanningExpression);
      
      syntaxString = ((ConstantExpression) arguments.get(lastArg - 2)).getName();
      currentSpan = Pair.of(leftSpan.getLeft(), rightSpan.getRight());
    } else if (oneArgumentFunctions.contains(name)) {
      syntaxString = ((ConstantExpression) arguments.get(lastArg - 1)).getName();
      currentSpan = findSpanningExpressionHelper(arguments.get(lastArg),
          spanStart, spanEnd, spanningExpression);
    } else {
      throw new IllegalArgumentException("Unknown function type: " + name);
    }
    
    syntaxString = syntaxString.replaceAll("^\"(.*)\"$", "$1");
    SyntacticCategory syntax = SyntacticCategory.parseFrom(syntaxString);

    if (currentSpan.getLeft() <= spanStart && currentSpan.getRight() >= spanEnd &&
        spanningExpression.get(0) == null && syntax.isAtomic()) {
      spanningExpression.set(0, ccgExpression);
    }    
    return currentSpan;
  }
  
  public Expression parse(Expression ccgExpression, List<Expression> words) {
    int numWords = words.size();
    List<Expression> wordExpressions = Lists.newArrayList(Collections.<Expression>nCopies(numWords, null));
    
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

    for (int i = 0; i < wordExpressions.size(); i++) {
      if (wordExpressions.get(i) == null) {
        throw new LogicalFormConversionError("No lexicon template for word: " + words.get(i));        
      }
    }
    return recursivelyTransformCcgParse(ccgExpression, wordExpressions);
  }

  private Expression getExpressionForWord(String word, SyntacticCategory syntax) {
    for (CategoryPattern pattern : patterns) {
      if (pattern.matches(Arrays.<String>asList(word), syntax)) {
        return pattern.getLogicalForm(Arrays.<String>asList(word), syntax);
      }
    }
    return null;
  }

  private Expression recursivelyTransformCcgParse(Expression ccgExpression,
      List<Expression> wordExpressions) {
    Preconditions.checkState(ccgExpression instanceof ApplicationExpression, 
        "Illegal expression type: " + ccgExpression);
    ApplicationExpression app = (ApplicationExpression) ccgExpression;
    
    String name = ((ConstantExpression) app.getFunction()).getName();
    List<Expression> arguments = app.getArguments();
    if (name.equals("lf")) {
      int wordInd = Integer.parseInt(((ConstantExpression) arguments.get(1)).getName());
      return wordExpressions.get(wordInd - 1);
    } else if (name.equals("lex")) {
      // Lex is a type-changing rule. Only rules which maintain the same
      // semantic type specification are supported.
      SyntacticCategory origCategory = SyntacticCategory.parseFrom(((ConstantExpression) arguments.get(0)).getName().replaceAll("\"", ""));
      SyntacticCategory newCategory = SyntacticCategory.parseFrom(((ConstantExpression) arguments.get(1)).getName().replaceAll("\"", ""));
      if (!SemTypePattern.hasSameSemanticType(origCategory, newCategory)) {
        throw new LogicalFormConversionError("Used type changing rule: " + origCategory + " to " + newCategory);
      }
      return recursivelyTransformCcgParse(arguments.get(2), wordExpressions);
    } else if (name.equals("fa")) {
      Expression left = recursivelyTransformCcgParse(arguments.get(1), wordExpressions);
      Expression right = recursivelyTransformCcgParse(arguments.get(2), wordExpressions);
      return new ApplicationExpression(left, Arrays.asList(right));
    } else if (name.equals("ba")) {
      Expression left = recursivelyTransformCcgParse(arguments.get(1), wordExpressions);
      Expression right = recursivelyTransformCcgParse(arguments.get(2), wordExpressions);
      return new ApplicationExpression(right, Arrays.asList(left));
    } else if (name.equals("rp")) {
      Preconditions.checkState(arguments.size() == 3, "rp arguments: " + arguments);
      return recursivelyTransformCcgParse(arguments.get(1), wordExpressions);
    } else if (name.equals("lp")) {
      Preconditions.checkState(arguments.size() == 3);
      return recursivelyTransformCcgParse(arguments.get(2), wordExpressions);
    }
    // TODO: composition rules.
    throw new LogicalFormConversionError("Unknown function type: " + name);
  }

  public class LogicalFormConversionError extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public LogicalFormConversionError(String cause) {
      super(cause);
    }
  }
}

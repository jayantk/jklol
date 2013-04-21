package com.jayantkrish.jklol.cvsm.ccg;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.ccg.lambda.ApplicationExpression;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.ccg.lambda.Expression;

public class CcgLfReader {

  private final List<CategoryPattern> patterns;
  
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
      Preconditions.checkState(wordExpressions.get(i) != null);
    }
    return recursivelyTransformCcgParse(((ApplicationExpression) ccgExpression).getArguments().get(1), 
        wordExpressions);
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

package com.jayantkrish.jklol.ccg.pattern;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.FunctionValue;
import com.jayantkrish.jklol.lisp.LispEval;
import com.jayantkrish.jklol.lisp.LispEval.EvalResult;
import com.jayantkrish.jklol.lisp.SExpression;

public class CcgPatternUtils {

  public static CcgPattern parseFrom(String patternString) {
    ExpressionParser<SExpression> parser = ExpressionParser.sExpression();
    SExpression patternExpression = parser.parseSingleExpression(patternString);

    LispEval eval = new LispEval();
    Environment env = Environment.empty();
    env.bindName("word", new WordPatternFunction());
    env.bindName("syntax", new SyntaxPatternFunction());
    env.bindName("lf_regex", new LogicalFormPatternFunction());
    env.bindName("and", new AndPatternFunction());
    env.bindName("or", new OrPatternFunction());
    env.bindName("subtree", new SubtreePatternFunction(false));
    env.bindName("head_subtree", new SubtreePatternFunction(true));
    env.bindName("combinator", new CombinatorPatternFunction());
    env.bindName("isTerminal", new CcgTerminalPattern(true));
    env.bindName("isNonterminal", new CcgTerminalPattern(false));

    EvalResult result = eval.eval(patternExpression, env);
    return (CcgPattern) result.getValue();
  }

  private static class WordPatternFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      List<String> args = Lists.newArrayList();
      for (Object argumentValue : argumentValues) {
        args.add((String) argumentValue);
      }
      return new CcgWordPattern(args);
    }
  }
  
  private static class SyntaxPatternFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      HeadedSyntacticCategory cat = HeadedSyntacticCategory.parseFrom(
          (String) argumentValues.get(0));
      return new CcgSyntaxPattern(cat);
    }
  }

  private static class LogicalFormPatternFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      return new CcgLogicalFormPattern((String) argumentValues.get(0));
    }
  }

  private static class AndPatternFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      List<CcgPattern> childPatterns = Lists.newArrayList();
      for (Object argumentValue : argumentValues) {
        childPatterns.add((CcgPattern) argumentValue);
      }
      return new CcgAndPattern(childPatterns);
    }
  }

  private static class OrPatternFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      List<CcgPattern> childPatterns = Lists.newArrayList();
      for (Object argumentValue : argumentValues) {
        childPatterns.add((CcgPattern) argumentValue);
      }
      return new CcgOrPattern(childPatterns);
    }
  }

  private static class SubtreePatternFunction implements FunctionValue {
    private final boolean matchSameHead;

    public SubtreePatternFunction(boolean matchSameHead) {
      this.matchSameHead = matchSameHead;
    }

    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      return new CcgSubtreePattern((CcgPattern) argumentValues.get(0), matchSameHead);
    }
  }

  private static class CombinatorPatternFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      return new CcgCombinatorPattern((CcgPattern) argumentValues.get(0),
          (CcgPattern) argumentValues.get(1));
    }
  }

  private CcgPatternUtils() {
    // Prevent instantiation.
  }
}

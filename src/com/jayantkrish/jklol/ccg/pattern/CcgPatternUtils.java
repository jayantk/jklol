package com.jayantkrish.jklol.ccg.pattern;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.EvalContext;
import com.jayantkrish.jklol.lisp.FunctionValue;
import com.jayantkrish.jklol.lisp.LispEval;
import com.jayantkrish.jklol.lisp.LispEval.EvalResult;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.util.IndexedList;

public class CcgPatternUtils {

  public static List<CcgPattern> parseFrom(String patternString) {
    IndexedList<String> symbolTable = LispEval.getInitialSymbolTable();
    ExpressionParser<SExpression> parser = ExpressionParser.sExpression(symbolTable);
    List<SExpression> expressions = parser.parseAll(patternString);

    LispEval eval = new LispEval(symbolTable);
    Environment env = Environment.empty();
    env.bindName("word", new WordPatternFunction(), symbolTable);
    env.bindName("syntax", new SyntaxPatternFunction(), symbolTable);
    env.bindName("lf-regex", new LogicalFormPatternFunction(), symbolTable);
    env.bindName("chain", new AndPatternFunction(), symbolTable);
    env.bindName("union", new OrPatternFunction(), symbolTable);
    env.bindName("subtree", new SubtreePatternFunction(false, false), symbolTable);
    env.bindName("subtree-contains", new SubtreePatternFunction(false, true), symbolTable);
    env.bindName("head-subtree", new SubtreePatternFunction(true, false), symbolTable);
    env.bindName("head-subtree-contains", new SubtreePatternFunction(true, true), symbolTable);
    env.bindName("combinator", new CombinatorPatternFunction(), symbolTable);
    env.bindName("smallest", new SmallestPatternFunction(), symbolTable);
    env.bindName("isTerminal", new CcgTerminalPattern(true), symbolTable);
    env.bindName("isNonterminal", new CcgTerminalPattern(false), symbolTable);
    env.bindName("replace-syntax", new ReplaceSyntaxPatternFunction(), symbolTable);
    env.bindName("propagate-features", new PropagateFeaturesPatternFunction(), symbolTable);
    env.bindName("recurse", new RecursivePatternFunction(), symbolTable);
    env.bindName("if-then", new IfPatternFunction(), symbolTable);
    env.bindName("not", new NotPatternFunction(), symbolTable);
    env.bindName("adjunct-to-argument", new CcgPrepositionFixingPattern(), symbolTable);

    List<CcgPattern> patterns = Lists.newArrayList();
    for (SExpression patternExpression : expressions) {
      EvalResult result = eval.eval(patternExpression, env);
      if (result.getValue() instanceof CcgPattern) {
        patterns.add((CcgPattern) result.getValue());
      }
    }
    return patterns;
  }

  private static class WordPatternFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      List<String> args = Lists.newArrayList();
      for (Object argumentValue : argumentValues) {
        args.add((String) argumentValue);
      }
      return new CcgWordPattern(args);
    }
  }
  
  private static class SyntaxPatternFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      HeadedSyntacticCategory cat = HeadedSyntacticCategory.parseFrom(
          (String) argumentValues.get(0));
      return new CcgSyntaxPattern(cat);
    }
  }

  private static class LogicalFormPatternFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      return new CcgLogicalFormPattern((String) argumentValues.get(0));
    }
  }

  private static class AndPatternFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      List<CcgPattern> childPatterns = Lists.newArrayList();
      for (Object argumentValue : argumentValues) {
        childPatterns.add((CcgPattern) argumentValue);
      }
      return new CcgChainPattern(childPatterns);
    }
  }

  private static class OrPatternFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      List<CcgPattern> childPatterns = Lists.newArrayList();
      for (Object argumentValue : argumentValues) {
        childPatterns.add((CcgPattern) argumentValue);
      }
      return new CcgUnionPattern(childPatterns);
    }
  }

  private static class SubtreePatternFunction implements FunctionValue {
    private final boolean matchSameHead;
    private final boolean returnWholeTree;

    public SubtreePatternFunction(boolean matchSameHead, boolean returnWholeTree) {
      this.matchSameHead = matchSameHead;
      this.returnWholeTree = returnWholeTree;
    }

    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      return new CcgSubtreePattern((CcgPattern) argumentValues.get(0), matchSameHead,
          returnWholeTree);
    }
  }

  private static class CombinatorPatternFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      return new CcgCombinatorPattern((CcgPattern) argumentValues.get(0),
          (CcgPattern) argumentValues.get(1));
    }
  }

  private static class SmallestPatternFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      return new CcgSmallestPattern((CcgPattern) argumentValues.get(0));
    }
  }

  private static class ReplaceSyntaxPatternFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      return new CcgReplaceSyntaxPattern(HeadedSyntacticCategory.parseFrom(
          (String) argumentValues.get(0)));
    }
  }

  private static class IfPatternFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      return new CcgIfPattern((CcgPattern) argumentValues.get(0),
          (CcgPattern) argumentValues.get(1));
    }
  }

  private static class RecursivePatternFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      return new CcgRecursivePattern((CcgPattern) argumentValues.get(0));
    }
  }

  private static class NotPatternFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      return new CcgNotPattern((CcgPattern) argumentValues.get(0));
    }
  }

  private static class PropagateFeaturesPatternFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      Preconditions.checkArgument(argumentValues.size() == 0);
      return new CcgPropagateFeaturesPattern();
    }
  }

  private CcgPatternUtils() {
    // Prevent instantiation.
  }
}

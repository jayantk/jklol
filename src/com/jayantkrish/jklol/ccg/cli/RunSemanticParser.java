package com.jayantkrish.jklol.ccg.cli;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.jayantkrish.jklol.ccg.CcgExactInference;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.VariableCanonicalizationReplacementRule;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.LispEval.EvalResult;
import com.jayantkrish.jklol.lisp.LispUtil;
import com.jayantkrish.jklol.lisp.ParametricBfgBuilder;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.NullLogFunction;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IoUtils;

public class RunSemanticParser extends AbstractCli {

  private OptionSpec<String> model;
  
  private OptionSpec<String> environment;
  
  @Override
  public void initializeOptions(OptionParser parser) {
    // Required arguments.
    model = parser.accepts("model").withRequiredArg().ofType(String.class).required();
    environment = parser.accepts("environment").withRequiredArg().ofType(String.class).required();
  }

  @Override
  public void run(OptionSet options) {
    IndexedList<String> symbolTable = AmbEval.getInitialSymbolTable();
    Environment env = AmbEval.getDefaultEnvironment(symbolTable);
    AmbEval eval = new AmbEval(symbolTable);
    ParametricBfgBuilder fgBuilder = new ParametricBfgBuilder(true);
    SExpression program = LispUtil.readProgram(Arrays.asList(options.valueOf(environment)), symbolTable);
    EvalResult result = eval.eval(program, env, fgBuilder);

    CcgParser parser = IoUtils.readSerializedObject(options.valueOf(model), CcgParser.class);
    CcgInference inferenceAlg = new CcgExactInference(null, -1L, Integer.MAX_VALUE, 1);
    ExpressionSimplifier simplifier = new ExpressionSimplifier(Arrays.
        <ExpressionReplacementRule>asList(new LambdaApplicationReplacementRule(),
            new VariableCanonicalizationReplacementRule()));

    List<String> words = options.nonOptionArguments();
    List<String> pos = Collections.nCopies(words.size(), ParametricCcgParser.DEFAULT_POS_TAG);
    AnnotatedSentence sentence = new AnnotatedSentence(words, pos);

    CcgParse parse = inferenceAlg.getBestParse(parser, sentence, null, new NullLogFunction());

    Expression2 logicalForm = simplifier.apply(parse.getLogicalForm());
    System.out.println("expression: " + logicalForm);

    SExpression expression = ExpressionParser.sExpression(symbolTable)
        .parseSingleExpression(logicalForm.toString());
    
    result = eval.eval(expression, env, fgBuilder);
    System.out.println("value: " + result.getValue());
  }

  public static void main(String[] args) {
    new RunSemanticParser().run(args);
  }
}

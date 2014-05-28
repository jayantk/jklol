package com.jayantkrish.jklol.lisp.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.BuiltinFunctions;
import com.jayantkrish.jklol.lisp.ConsValue;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.LispEval.EvalResult;
import com.jayantkrish.jklol.lisp.ParametricBfgBuilder;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IoUtils;

public class AmbLisp extends AbstractCli {

  private OptionSpec<Void> printFactorGraph;
  private OptionSpec<Void> interactive;
  private OptionSpec<String> evalOpt;
  
  private OptionSpec<Integer> optEpochs;
  private OptionSpec<Double> optL2Regularization;
  private OptionSpec<Double> optL2RegularizationFrequency;
  private OptionSpec<String> args;
  
  @Override
  public void initializeOptions(OptionParser parser) {
    printFactorGraph = parser.accepts("printFactorGraph");
    interactive = parser.accepts("interactive");
    evalOpt = parser.accepts("eval").withRequiredArg().ofType(String.class);
    
    // Options for controlling the optimization procedure
    optEpochs = parser.accepts("optEpochs").withRequiredArg().ofType(Integer.class).defaultsTo(50);
    optL2Regularization = parser.accepts("optL2Regularization").withRequiredArg()
        .ofType(Double.class).defaultsTo(0.0);
    optL2RegularizationFrequency  = parser.accepts("optL2RegularizationFrequency").withRequiredArg()
        .ofType(Double.class).defaultsTo(1.0);

    // Command line arguments passed through to the program
    // being evaluated.
    args = parser.accepts("args").withRequiredArg().ofType(String.class);
  }

  @Override
  public void run(OptionSet options) {
    StringBuilder programBuilder = new StringBuilder();
    programBuilder.append("(begin ");
    // Non-option arguments are filenames containing the code to execute.
    List<String> filenames = options.nonOptionArguments();

    for (String filename : filenames) {
      for (String line : IoUtils.readLines(filename)) {
        line = line.replaceAll("^ *;.*", "");
        programBuilder.append(line);
        programBuilder.append(" ");
      }
    }

    programBuilder.append(" )");
    String program = programBuilder.toString();

    IndexedList<String> symbolTable = AmbEval.getInitialSymbolTable();
    AmbEval eval = new AmbEval(symbolTable);
    ExpressionParser<SExpression> parser = ExpressionParser.sExpression(symbolTable);
    SExpression programExpression = parser.parseSingleExpression(program);
    ParametricBfgBuilder fgBuilder = new ParametricBfgBuilder(true);
    Environment environment = createEnvironmentFromOptions(options, symbolTable);
    EvalResult result = eval.eval(programExpression, environment, fgBuilder);

    if (options.has(evalOpt)) {
      SExpression argExpression = parser.parseSingleExpression(options.valueOf(evalOpt));
      result = eval.eval(argExpression, environment, fgBuilder);
    }

    BuiltinFunctions.display(result.getValue());

    if (options.has(interactive)) {
      System.out.println("Starting interactive mode.");
      // Any input on stdin is also evaluated after the given files.
      BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
      String line = null;
      System.out.print(">> ");
      try {
        while ((line = in.readLine()) != null) {
          line = line.replaceAll(";.*", "");

          try {
            SExpression expression = parser.parseSingleExpression(line);
            result = eval.eval(expression, environment, fgBuilder);
            BuiltinFunctions.display(result.getValue());
          } catch (Exception e) {
            System.out.println("Exception: " + e);
          }

          System.out.print(">> ");
        }
      } catch (IOException e) {
        System.out.println("Terminating interactive mode.");
      }
    }

    if (options.has(printFactorGraph)) {
      System.out.println("Factor graph: ");
      System.out.println(fgBuilder.build().getParameterDescription());
    }
  }

  private Environment createEnvironmentFromOptions(OptionSet options,
      IndexedList<String> symbolTable) {
    Environment env = AmbEval.getDefaultEnvironment(symbolTable);
    env.bindName(AmbEval.OPT_EPOCHS_VAR_NAME, options.valueOf(optEpochs), symbolTable);
    env.bindName(AmbEval.OPT_L2_VAR_NAME, options.valueOf(optL2Regularization), symbolTable);
    env.bindName(AmbEval.OPT_L2_FREQ_VAR_NAME, options.valueOf(optL2RegularizationFrequency),
        symbolTable);

    List<String> commandLineArgs = Lists.newArrayList();
    for (String arg : options.valuesOf(args)) {
      commandLineArgs.add(arg);
    }
    env.bindName(AmbEval.CLI_ARGV_VAR_NAME, ConsValue.listToConsList(commandLineArgs),
        symbolTable);
    return env;
  }

  public static void main(String[] args) {
    new AmbLisp().run(args);
  }
}

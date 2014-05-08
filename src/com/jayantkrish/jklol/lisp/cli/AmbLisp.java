package com.jayantkrish.jklol.lisp.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.BuiltinFunctions;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.LispEval.EvalResult;
import com.jayantkrish.jklol.lisp.ParametricBfgBuilder;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.util.IoUtils;

public class AmbLisp extends AbstractCli {

  private OptionSpec<Void> printFactorGraph;
  private OptionSpec<Void> interactive;
  
  @Override
  public void initializeOptions(OptionParser parser) {
    printFactorGraph = parser.accepts("printFactorGraph");
    interactive = parser.accepts("interactive");
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

    AmbEval eval = new AmbEval();
    ExpressionParser<SExpression> parser = ExpressionParser.sExpression();
    SExpression programExpression = parser.parseSingleExpression(program);
    ParametricBfgBuilder fgBuilder = new ParametricBfgBuilder(true);
    Environment environment = AmbEval.getDefaultEnvironment();
    EvalResult result = eval.eval(programExpression, environment, fgBuilder);

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

  public static void main(String[] args) {
    new AmbLisp().run(args);
  }
}

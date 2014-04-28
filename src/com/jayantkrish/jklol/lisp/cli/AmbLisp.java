package com.jayantkrish.jklol.lisp.cli;

import java.util.List;
import java.util.Scanner;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.LispEval.EvalResult;
import com.jayantkrish.jklol.lisp.ParametricBfgBuilder;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.util.IoUtils;

public class AmbLisp extends AbstractCli {

  private OptionSpec<Void> printFactorGraph;
  private OptionSpec<Void> stdin;
  
  @Override
  public void initializeOptions(OptionParser parser) {
    printFactorGraph = parser.accepts("printFactorGraph");
    stdin = parser.accepts("stdin");
  }

  @Override
  public void run(OptionSet options) {

    StringBuilder programBuilder = new StringBuilder();
    programBuilder.append("(begin ");
    // Non-option arguments are filenames containing the code to execute.
    List<String> filenames = options.nonOptionArguments();

    for (String filename : filenames) {
      for (String line : IoUtils.readLines(filename)) {
        line = line.replaceAll(";.*", "");
        programBuilder.append(line);
      }
    }

    if (options.has(stdin)) {
      // Any input on stdin is also evaluated after the filenames.
      Scanner scanner = new Scanner(System.in);
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        line = line.replaceAll(";.*", "");
        programBuilder.append(line);
      }
    }

    programBuilder.append(" )");
    String program = programBuilder.toString();

    AmbEval eval = new AmbEval();
    ExpressionParser<SExpression> parser = ExpressionParser.sExpression();
    SExpression programExpression = parser.parseSingleExpression(program);
    ParametricBfgBuilder fgBuilder = new ParametricBfgBuilder(true);
    EvalResult result = eval.eval(programExpression, AmbEval.getDefaultEnvironment(), 
        fgBuilder);

    System.out.println(result.getValue());
    
    if (options.has(printFactorGraph)) {
      System.out.println("Factor graph: ");
      System.out.println(fgBuilder.build().getParameterDescription());
    }
  }

  public static void main(String[] args) {
    new AmbLisp().run(args);
  }
}

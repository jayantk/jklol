package com.jayantkrish.jklol.lisp.cli;

import java.util.List;

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
  
  @Override
  public void initializeOptions(OptionParser parser) {
    printFactorGraph = parser.accepts("printFactorGraph");
  }

  @Override
  public void run(OptionSet options) {
    List<String> filenames = options.nonOptionArguments();

    StringBuilder programBuilder = new StringBuilder();
    programBuilder.append("(begin ");
    for (String filename : filenames) {
      for (String line : IoUtils.readLines(filename)) {
        if (line.matches("^\\s*;.*")) {
          // This line is a comment.
          continue;
        }
        programBuilder.append(line);
      }
    }
    programBuilder.append(" )");

    AmbEval eval = new AmbEval();
    ExpressionParser<SExpression> parser = ExpressionParser.sExpression();
    SExpression programExpression = parser.parseSingleExpression(programBuilder.toString());
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

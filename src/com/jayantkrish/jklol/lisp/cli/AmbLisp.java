package com.jayantkrish.jklol.lisp.cli;

import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.Eval;
import com.jayantkrish.jklol.lisp.Eval.EvalResult;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.util.IoUtils;

public class AmbLisp extends AbstractCli {

  @Override
  public void initializeOptions(OptionParser parser) {
    
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

    Eval eval = new AmbEval();
    ExpressionParser<SExpression> parser = ExpressionParser.sExpression();
    EvalResult result = eval.eval(parser.parseSingleExpression(programBuilder.toString()),
        AmbEval.getDefaultEnvironment());

    System.out.println(result.getValue());
  }

  public static void main(String[] args) {
    new AmbLisp().run(args);
  }
}

package com.jayantkrish.jklol.cvsm.ccg;

import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.cvsm.ccg.CcgLfReader.LogicalFormConversionError;
import com.jayantkrish.jklol.util.IoUtils;

public class ConvertCncToCvsm extends AbstractCli {
  
  private OptionSpec<String> cncParses;
  private OptionSpec<String> lfTemplates;
  
  public ConvertCncToCvsm() {
    super();
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    cncParses = parser.accepts("cncParses").withRequiredArg().ofType(String.class).required();
    lfTemplates = parser.accepts("lfTemplates").withRequiredArg().ofType(String.class).required();
  }

  @Override
  public void run(OptionSet options) {
    CcgLfReader reader = CcgLfReader.parseFrom(IoUtils.readLines(options.valueOf(lfTemplates)));
    ExpressionParser exp = new ExpressionParser();
    
    List<String> lines = IoUtils.readLines(options.valueOf(cncParses));
    Expression ccgExpression = null;
    List<Expression> wordExpressions = null;
    List<Expression> expressions = Lists.newArrayList();
    for (String line : lines) {
      if (!line.startsWith("(")) {
        continue;
      }
      
      if (line.startsWith("(ccg")) {
        if (ccgExpression != null) {
          try {
            Expression parsedExpression = reader.parse(ccgExpression, wordExpressions);
            System.out.println(parsedExpression.simplify());
            expressions.add(parsedExpression);
          } catch (LogicalFormConversionError error) {
            System.out.println("No conversion.");
            expressions.add(null);
          }
        }
        ccgExpression = exp.parseSingleExpression(line);
        wordExpressions = Lists.newArrayList();
      } else if (line.startsWith("(w")) {
        wordExpressions.add(exp.parseSingleExpression(line));
      }
    }

    if (ccgExpression != null) {
      // TODO
      expressions.add(reader.parse(ccgExpression, wordExpressions));
    }
  }

  public static void main(String[] args) {
    new ConvertCncToCvsm().run(args);
  }
}

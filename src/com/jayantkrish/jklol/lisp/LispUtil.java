package com.jayantkrish.jklol.lisp;

import java.util.List;

import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IoUtils;

public class LispUtil {

  public static SExpression readProgram(List<String> filenames, IndexedList<String> symbolTable) {
    StringBuilder programBuilder = new StringBuilder();
    programBuilder.append("(begin ");

    for (String filename : filenames) {
      for (String line : IoUtils.readLines(filename)) {
        line = line.replaceAll("^ *;.*", "");
        programBuilder.append(line);
        programBuilder.append(" ");
      }
    }

    programBuilder.append(" )");
    String program = programBuilder.toString();
    ExpressionParser<SExpression> parser = ExpressionParser.sExpression(symbolTable);
    SExpression programExpression = parser.parseSingleExpression(program);
    return programExpression;
  }

}

package com.jayantkrish.jklol.lisp;

import java.util.List;

import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IoUtils;

/**
 * Utility functions for writing Java/Lisp interface code
 * and working with Lisp programs.
 * 
 * @author jayantk
 *
 */
public class LispUtil {

  /**
   * Reads a program from a list of files. Lines starting with
   * any amount of whitespace followed by ; are ignored as comments.
   * 
   * @param filenames
   * @param symbolTable
   * @return
   */
  public static SExpression readProgram(List<String> filenames, IndexedList<String> symbolTable) {
    StringBuilder programBuilder = new StringBuilder();
    programBuilder.append("(begin ");

    for (String filename : filenames) {
      for (String line : IoUtils.readLines(filename)) {
        line = line.replaceAll("^[ \t]*;.*", "");
        programBuilder.append(line);
        programBuilder.append(" ");
      }
    }

    programBuilder.append(" )");
    String program = programBuilder.toString();
    ExpressionParser<SExpression> parser = ExpressionParser.sExpression(symbolTable);
    SExpression programExpression = parser.parse(program);
    return programExpression;
  }

  /**
   * Identical to Preconditions.checkArgument but throws an
   * {@code EvalError} instead of an IllegalArgumentException.
   * Use this check to verify properties of a Lisp program
   * execution, i.e., whenever the raised exception should be
   * catchable by the evaluator of the program.
   *
   * @param condition
   * @param message
   * @param values
   */
  public static void checkArgument(boolean condition, String message, Object ... values) {
    if (!condition) {
      throw new EvalError(String.format(message, values));
    }
  }

  public static void checkArgument(boolean condition) {
    checkArgument(condition, "");
  }

  /**
   * Identical to Preconditions.checkNotNull but throws an
   * {@code EvalError} instead of an IllegalArgumentException.
   * Use this check to verify properties of a Lisp program
   * execution, i.e., whenever the raised exception should be
   * catchable by the evaluator of the program.
   *
   * @param condition
   * @param message
   * @param values
   */
  public static void checkNotNull(Object ref, String message, Object ... values) {
    if (ref == null) {
      throw new EvalError(String.format(message, values));
    }
  }

  public static void checkNotNull(Object ref) {
    checkNotNull(ref, "");
  }
  
  /**
   * Identical to Preconditions.checkState but throws an
   * {@code EvalError} instead of an IllegalArgumentException.
   * Use this check to verify properties of a Lisp program
   * execution, i.e., whenever the raised exception should be
   * catchable by the evaluator of the program.
   *
   * @param condition
   * @param message
   * @param values
   */
  public static void checkState(boolean condition, String message, Object ... values) {
    if (!condition) {
      throw new EvalError(String.format(message, values));
    }
  }
  
  public static void checkState(boolean condition) {
    checkState(condition, "");
  }

  public static <T> T cast(Object o, Class<T> clazz) {
    checkArgument(clazz.isInstance(o));
    return clazz.cast(o);
  }
}

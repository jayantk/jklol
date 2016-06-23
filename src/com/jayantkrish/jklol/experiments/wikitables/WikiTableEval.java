package com.jayantkrish.jklol.experiments.wikitables;

import java.util.List;
import java.util.Map;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.RegexTypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.AmbEvalEvaluator;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionEvaluator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.LispUtil;
import com.jayantkrish.jklol.lisp.ParametricBfgBuilder;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IoUtils;

public class WikiTableEval extends AbstractCli {

  private OptionSpec<String> tablesDir;
  private OptionSpec<String> tableId;
  private OptionSpec<String> environment;
  private OptionSpec<String> typeDeclaration;
  
  public WikiTableEval() {
    super();
  }
  
  @Override
  public void initializeOptions(OptionParser parser) {
    tablesDir = parser.accepts("tablesDir").withRequiredArg().ofType(String.class).required();
    tableId = parser.accepts("tableId").withRequiredArg().ofType(String.class).required();
    environment = parser.accepts("environment").withRequiredArg().ofType(String.class).withValuesSeparatedBy(',');
  }

  @Override
  public void run(OptionSet options) {
    List<WikiTable> tables = WikiTablesUtil.readTables(options.valueOf(tablesDir));
    Map<String, Integer> tableIndexMap = Maps.newHashMap();
    for (int i = 0; i < tables.size(); i++) {
      WikiTable table = tables.get(i);
      tableIndexMap.put(table.getId(), i);
    }
    
    IndexedList<String> symbolTable = AmbEval.getInitialSymbolTable();
    Environment env = WikiTablesUtil.getEnvironment(symbolTable, tableIndexMap, tables);
    AmbEval eval = new AmbEval(symbolTable);
    ParametricBfgBuilder fgBuilder = new ParametricBfgBuilder(true);
    SExpression program = LispUtil.readProgram(options.valuesOf(environment), symbolTable);
    ExpressionParser<SExpression> sexpParser = ExpressionParser.sExpression(symbolTable);
    eval.eval(program, env, fgBuilder);
    
    ExpressionEvaluator evaluator = new AmbEvalEvaluator(sexpParser, eval, env);
    ExpressionParser<Expression2> expParser = ExpressionParser.expression2();

    WikiTable table = tables.get(tableIndexMap.get(options.valueOf(tableId)));
    Expression2 expression = expParser.parse(Joiner.on(" ").join(options.nonOptionArguments()));
    
    Expression2 sexpression = WikiTablesUtil.getQueryExpression(table.getId(), expression);
    Object value = evaluator.evaluateSilentErrors(sexpression, "ERROR");
    System.out.println(value);
  }
  
  public static void main(String[] args) {
    new WikiTableEval().run(args);
  }
}

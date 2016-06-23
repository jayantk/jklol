package com.jayantkrish.jklol.experiments.wikitables;

import java.util.List;
import java.util.Map;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionExecutor;
import com.jayantkrish.jklol.cli.AbstractCli;

public class WikiTableEval extends AbstractCli {

  private OptionSpec<String> tablesDir;
  private OptionSpec<String> tableId;
  private OptionSpec<String> environment;
  
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
    
    ExpressionExecutor executor = WikiTablesUtil.getExecutor(tables, tableIndexMap,
        options.valuesOf(environment));
    ExpressionParser<Expression2> expParser = ExpressionParser.expression2();

    WikiTable table = tables.get(tableIndexMap.get(options.valueOf(tableId)));
    Expression2 expression = expParser.parse(Joiner.on(" ").join(options.nonOptionArguments()));
    
    Optional<Object> value = executor.evaluateSilent(expression, table.getId());
    if (value.isPresent()) {
      System.out.println(value.get());
    } else {
      System.out.println("Execution error");
    }
  }
  
  public static void main(String[] args) {
    new WikiTableEval().run(args);
  }
}

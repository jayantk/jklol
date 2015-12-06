package com.jayantkrish.jklol.experiments.wikitables;

import java.util.List;
import java.util.Map;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.enumeratelf.BinaryEnumerationRule;
import com.jayantkrish.jklol.ccg.enumeratelf.LogicalFormEnumerator;
import com.jayantkrish.jklol.ccg.enumeratelf.UnaryEnumerationRule;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.RegexTypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda.Type;
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

public class EnumerateLogicalForms extends AbstractCli {
  
  private OptionSpec<String> trainingData;
  private OptionSpec<String> tablesDir;
  private OptionSpec<String> environment;
  private OptionSpec<String> typeDeclaration;

  public EnumerateLogicalForms() {
    super(CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    trainingData = parser.accepts("trainingData").withRequiredArg().ofType(String.class).required();
    tablesDir = parser.accepts("tablesDir").withRequiredArg().ofType(String.class).required();
    environment = parser.accepts("environment").withRequiredArg().ofType(String.class).withValuesSeparatedBy(',');
    typeDeclaration = parser.accepts("typeDeclaration").withRequiredArg().ofType(String.class);
  }

  @Override
  public void run(OptionSet options) {
    List<WikiTable> tables = WikiTablesUtil.readTables(options.valueOf(tablesDir));
    List<WikiTableExample> examples = WikiTablesUtil.readTrainingExamples(
        options.valueOf(trainingData));
    Map<String, Integer> tableIndexMap = Maps.newHashMap();
    for (int i = 0; i < tables.size(); i++) {
      WikiTable table = tables.get(i);
      tableIndexMap.put(table.getId(), i);
    }
    
    // Build environment.
    IndexedList<String> symbolTable = AmbEval.getInitialSymbolTable();
    Environment env = WikiTablesUtil.getEnvironment(symbolTable, tableIndexMap, tables);
    AmbEval eval = new AmbEval(symbolTable);
    ParametricBfgBuilder fgBuilder = new ParametricBfgBuilder(true);
    SExpression program = LispUtil.readProgram(options.valuesOf(environment), symbolTable);
    ExpressionParser<SExpression> sexpParser = ExpressionParser.sExpression(symbolTable);
    eval.eval(program, env, fgBuilder);

    System.out.println("# of tables: " + tables.size());
    System.out.println("# of examples: " + examples.size());
    
    TypeDeclaration types = RegexTypeDeclaration.fromCsv(IoUtils.readLines(options.valueOf(typeDeclaration)));

    ExpressionSimplifier simplifier = WikiTablesUtil.getExpressionSimplifier();
    ExpressionEvaluator evaluator = new AmbEvalEvaluator(sexpParser, eval, env);
    
    // TODO: refactor me.
    LogicalFormEnumerator enumerator = getLogicalFormEnumerator(simplifier, types);
    ExpressionParser<Expression2> expParser = ExpressionParser.expression2();
    for (WikiTableExample example : examples) {
      WikiTable table = tables.get(tableIndexMap.get(example.getTableId()));
      WikiTableMentionAnnotation mentions = WikiTablesUtil.findMentions(example, table);
      
      List<String> mentionStrings = mentions.getMentions();
      List<String> mentionTypes = mentions.getMentionTypes();
      
      List<Expression2> mentionExpressions = Lists.newArrayList();
      for (int i = 0; i < mentionStrings.size(); i++) {
        if (mentionTypes.get(i).equals(WikiTableMentionAnnotation.HEADING)) {
          mentionExpressions.add(expParser.parse("(column-set \"" + mentionStrings.get(i) + "\")"));
        } else if (mentionTypes.get(i).equals(WikiTableMentionAnnotation.VALUE)) {
          mentionExpressions.add(expParser.parse("(cellvalue-set \"" + mentionStrings.get(i) + "\")"));
        }
      }
      
      System.out.println(example.getQuestion());
      List<Expression2> enumerated = enumerator.enumerate(mentionExpressions, 100);
      for (Expression2 e : enumerated) {
        System.out.println(e);
      }
    }
  }
  
  private static LogicalFormEnumerator getLogicalFormEnumerator(ExpressionSimplifier simplifier, 
      TypeDeclaration types) {
    String[][] unaryRules = new String[][] {
        {"c", "(lambda $0 (first-row $0))"},
        {"c", "(lambda $0 (last-row $0))"},
        {"c", "(lambda $0 (set-size $0))"},
        {"c", "(lambda $0 (next-row $0))"},
        {"c", "(lambda $0 (prev-row $0))"},
        {"c", "(lambda $0 (samevalue $0))"},
    };

    String[][] binaryRules = new String[][] {
        {"c", "c", "(lambda $L $R (intersect $L (samerow-set $R)))"},
    };

    ExpressionParser<Type> typeParser = ExpressionParser.typeParser();
    ExpressionParser<Expression2> lfParser = ExpressionParser.expression2();

    List<UnaryEnumerationRule> unaryRuleList = Lists.newArrayList();
    for (int i = 0; i < unaryRules.length; i++) {
      Type type = typeParser.parse(unaryRules[i][0]);
      Expression2 lf = lfParser.parse(unaryRules[i][1]);
      unaryRuleList.add(new UnaryEnumerationRule(type, lf, simplifier, types));
    }
    
    List<BinaryEnumerationRule> binaryRuleList = Lists.newArrayList();
    for (int i = 0; i < binaryRules.length; i++) {
      Type type1 = typeParser.parse(binaryRules[i][0]);
      Type type2 = typeParser.parse(binaryRules[i][1]);
      Expression2 lf = lfParser.parse(binaryRules[i][2]);

      binaryRuleList.add(new BinaryEnumerationRule(type1, type2, lf, simplifier, types));
    }
    
    return new LogicalFormEnumerator(unaryRuleList, binaryRuleList, types);
  }
  
  public static void main(String[] args) {
    new EnumerateLogicalForms().run(args);
  }
}

package com.jayantkrish.jklol.experiments.wikitables;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.enumeratelf.DenotationRuleFilter;
import com.jayantkrish.jklol.ccg.enumeratelf.EnumerationRuleFilter;
import com.jayantkrish.jklol.ccg.enumeratelf.LogicalFormEnumerator;
import com.jayantkrish.jklol.ccg.enumeratelf.LogicalFormEnumerator.CellKey;
import com.jayantkrish.jklol.ccg.enumeratelf.LogicalFormEnumerator.Chart;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.RegexTypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionExecutor;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.util.IoUtils;

public class EnumerateLogicalForms extends AbstractCli {
  
  private OptionSpec<String> trainingData;
  private OptionSpec<String> tablesDir;
  private OptionSpec<String> environment;
  private OptionSpec<String> typeDeclaration;
  private OptionSpec<Void> verbose;

  public EnumerateLogicalForms() {
    super(CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    trainingData = parser.accepts("trainingData").withRequiredArg().ofType(String.class).required();
    tablesDir = parser.accepts("tablesDir").withRequiredArg().ofType(String.class).required();
    environment = parser.accepts("environment").withRequiredArg().ofType(String.class).withValuesSeparatedBy(',');
    typeDeclaration = parser.accepts("typeDeclaration").withRequiredArg().ofType(String.class);
    verbose = parser.accepts("verbose");
  }

  @Override
  public void run(OptionSet options) {
    // Read in data (tables and examples)
    List<WikiTable> tables = WikiTablesUtil.readTables(options.valueOf(tablesDir));
    List<WikiTableExample> examples = WikiTablesUtil.readTrainingExamples(
        options.valueOf(trainingData));
    Map<String, Integer> tableIndexMap = Maps.newHashMap();
    for (int i = 0; i < tables.size(); i++) {
      WikiTable table = tables.get(i);
      tableIndexMap.put(table.getId(), i);
    }
    System.out.println("# of tables: " + tables.size());
    System.out.println("# of examples: " + examples.size());
    
    TypeDeclaration types = RegexTypeDeclaration.fromCsv(IoUtils.readLines(
        options.valueOf(typeDeclaration)));
    ExpressionSimplifier simplifier = WikiTablesUtil.getExpressionSimplifier();
    ExpressionExecutor executor = WikiTablesUtil.getExecutor(tables, tableIndexMap,
        options.valuesOf(environment));

    // TODO: refactor me.
    int numCorrect = 0;
    int numSoFar = 0;
    LogicalFormEnumerator enumerator = getLogicalFormEnumerator(simplifier, types, executor);
    ExpressionParser<Expression2> expParser = ExpressionParser.expression2();
    for (WikiTableExample example : examples) {
      if (numSoFar % 100 == 0) {
        double pct = ((double) numCorrect) / numSoFar;
        System.out.println("Statistics so far: " + numCorrect + "/" + numSoFar + " (" + pct + ")");
      }
      numSoFar++;
      
      WikiTable table = tables.get(tableIndexMap.get(example.getTableId()));
      WikiTableMentionAnnotation mentions = WikiTablesUtil.findMentions(example, table);
      
      List<String> mentionStrings = mentions.getMentions();
      List<String> mentionTypes = mentions.getMentionTypes();
      
      Set<Expression2> mentionExpressions = Sets.newHashSet();
      for (int i = 0; i < mentionStrings.size(); i++) {
        if (mentionTypes.get(i).equals(WikiTableMentionAnnotation.HEADING)) {
          mentionExpressions.add(expParser.parse("(column-id " + Expression2.stringValue(mentionStrings.get(i)) + ")"));
        } else if (mentionTypes.get(i).equals(WikiTableMentionAnnotation.VALUE)) {
          mentionExpressions.add(expParser.parse("(cellvalue-set " + Expression2.stringValue(mentionStrings.get(i)) + ")"));
        }
      }
      
      List<EnumerationRuleFilter> addedFilters = Lists.newArrayList();
      addedFilters.add(new DenotationRuleFilter());
      
      System.out.println(example.getQuestion() + " " + example.getAnswer() + " " + example.getTableId());
      System.out.println("  " + mentionExpressions);
      // List<Expression2> enumerated = enumerator.enumerate(mentionExpressions, addedFilters, 300);
      Chart chart = enumerator.enumerateDp(mentionExpressions, example.getTableId(), 5);
      Expression2 answerExpression = WikiTablesUtil.getAnswerExpression(example);
      Object answer = executor.evaluate(answerExpression.getSubexpressions().get(2));
      Predicate<CellKey> answerPredicate = new AnswerPredicate(executor, example.getTableId(), answer); 
      Set<Expression2> enumerated = chart.getLogicalFormsFromPredicate(answerPredicate, addedFilters);

      for (Expression2 e : enumerated) {
        Optional<Object> value = executor.evaluateSilent(e, example.getTableId());
        System.out.println("  * " + e + " " + value.get());
        numCorrect++;
      }
      
      System.out.println();
    }
  }
  
  private static LogicalFormEnumerator getLogicalFormEnumerator(ExpressionSimplifier simplifier, 
      TypeDeclaration types, ExpressionExecutor executor) {
    String[][] unaryRules = new String[][] {
        {"e", "(lambda ($0) (set-size $0))"},
        {"r", "(lambda ($0) (next-row $0))"},
        {"r", "(lambda ($0) (prev-row $0))"},
        {"r", "(lambda ($0) (row-index $0))"},
    };

    String[][] binaryRules = new String[][] {
        {"c", "e", "(lambda ($L $R) (entities-to-rows $L $R))"},
        {"c", "r", "(lambda ($L $R) (rows-to-entities $L $R))"},
        // {"e", "e", "(lambda ($L $R) (intersect $L (samerow-set $R)))"},
        // {"e", "e", "(lambda ($L $R) (union $L $R))"},
        {"i", "i", "(lambda ($L $R) (- $L $R))"},
    };

    return LogicalFormEnumerator.fromRuleStrings(unaryRules, binaryRules, simplifier, types,
        executor);
  }

  public static void main(String[] args) {
    new EnumerateLogicalForms().run(args);
  }
  
  private static class AnswerPredicate implements Predicate<CellKey> {
    private final ExpressionExecutor executor;
    private final String tableId;
    private final Object answer;
    
    public AnswerPredicate(ExpressionExecutor executor, String tableId, Object answer) {
      this.executor = executor;
      this.tableId = tableId;
      this.answer = answer;
    }

    @Override
    public boolean apply(CellKey o) {
      if (!(o.type.equals(Type.parseFrom("e")) || o.type.equals(Type.parseFrom("i")))) {
        return false;
      }

      Optional<Object> denotationOption = executor.applySilent(
          Expression2.constant("get-values"), tableId, Arrays.asList(o.denotation));

      if (!denotationOption.isPresent()) {
        return false;
      }
      
      Object value = denotationOption.get();

      if (value instanceof Integer) {
        value = Sets.newHashSet(Integer.toString((Integer) value));
      }

      // TODO: may need more sophisticated comparison logic for
      // numerics and yes/no questions.
      return answer.equals(value);
    }
  }
}

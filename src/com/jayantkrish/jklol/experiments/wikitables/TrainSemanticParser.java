package com.jayantkrish.jklol.experiments.wikitables;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.CcgBeamSearchInference;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgFeatureFactory;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.util.SemanticParserExampleLoss;
import com.jayantkrish.jklol.ccg.util.SemanticParserUtils;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.LispUtil;
import com.jayantkrish.jklol.lisp.ParametricBfgBuilder;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.util.IndexedList;

public class TrainSemanticParser extends AbstractCli {
  
  private OptionSpec<String> trainingData;
  private OptionSpec<String> tablesDir;
  
  private OptionSpec<String> environment;

  public TrainSemanticParser() {
    super(CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    trainingData = parser.accepts("trainingData").withRequiredArg().ofType(String.class).required();
    tablesDir = parser.accepts("tablesDir").withRequiredArg().ofType(String.class).required();
    
    environment = parser.accepts("environment").withRequiredArg().ofType(String.class).withValuesSeparatedBy(',');
  }

  @Override
  public void run(OptionSet options) {
    // Read data.
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

    List<String> lexiconLines = Arrays.asList(new String[] {"first,(N{0}/N{0}){1},(lambda f (first f)),0 first"});
    List<String> unknownLexiconLines = Arrays.asList(new String[] {});
    List<String> rules = Arrays.asList(
        new String[] {"DUMMY{0} DUMMY{0}","DUMMY{0} DUMMY{0} DUMMY{0}",
            "N{0} N{1} N{0},(lambda $L $R (intersect $L (samerow-set $R)))"});
    
    CcgFeatureFactory factory = new WikiTablesCcgFeatureFactory(false, true);
    ParametricCcgParser family = ParametricCcgParser.parseFromLexicon(
        lexiconLines, unknownLexiconLines, rules, factory, null, false, null, true);
    
    CcgParser parser = family.getModelFromParameters(family.getNewSufficientStatistics());
    ExpressionSimplifier simplifier = WikiTablesUtil.getExpressionSimplifier();
    ExpressionComparator comparator = new EvaluationComparator(simplifier, sexpParser, eval, env);

    List<CcgExample> ccgExamples = Lists.newArrayList();
    for (WikiTableExample example : examples) {
      ccgExamples.add(WikiTablesUtil.convertExample(example, tables, tableIndexMap));
    }

    CcgInference inference = new CcgBeamSearchInference(null, comparator, 100, -1,
        Integer.MAX_VALUE, 1, false);
    List<SemanticParserExampleLoss> lossAccumulator = Lists.newArrayList();
    SemanticParserUtils.testSemanticParser(ccgExamples, parser, inference, simplifier,
        comparator, lossAccumulator);

    /*
    for () {
      List<CcgParse> parses = parser.beamSearch(sentence, 100);
      System.out.println(example.getQuestion());
      System.out.println(annotation.getMentions());
      System.out.println(table.toTsv());
      if (parses.size() > 0) {
        for (int i = 0; i < Math.min(5, parses.size()); i++) {
          Expression2 lf = simplifier.apply(parses.get(i).getLogicalForm());
          
          SExpression expression = ExpressionParser.sExpression(symbolTable)
              .parseSingleExpression("(eval-table \"" + example.getTableId() + "\" (quote (eval-query " + lf.toString() + ")))");
          Object value = "ERROR";
          try {
            value = eval.eval(expression, env, fgBuilder).getValue();
          } catch (EvalError e) {
            // Don't need to do anything.
          } catch (ClassCastException e) {
            e.printStackTrace();
          }
          
          String correct = example.getAnswer().equals(value) ? "1" : "0";
          System.out.println(correct + " " + lf + " " + value + " " + example.getAnswer());
        }
      }
      System.out.println("\n");
    }
    */
  }
  
  private static void examineData(List<WikiTableExample> examples,
      List<WikiTable> tables, Map<String, Integer> tableIndexMap) {
    int numAnswerable = 0;
    for (WikiTableExample example : examples) {
      WikiTable table = tables.get(tableIndexMap.get(example.getTableId()));
      WikiTableMentionAnnotation annotation = WikiTablesUtil.findMentions(example, table);
      // System.out.println(example.getQuestion());
      
      boolean foundAllAnswers = true;
      for (String answer : example.getAnswer()) {
        List<Cell> cells = Lists.newArrayList();
        for (int i = 0; i < table.getNumRows(); i++) {
          for (int j = 0; j < table.getNumColumns(); j++) {
            if (table.getValue(i,j).equals(answer)) {
              cells.add(new Cell(i, j));
            }
          }
        }

        // System.out.println("  " + answer + ": " + cells);
        if (cells.size() == 0) {
          foundAllAnswers = false;
        }
      }

      /*
      System.out.println("headings: " + annotation.getMentionsWithType(WikiTableMentionAnnotation.HEADING));
      System.out.println("values: " + annotation.getMentionsWithType(WikiTableMentionAnnotation.VALUE));
      System.out.println(table.toTsv());
      */
      
      if (foundAllAnswers) {
        numAnswerable += 1;
      }
    }
    
    System.out.println("answerable: " + numAnswerable);
  }

  public static void main(String[] args) {
    new TrainSemanticParser().run(args);
  }
}

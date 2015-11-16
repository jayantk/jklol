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
import com.jayantkrish.jklol.ccg.CcgLoglikelihoodOracle;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.util.SemanticParserExampleLoss;
import com.jayantkrish.jklol.ccg.util.SemanticParserUtils;
import com.jayantkrish.jklol.ccg.util.SemanticParserUtils.SemanticParserLoss;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.LispUtil;
import com.jayantkrish.jklol.lisp.ParametricBfgBuilder;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.util.IndexedList;

public class TrainSemanticParser extends AbstractCli {
  
  private OptionSpec<String> trainingData;
  private OptionSpec<String> tablesDir;
  private OptionSpec<String> environment;
  
  private OptionSpec<String> trainingLossFile;

  public TrainSemanticParser() {
    super(CommonOptions.MAP_REDUCE, CommonOptions.STOCHASTIC_GRADIENT);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    trainingData = parser.accepts("trainingData").withRequiredArg().ofType(String.class).required();
    tablesDir = parser.accepts("tablesDir").withRequiredArg().ofType(String.class).required();
    environment = parser.accepts("environment").withRequiredArg().ofType(String.class).withValuesSeparatedBy(',');
    
    trainingLossFile = parser.accepts("trainingLossFile").withRequiredArg().ofType(String.class).required();
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

    List<String> lexiconLines = Arrays.asList(new String[] {
        "first,(N{0}/N{0}){1},(lambda f (first-row f)),0 first",
        "last,(N{0}/N{0}){1},(lambda f (last-row f)),0 last"});
    List<String> unknownLexiconLines = Arrays.asList(new String[] {});
    List<String> rules = Arrays.asList(
        new String[] {"DUMMY{0} DUMMY{0}","DUMMY{0} DUMMY{0} DUMMY{0}",
            "N{0} N{1} N{0},(lambda $L $R (intersect $L (samerow-set $R)))"});
    
    CcgFeatureFactory factory = new WikiTablesCcgFeatureFactory(false, true);
    ParametricCcgParser family = ParametricCcgParser.parseFromLexicon(
        lexiconLines, unknownLexiconLines, rules, factory, null, false, null, true);
    
    ExpressionSimplifier simplifier = WikiTablesUtil.getExpressionSimplifier();
    ExpressionComparator comparator = new EvaluationComparator(simplifier, sexpParser, eval, env);
    
    List<CcgExample> ccgExamples = Lists.newArrayList();
    for (WikiTableExample example : examples) {
      ccgExamples.add(WikiTablesUtil.convertExample(example, tables, tableIndexMap));
    }
    
    /*
    examineData(examples, tables, tableIndexMap,
        family.getModelFromParameters(family.getNewSufficientStatistics()),
        comparator);
        */
    
    int beamSize = 100;
    CcgBeamSearchInference inference = new CcgBeamSearchInference(null, comparator, beamSize, -1,
        Integer.MAX_VALUE, 1, false);
    GradientOracle<CcgParser, CcgExample> oracle = new CcgLoglikelihoodOracle(family,
        comparator, inference);

    GradientOptimizer trainer = createGradientOptimizer(ccgExamples.size());
    SufficientStatistics parameters = trainer.train(oracle, oracle.initializeGradient(),
        ccgExamples);

    CcgParser parser = family.getModelFromParameters(parameters);
    
    List<SemanticParserExampleLoss> lossAccumulator = Lists.newArrayList();
    SemanticParserLoss loss = SemanticParserUtils.testSemanticParser(ccgExamples,
        parser, inference, simplifier, comparator, lossAccumulator, false);
    
    System.out.println("Training results: ");
    System.out.println("Precision: " + loss.getPrecision());
    System.out.println("Recall: " + loss.getRecall());
    System.out.println("Oracle Recall @ " + beamSize +": " + loss.getLexiconRecall());
    
    SemanticParserExampleLoss.writeJsonToFile(options.valueOf(trainingLossFile), lossAccumulator);
  }
  
  private static void examineData(List<WikiTableExample> examples,
      List<WikiTable> tables, Map<String, Integer> tableIndexMap,
      CcgParser parser, ExpressionComparator comparator) {
    int numAnswerable = 0;
    for (WikiTableExample example : examples) {
      WikiTable table = tables.get(tableIndexMap.get(example.getTableId()));
      WikiTableMentionAnnotation annotation = WikiTablesUtil.findMentions(example, table);
      CcgExample ccgExample = WikiTablesUtil.convertExample(example, tables, tableIndexMap);

      System.out.println(example.getQuestion());
      System.out.println(table.toTsv());

      List<CcgParse> parses = parser.beamSearch(ccgExample.getSentence(), 100);
      for (int i = 0; i < Math.min(10, parses.size()); i++) {
        comparator.equals(parses.get(i).getLogicalForm(), ccgExample.getLogicalForm());
      }
    }  
  }
  
  public static void main(String[] args) {
    new TrainSemanticParser().run(args);
  }
}

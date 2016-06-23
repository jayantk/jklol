package com.jayantkrish.jklol.experiments.wikitables;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.CommutativeReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.VariableCanonicalizationReplacementRule;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.AmbEval.RaisedBuiltinFunction;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.LispUtil;
import com.jayantkrish.jklol.lisp.ParametricBfgBuilder;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.util.CsvParser;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IoUtils;

/**
 * Static utility methods for the WikiTables data set.
 * 
 * @author jayantk
 *
 */
public class WikiTablesUtil {

  public static List<WikiTable> readTables(String dataDir) {
    File file = new File(dataDir);
    
    List<WikiTable> tables = Lists.newArrayList();
    for (File subdir : file.listFiles()) {
      if (subdir.isDirectory()) {
        for (File tableFile : subdir.listFiles()) {
          if (tableFile.getName().endsWith(".csv")) {
            String id = "csv/" + subdir.getName() + "/" + tableFile.getName();
            tables.add(WikiTable.fromCsvFile(id, tableFile.getAbsolutePath()));
          }
        }
      }
    }
    return tables;
  }
  
  public static List<WikiTableExample> readTrainingExamples(String tsvFile) {
    CsvParser parser = CsvParser.tsvParser();
    List<String> lines = IoUtils.readLines(tsvFile);
    List<WikiTableExample> examples = Lists.newArrayList();
    for (String line : lines) {
      String[] parts = parser.parseLine(line);
      Set<String> answers = Sets.newHashSet(Arrays.copyOfRange(parts, 3, parts.length));
      examples.add(new WikiTableExample(parts[0], parts[1], parts[2], answers));
    }
    return examples;
  }

  public static WikiTableMentionAnnotation findMentions(WikiTableExample example,
      WikiTable table) {
    String question = example.getQuestion();
    TokenizedQuestion tokenized = WikiTablesUtil.tokenize(question);
    int[] tokenStartInts = Ints.toArray(tokenized.getTokenStartIndexes());
    int[] tokenEndInts = Ints.toArray(tokenized.getTokenEndIndexes());

    List<String> mentions = Lists.newArrayList();
    List<String> mentionTypes = Lists.newArrayList();
    List<Integer> tokenStarts = Lists.newArrayList();
    List<Integer> tokenEnds = Lists.newArrayList();
    String[] headings = table.getHeadings();
    for (int i = 0; i < headings.length; i++) {
      String heading = headings[i];
      if (heading.length() > 0) {
        int index = question.indexOf(heading.toLowerCase());
      
        while (index != -1) {
          int start = findTokenWithCharIndex(tokenStartInts, index);
          int end = findTokenWithCharIndex(tokenEndInts, index + heading.length()) + 1;
          
          mentions.add(heading);
          mentionTypes.add(WikiTableMentionAnnotation.HEADING);
          tokenStarts.add(start);
          tokenEnds.add(end);
          
          index = question.indexOf(heading.toLowerCase(), index + 1);
        }
      }
    }
    
    String[][] rows = table.getRows();
    for (int i = 0; i < rows.length; i++) {
      for (int j = 0; j < rows[i].length; j++) {
        String value = rows[i][j];
        if (value.length() > 0) {
          int index = question.indexOf(value.toLowerCase());
        
          while (index != -1) {
            int start = findTokenWithCharIndex(tokenStartInts, index);
            int end = findTokenWithCharIndex(tokenEndInts, index + value.length()) + 1;

            mentions.add(value);
            mentionTypes.add(WikiTableMentionAnnotation.VALUE);
            tokenStarts.add(start);
            tokenEnds.add(end);

            index = question.indexOf(value.toLowerCase(), index + 1);
          }
        }
      }
    }
    
    return new WikiTableMentionAnnotation(mentions, mentionTypes, tokenStarts, tokenEnds);
  }
  
  public static int findTokenWithCharIndex(int[] tokenStarts, int charIndex) {
    int index = Arrays.binarySearch(tokenStarts, charIndex);
    if (index >= 0) {
      return index;
    } else {
      return -1 * (index + 1); 
    }
  }
  
  public static TokenizedQuestion tokenize(String question) {
    List<Integer> tokenStartIndexes = Lists.newArrayList();
    List<Integer> tokenEndIndexes = Lists.newArrayList();

    boolean inToken = true;
    tokenStartIndexes.add(0);
    for (int i = 0; i < question.length(); i++) {
      String substring = question.substring(i, i+1);
      if (substring.matches("[ ,.?'\"]")) {
        if (inToken) {
          tokenEndIndexes.add(i);
          inToken = false;
        }
      } else if (!inToken) {
        tokenStartIndexes.add(i);
        inToken = true;
      }
    }
    if (inToken) {
      tokenEndIndexes.add(question.length());
    }
    
    List<String> tokens = Lists.newArrayList();
    for (int i = 0; i < tokenStartIndexes.size(); i++) {
      tokens.add(question.substring(tokenStartIndexes.get(i), tokenEndIndexes.get(i)));
    }
    return new TokenizedQuestion(tokens, tokenStartIndexes, tokenEndIndexes);
    // String[] parts = question.split("[ ,.?']");
    // return Arrays.asList(parts);
  }
  
  public static ExpressionSimplifier getExpressionSimplifier() {
    List<ExpressionReplacementRule> rules = Lists.newArrayList();
    rules.add(new LambdaApplicationReplacementRule());
    rules.add(new VariableCanonicalizationReplacementRule());
    rules.add(new CommutativeReplacementRule("and"));
    return new ExpressionSimplifier(rules);
  }
  
  public static CcgExample convertExample(WikiTableExample example, List<WikiTable> tables,
      Map<String, Integer> tableIndexMap) {
    TokenizedQuestion tokenizedQuestion = WikiTablesUtil.tokenize(example.getQuestion());
    WikiTable table = tables.get(tableIndexMap.get(example.getTableId()));
    WikiTableMentionAnnotation annotation = WikiTablesUtil.findMentions(example, table);

    List<String> tokens = tokenizedQuestion.getTokens();
    List<String> pos = Collections.nCopies(tokens.size(), ParametricCcgParser.DEFAULT_POS_TAG);
    AnnotatedSentence sentence = new AnnotatedSentence(tokens, pos);
    sentence = sentence.addAnnotation(WikiTableMentionAnnotation.NAME, annotation);

    Expression2 label = getAnswerExpression(example);
    return new CcgExample(sentence, null, null, label);
  }
  
  public static Expression2 getAnswerExpression(WikiTableExample example) {
    List<Expression2> set = Lists.newArrayList();
    set.add(Expression2.constant("make-set"));
    set.addAll(Expression2.stringValues(Lists.newArrayList(example.getAnswer())));
    
    List<Expression2> labels = Lists.newArrayList();
    labels.add(Expression2.constant("label"));
    labels.add(Expression2.constant(example.getTableId()));
    labels.add(Expression2.nested(set));

    return Expression2.nested(labels);
  }
  
  public static Expression2 getQueryExpression(String tableId, Expression2 expression) {
    return ExpressionParser.expression2().parse("(eval-table \""
        + tableId + "\" (quote (get-values " + expression + ")))");
  }
  
  public static Environment getEnvironment(IndexedList<String> symbolTable,
      Map<String, Integer> tableIdMap, List<WikiTable> tables) {
    Environment env = AmbEval.getDefaultEnvironment(symbolTable);
    
    // Bind the table functions
    env.bindName("get-table", new RaisedBuiltinFunction(new WikiTableFunctions.GetTable(tableIdMap, tables)), symbolTable);
    env.bindName("get-table-col", new RaisedBuiltinFunction(new WikiTableFunctions.GetTableCol()), symbolTable);
    env.bindName("get-table-cells", new RaisedBuiltinFunction(new WikiTableFunctions.GetTableCells()), symbolTable);
    env.bindName("get-row-cells", new RaisedBuiltinFunction(new WikiTableFunctions.GetRowCells()), symbolTable);
    env.bindName("get-col-cells", new RaisedBuiltinFunction(new WikiTableFunctions.GetColCells()), symbolTable);
    env.bindName("get-cell", new RaisedBuiltinFunction(new WikiTableFunctions.GetCell()), symbolTable);
    env.bindName("get-col", new RaisedBuiltinFunction(new WikiTableFunctions.GetCol()), symbolTable);
    env.bindName("get-row", new RaisedBuiltinFunction(new WikiTableFunctions.GetRow()), symbolTable);
    env.bindName("get-value", new RaisedBuiltinFunction(new WikiTableFunctions.GetValue()), symbolTable);
    env.bindName("set-filter", new RaisedBuiltinFunction(new WikiTableFunctions.SetFilter()), symbolTable);
    env.bindName("set-map", new RaisedBuiltinFunction(new WikiTableFunctions.SetMap()), symbolTable);
    env.bindName("set-size", new RaisedBuiltinFunction(new WikiTableFunctions.SetSize()), symbolTable);
    env.bindName("set-min", new RaisedBuiltinFunction(new WikiTableFunctions.SetMin()), symbolTable);
    env.bindName("set-max", new RaisedBuiltinFunction(new WikiTableFunctions.SetMax()), symbolTable);
    env.bindName("set-union", new RaisedBuiltinFunction(new WikiTableFunctions.SetUnion()), symbolTable);
    env.bindName("set-contains?", new RaisedBuiltinFunction(new WikiTableFunctions.SetContains()), symbolTable);
    env.bindName("make-set", new RaisedBuiltinFunction(new WikiTableFunctions.MakeSet()), symbolTable);
    env.bindName("set?", new RaisedBuiltinFunction(new WikiTableFunctions.IsSet()), symbolTable);
    return env;
  }
  
  public static WikiTableExecutor getExecutor(List<WikiTable> tables, Map<String, Integer> tableIndexMap,
      List<String> lispPaths) {
    IndexedList<String> symbolTable = AmbEval.getInitialSymbolTable();
    Environment env = WikiTablesUtil.getEnvironment(symbolTable, tableIndexMap, tables);
    AmbEval eval = new AmbEval(symbolTable);
    ParametricBfgBuilder fgBuilder = new ParametricBfgBuilder(true);
    SExpression program = LispUtil.readProgram(lispPaths, symbolTable);
    ExpressionParser<SExpression> sexpParser = ExpressionParser.sExpression(symbolTable);
    eval.eval(program, env, fgBuilder);
    return new WikiTableExecutor(sexpParser, eval, env);
  }
  
  private WikiTablesUtil() {
    // Prevent instantiation.
  }
}

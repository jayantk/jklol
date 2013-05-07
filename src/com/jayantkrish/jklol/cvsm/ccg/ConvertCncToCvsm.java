package com.jayantkrish.jklol.cvsm.ccg;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.io.NullOutputStream;
import com.jayantkrish.jklol.ccg.lambda.ApplicationExpression;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.cvsm.ccg.CcgLfReader.LogicalFormConversionError;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IoUtils;
import com.jayantkrish.jklol.util.Pair;

public class ConvertCncToCvsm extends AbstractCli {

  private OptionSpec<String> cncParses;
  private OptionSpec<String> mentions;
  private OptionSpec<String> relationDictionary;
  private OptionSpec<String> lfTemplates;
  private OptionSpec<Integer> maxSpanLength;

  private OptionSpec<String> trainingOut;
  private OptionSpec<String> validationOut;
  private OptionSpec<Integer> validationNum;

  private OptionSpec<Void> generateSubexpressionExamples;
  private OptionSpec<Void> brief;

  private CcgLfReader reader;

  public ConvertCncToCvsm() {
    super();
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    cncParses = parser.accepts("cncParses").withRequiredArg().ofType(String.class).required();
    mentions = parser.accepts("mentions").withRequiredArg().ofType(String.class).required();
    relationDictionary = parser.accepts("relationDictionary").withRequiredArg().ofType(String.class).required();
    lfTemplates = parser.accepts("lfTemplates").withRequiredArg().ofType(String.class).required();
    maxSpanLength = parser.accepts("maxSpanLength").withRequiredArg().ofType(Integer.class).defaultsTo(Integer.MAX_VALUE - 1);

    trainingOut = parser.accepts("trainingOut").withRequiredArg().ofType(String.class).required();
    validationOut = parser.accepts("validationOut").withRequiredArg().ofType(String.class).defaultsTo("");
    validationNum = parser.accepts("validationNum").withRequiredArg().ofType(Integer.class).defaultsTo(0);

    generateSubexpressionExamples = parser.accepts("generateSubexpressionExamples");
    brief = parser.accepts("brief");
  }

  @Override
  public void run(OptionSet options) {
    if (options.has(brief)) {
      System.setErr(new PrintStream(new NullOutputStream()));
    }

    reader = CcgLfReader.parseFrom(IoUtils.readLines(options.valueOf(lfTemplates)));
    ExpressionParser exp = new ExpressionParser();

    List<RelationExtractionExample> examples = readExamples(IoUtils.readLines(options.valueOf(mentions)));
    IndexedList<String> relDict = IndexedList.create(IoUtils.readLines(options.valueOf(relationDictionary)));

    List<String> lines = IoUtils.readLines(options.valueOf(cncParses));
    Expression ccgExpression = null;
    List<Expression> wordExpressions = null;
    List<List<Expression>> expressions = Lists.newArrayList();
    for (String line : lines) {
      if (!line.startsWith("(")) {
        continue;
      }

      if (line.startsWith("(ccg")) {
        if (ccgExpression != null) {
          int parseNum = Integer.parseInt(((ConstantExpression) ((ApplicationExpression) ccgExpression).getArguments().get(0)).getName());
          while (expressions.size() < parseNum - 1) {
            expressions.add(Lists.<Expression>newArrayList());
          }
          RelationExtractionExample sentence = examples.get(parseNum - 1);
          expressions.add(convertExpression(sentence, ccgExpression, wordExpressions,
					    options.has(generateSubexpressionExamples), options.valueOf(maxSpanLength)));
        }
        ccgExpression = exp.parseSingleExpression(line);
        wordExpressions = Lists.newArrayList();
      } else if (line.startsWith("(w")) {
        wordExpressions.add(exp.parseSingleExpression(line));
      }
    }

    if (ccgExpression != null) {
      int parseNum = Integer.parseInt(((ConstantExpression) ((ApplicationExpression) ccgExpression).getArguments().get(0)).getName());
      while (expressions.size() < parseNum - 1) {
        expressions.add(Lists.<Expression>newArrayList());
      }
      RelationExtractionExample sentence = examples.get(parseNum - 1);
      expressions.add(convertExpression(sentence, ccgExpression, wordExpressions,
					options.has(generateSubexpressionExamples), options.valueOf(maxSpanLength)));
    }

    System.err.println("expressions: " + expressions.size() + " examples: " + examples.size());

    writeData(expressions, examples, relDict, options.valueOf(trainingOut),
        options.valueOf(validationOut), options.valueOf(validationNum));
  }

  private static void writeData(List<List<Expression>> expressions, List<RelationExtractionExample> examples,
      IndexedList<String> relDict, String trainingFilename, String validationFilename, int validationModulo) {
    PrintWriter trainingOut = null, validationOut = null;
    try {
      trainingOut = new PrintWriter(new FileWriter(trainingFilename));
      validationOut = validationFilename != null ? new PrintWriter(new FileWriter(validationFilename)) : null;

      Preconditions.checkArgument(examples.size() == expressions.size());
      for (int i = 0; i < examples.size(); i++) {
        RelationExtractionExample example = examples.get(i);

        if (validationModulo > 0 && i % validationModulo == 0) {
          if (expressions.get(i).size() > 0) {
            // For validation, only print the first expression, which contains
            // all of the subexpressions.
            validationOut.print("\"" + expressions.get(i).get(0) + "\",\"" + example.getLabelDistribution(relDict) + "\"\n");
          }
        } else {
          for (Expression expression : expressions.get(i)) {
            trainingOut.print("\"" + expression + "\",\"" + example.getLabelDistribution(relDict) + "\"\n");
          }
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      if (trainingOut != null) { trainingOut.close(); }
      if (validationOut != null) { validationOut.close(); }
    }
  }

  private List<RelationExtractionExample> readExamples(List<String> lines) {
    List<RelationExtractionExample> examples = Lists.newArrayList();
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line.matches("^[0-9].*")) {
        String label = lines.get(i + 1);

        String sentence = line.split("\t")[1];
        sentence = sentence.replaceFirst("^\\s*\"(.*)\"\\s*$", "$1");        
        examples.add(new RelationExtractionExample(sentence, label));
      }
    }
    return examples;
  }

  private List<Expression> convertExpression(RelationExtractionExample example, Expression ccgExpression, 
					     List<Expression> initialWordExpressions, boolean generateSubexpressionExamples, int maxSpanLength) {
    int parseNum = Integer.parseInt(((ConstantExpression) ((ApplicationExpression) ccgExpression).getArguments().get(0)).getName());
    ccgExpression = ((ApplicationExpression) ccgExpression).getArguments().get(1);

    List<Expression> wordExpressions = Lists.newArrayList(initialWordExpressions);
    for (Expression wordExpression : initialWordExpressions) {
      int wordParseNum = Integer.parseInt(((ConstantExpression) 
          ((ApplicationExpression) wordExpression).getArguments().get(0)).getName());

      if (wordParseNum != parseNum) {
        wordExpressions.remove(wordExpression);
      }
    }

    // Find the marked mentions to identify the sentence span to retain.
    Span e1Span = mapSpanToTokenizedSpan(example.getE1Span(), wordExpressions);
    Span e2Span = mapSpanToTokenizedSpan(example.getE2Span(), wordExpressions);
    if (e1Span == null || e2Span == null) {
      System.err.println("No conversion: no span for " + example.getE1Span() + " and " + example.getE2Span());
      return Lists.<Expression>newArrayList();
    }
    Expression spanningExpression = reader.findSpanningExpression(ccgExpression, e1Span.getStart(), e2Span.getEnd());
    if (spanningExpression == null) {
      System.err.println("No conversion: no atomic type: " + ccgExpression);
      return Lists.<Expression>newArrayList();
    }

    Pair<Integer, Integer> parseSpan = reader.getExpressionSpan(spanningExpression);
    List<String> wordsInSpan = getWordsInSpan(parseSpan.getLeft(), parseSpan.getRight(), wordExpressions);
    spanningExpression = reader.pruneModifiers(spanningExpression, Arrays.asList(e1Span, e2Span));

    try {
      reader.parse(spanningExpression, wordExpressions);
    } catch (LogicalFormConversionError error) {
      System.err.println("No conversion. " + error.getMessage());
      return Lists.<Expression>newArrayList();
    }

    List<String> wordsInParse = reader.getWordsInCcgParse(spanningExpression, wordExpressions);
    if (parseSpan.getRight() - parseSpan.getLeft() > maxSpanLength) {
      System.err.println("Span too big: " + wordsInSpan);
      return Lists.<Expression>newArrayList();
    }
    System.out.println(wordsInSpan + " " + wordsInParse);

    List<Expression> subexpressions = null;
    if (generateSubexpressionExamples) {
      subexpressions = reader.findAtomicSubexpressions(spanningExpression);
    } else {
      subexpressions = Lists.newArrayList(spanningExpression);
    }

    List<Expression> exampleExpressions = Lists.newArrayList();
    for (Expression subexpression : subexpressions) {
      try {
        Expression parsedExpression = reader.parse(subexpression, wordExpressions);
        parsedExpression = new ApplicationExpression(new ConstantExpression("op:softmax"),
            Arrays.asList(new ApplicationExpression(new ConstantExpression("op:add"), 
                Arrays.asList(new ApplicationExpression(new ConstantExpression("op:matvecmul"),
                    Arrays.asList(new ConstantExpression("weights:softmax"), parsedExpression)),
                    new ConstantExpression("weights:softmax_bias")))));

        exampleExpressions.add(parsedExpression.simplify());
      } catch (LogicalFormConversionError error) {
        System.err.println("No conversion. " + error.getMessage());
      }
    }

    return exampleExpressions;
  }

  private List<String> getWordsInSpan(int start, int end, List<Expression> wordExpressions) {
    List<String> words = Lists.newArrayList();
    for (int i = start; i < end; i++) {
      ApplicationExpression app = (ApplicationExpression) wordExpressions.get(i);
      words.add(((ConstantExpression) app.getArguments().get(2)).getName().replaceAll("^\"(.*)\"", "$1"));
    }
    return words;
  }

  private Span mapSpanToTokenizedSpan(Span span, List<Expression> wordExpressions) {
    String firstWord = span.getWords().get(0);
    for (int i = span.getStart(); i < wordExpressions.size(); i++) {
      ApplicationExpression app = (ApplicationExpression) wordExpressions.get(i);
      String word = ((ConstantExpression) app.getArguments().get(2)).getName().replaceAll("^\"(.*)\"", "$1");

      if (word.equals(firstWord)) {
        return new Span(i, i + span.getSize(), span.getWords());
      }
    }
    return null;
  }

  public static void main(String[] args) {
    new ConvertCncToCvsm().run(args);
  }

  public static class RelationExtractionExample {
    private final String sentence;
    private final String[] words;
    private final String label;

    public RelationExtractionExample(String sentence, String label) {
      this.sentence = sentence.replaceAll("([^ ])(<e[0-9]>)", "$1 $2").replaceAll("(</e[0-9]>)([^ ])", "$1 $2");
      String sentenceWithoutEntities = sentence.replaceAll("(.)</?e[0-9]>", "$1 ").replaceAll("</?e[0-9]>", "");
      words = sentenceWithoutEntities.split("  *");
      this.label = label;
    }

    public String getSentence() {
      return sentence;
    }

    public Span getSentenceSpan(int spanStart, int spanEnd) {
      return new Span(spanStart, spanEnd, Lists.newArrayList(Arrays.copyOfRange(words, spanStart, spanEnd)));
    }

    public Span getE1Span() {
      int start = getStringWordIndex("<e1>");
      int end = getStringWordIndex("</e1>");

      return new Span(start, end, Lists.newArrayList(Arrays.copyOfRange(words, start, end)));
    }

    public Span getE2Span() {
      int start = getStringWordIndex("<e2>");
      int end = getStringWordIndex("</e2>");

      return new Span(start, end, Lists.newArrayList(Arrays.copyOfRange(words, start, end)));
    }

    private int getStringWordIndex(String string) {
      int index = sentence.indexOf(string);
      if (index == 0) {
        return index;
      } else {
        return sentence.substring(0, sentence.indexOf(string)).split(" ").length;
      }
    }

    public String getLabel() {
      return label;
    }

    public String getLabelDistribution(IndexedList<String> relDict) {
      int index = relDict.getIndex(label);
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < relDict.size(); i++) {
        if (i != 0) {
          sb.append(",");
        }
        sb.append(i == index ? "1" : "0");
      }
      return sb.toString();
    }
  }

  public static class Span {
    private final int start;
    private final int end;

    private final List<String> words;

    public Span(int start, int end, List<String> words) {
      this.start = start;
      this.end = end;
      this.words = words;

      Preconditions.checkArgument(words.size() == (end - start));
    }

    public int getStart() {
      return start;
    }

    public int getEnd() {
      return end;
    }

    public int getSize() {
      return end - start;
    }

    public List<String> getWords() {
      return words;
    }

    @Override
    public String toString() {
      return start + "," + end + ":" + words;
    }
  }
}

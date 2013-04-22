package com.jayantkrish.jklol.cvsm.ccg;

import java.util.Arrays;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.lambda.ApplicationExpression;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.cvsm.ccg.CcgLfReader.LogicalFormConversionError;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IoUtils;

public class ConvertCncToCvsm extends AbstractCli {
  
  private OptionSpec<String> cncParses;
  private OptionSpec<String> mentions;
  private OptionSpec<String> relationDictionary;
  private OptionSpec<String> lfTemplates;
  
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
  }

  @Override
  public void run(OptionSet options) {
    reader = CcgLfReader.parseFrom(IoUtils.readLines(options.valueOf(lfTemplates)));
    ExpressionParser exp = new ExpressionParser();

    List<RelationExtractionExample> examples = readExamples(IoUtils.readLines(options.valueOf(mentions)));
    IndexedList<String> relDict = IndexedList.create(IoUtils.readLines(options.valueOf(relationDictionary)));
    
    List<String> lines = IoUtils.readLines(options.valueOf(cncParses));
    Expression ccgExpression = null;
    List<Expression> wordExpressions = null;
    List<Expression> expressions = Lists.newArrayList();
    for (String line : lines) {
      if (!line.startsWith("(")) {
        continue;
      }
      
      if (line.startsWith("(ccg")) {
        if (ccgExpression != null) {
          int parseNum = Integer.parseInt(((ConstantExpression) ((ApplicationExpression) ccgExpression).getArguments().get(0)).getName());
          RelationExtractionExample sentence = examples.get(parseNum - 1);
          expressions.add(convertExpression(sentence, ccgExpression, wordExpressions, relDict));
        }
        ccgExpression = exp.parseSingleExpression(line);
        wordExpressions = Lists.newArrayList();
      } else if (line.startsWith("(w")) {
        wordExpressions.add(exp.parseSingleExpression(line));
      }
    }

    if (ccgExpression != null) {
      int parseNum = Integer.parseInt(((ConstantExpression) ((ApplicationExpression) ccgExpression).getArguments().get(0)).getName());
      RelationExtractionExample sentence = examples.get(parseNum - 1);
      expressions.add(convertExpression(sentence, ccgExpression, wordExpressions, relDict));
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
  
  private Expression convertExpression(RelationExtractionExample example, Expression ccgExpression, 
      List<Expression> initialWordExpressions, IndexedList<String> relDict) {
    ccgExpression = ((ApplicationExpression) ccgExpression).getArguments().get(1);

    // Find the marked mentions to identify the sentence span to retain.
    Span e1Span = mapSpanToTokenizedSpan(example.getE1Span(), wordExpressions);
    Span e2Span = mapSpanToTokenizedSpan(example.getE2Span(), wordExpressions);
    if (e1Span == null || e2Span == null) {
      System.err.println("No conversion: no span for " + example.getE1Span() + " and " + example.getE2Span());
      return null;
    }
    Expression spanningExpression = reader.findSpanningExpression(ccgExpression, e1Span.getStart(), e2Span.getEnd());
    if (spanningExpression == null) {
      System.err.println("No conversion: no atomic type: " + ccgExpression);
      return null;
    }

    try {
      Expression parsedExpression = reader.parse(spanningExpression, wordExpressions);
      parsedExpression = new ApplicationExpression(new ConstantExpression("op:softmax"), Arrays.asList(new ApplicationExpression(new ConstantExpression("op:matvecmul"), Arrays.asList(new ConstantExpression("weights:softmax"), parsedExpression))));

      System.out.println("\"" + parsedExpression.simplify() + "\",\"" + example.getLabelDistribution(relDict) + "\"");
      return parsedExpression;
    } catch (LogicalFormConversionError error) {
      System.err.println("No conversion. " + error.getMessage());
      return null;
    }
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
    private final String sentenceWithoutEntities;
    private final String label;

    public RelationExtractionExample(String sentence, String label) {
      this.sentence = sentence.replaceAll("([^ ])(<e[0-9]>)", "$1 $2").replaceAll("(</e[0-9]>)([^ ])", "$1 $2");
      this.sentenceWithoutEntities = sentence.replaceAll("(.)</?e[0-9]>", "$1 ").replaceAll("</?e[0-9]>", "");
      this.label = label;
    }

    public String getSentence() {
      return sentence;
    }
    
    public Span getE1Span() {
      int start = getStringWordIndex("<e1>");
      int end = getStringWordIndex("</e1>");
      
      String[] words = sentenceWithoutEntities.split("  *");
      
      return new Span(start, end, Lists.newArrayList(Arrays.copyOfRange(words, start, end)));
    }
    
    public Span getE2Span() {
      int start = getStringWordIndex("<e2>");
      int end = getStringWordIndex("</e2>");
      
      String[] words = sentenceWithoutEntities.split("  *");
      
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

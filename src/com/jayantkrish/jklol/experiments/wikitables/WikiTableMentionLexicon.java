package com.jayantkrish.jklol.experiments.wikitables;

import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.chart.ChartEntry;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lexicon.AbstractCcgLexicon;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;

public class WikiTableMentionLexicon extends AbstractCcgLexicon {
  private static final long serialVersionUID = 1L;

  private final String annotationName;
  private final Map<String, CcgCategory> typeCategoryMap;
  
  public WikiTableMentionLexicon(VariableNumMap terminalVar, String annotationName,
      Map<String, CcgCategory> typeCategoryMap) {
    super(terminalVar);
    this.annotationName = Preconditions.checkNotNull(annotationName);
    this.typeCategoryMap = typeCategoryMap;
  }

  @Override
  public void getLexiconEntries(int spanStart, int spanEnd, AnnotatedSentence sentence,
      ChartEntry[] alreadyGenerated, int numAlreadyGenerated, List<Object> triggerAccumulator,
      List<CcgCategory> accumulator, List<Double> probAccumulator) {
    WikiTableMentionAnnotation annotation = (WikiTableMentionAnnotation) sentence
        .getAnnotation(annotationName);
    List<String> mentions = annotation.getMentions();
    List<String> mentionTypes = annotation.getMentionTypes();
    List<Integer> starts = annotation.getTokenStarts();
    List<Integer> ends = annotation.getTokenEnds();
    for (int i = 0; i < starts.size(); i++) {
      if (spanStart == starts.get(i) && spanEnd == ends.get(i) - 1) {
        CcgCategory c = typeCategoryMap.get(mentionTypes.get(i));
        Expression2 newLf = Expression2.nested(c.getLogicalForm(),
            Expression2.stringValue(mentions.get(i)));
        CcgCategory newCategory = c.replaceLogicalForm(newLf);

        triggerAccumulator.add(mentions.get(i));
        accumulator.add(newCategory);
        probAccumulator.add(1.0);
      }
    }
  }
}

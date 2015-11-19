package com.jayantkrish.jklol.experiments.wikitables;

import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

public class WikiTableExample {

  private final String id;
  private final String questionText;
  private final String tableId;
  private final Set<String> answer;
  
  public WikiTableExample(String id, String questionText, String tableId, Set<String> answer) {
    this.id = Preconditions.checkNotNull(id);
    this.questionText = Preconditions.checkNotNull(questionText);
    this.tableId = Preconditions.checkNotNull(tableId);
    this.answer = Sets.newHashSet(answer);
  }

  public String getId() {
    return id;
  }

  public String getQuestion() {
    return questionText;
  }

  public String getTableId() {
    return tableId;
  }

  public Set<String> getAnswer() {
    return answer;
  }
}

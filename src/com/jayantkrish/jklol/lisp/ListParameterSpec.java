package com.jayantkrish.jklol.lisp;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.IndexedList;

public class ListParameterSpec extends AbstractParameterSpec {

  private final List<ParameterSpec> children;

  public ListParameterSpec(int id, List<ParameterSpec> children) {
    super(id);
    this.children = ImmutableList.copyOf(children);
  }

  @Override
  public SufficientStatistics getCurrentParameters() {
    List<SufficientStatistics> parameters = Lists.newArrayList();
    IndexedList<String> names = IndexedList.create();
    for (ParameterSpec child : children) {
      parameters.add(child.getCurrentParameters());
      names.add(Integer.toString(child.getId()));
    }

    return new ListSufficientStatistics(names, parameters);
  }

  @Override
  public SufficientStatistics getNewParameters() {
    List<SufficientStatistics> parameters = Lists.newArrayList();
    IndexedList<String> names = IndexedList.create();
    for (ParameterSpec child : children) {
      parameters.add(child.getNewParameters());
      names.add(Integer.toString(child.getId()));
    }

    return new ListSufficientStatistics(names, parameters);
  }

  @Override
  public ParameterSpec getParametersById(int id) {
    if (id == getId()) {
      return this;
    } else {
      for (ParameterSpec child : children) {
        ParameterSpec found = child.getParametersById(id);
        if (found != null) {
          return found;
        }
      }
      return null;
    }
  }

  @Override
  public ParameterSpec wrap(SufficientStatistics parameters) {
    List<SufficientStatistics> inputStats = parameters.coerceToList().getStatistics();
    Preconditions.checkArgument(inputStats.size() == children.size());
    List<ParameterSpec> wrapped = Lists.newArrayList();
    for (int i = 0; i < inputStats.size(); i++) {
      wrapped.add(children.get(i).wrap(inputStats.get(i)));
    }
    
    return new ListParameterSpec(getId(), wrapped);
  }
  
  @Override
  public List<Object> toArgumentList() {
    return Lists.<Object>newArrayList(children);
  }
}

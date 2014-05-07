package com.jayantkrish.jklol.lisp;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.cvsm.CvsmSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.ArrayUtils;
import com.jayantkrish.jklol.util.IndexedList;

public class ListParameterSpec extends AbstractParameterSpec {
  private static final long serialVersionUID = 1L;

  private final List<ParameterSpec> children;
  private final int[] childIdIndex;
  private final int[] childIdIndexValues;

  public ListParameterSpec(int id, List<ParameterSpec> children) {
    super(id);
    this.children = ImmutableList.copyOf(children);
    
    List<Integer> childIdIndexList = Lists.newArrayList();
    List<Integer> childIdIndexValueList = Lists.newArrayList();
    for (int i = 0; i < children.size(); i++) {
      int[] childIds = children.get(i).getContainedIds();
      for (int j = 0; j < childIds.length; j++) {
        childIdIndexList.add(childIds[j]);
        childIdIndexValueList.add(i);
      }
    }
    
    this.childIdIndex = Ints.toArray(childIdIndexList);
    this.childIdIndexValues = Ints.toArray(childIdIndexValueList);

    ArrayUtils.sortKeyValuePairs(childIdIndex, childIdIndexValues,
        0, childIdIndex.length);
  }

  private ListParameterSpec(int id, List<ParameterSpec> children,
      int[] childIdIndex, int[] childIdIndexValues) {
    super(id);
    this.children = Preconditions.checkNotNull(children);
    this.childIdIndex = Preconditions.checkNotNull(childIdIndex);
    this.childIdIndexValues = Preconditions.checkNotNull(childIdIndexValues);
  }

  public int[] getContainedIds() {
    return childIdIndex;
  }

  @Override
  public boolean containsId(int candidateId) {
    return Arrays.binarySearch(childIdIndex, candidateId) >= 0;
  }

  @Override
  public SufficientStatistics getCurrentParameters() {
    List<SufficientStatistics> parameters = Lists.newArrayList();
    IndexedList<String> names = IndexedList.create();
    List<Supplier<SufficientStatistics>> suppliers = Lists.newArrayList();
    for (ParameterSpec child : children) {
      parameters.add(child.getCurrentParameters());
      names.add(Integer.toString(child.getId()));
      suppliers.add(new ParameterSpecSupplier(child));
    }

    return new CvsmSufficientStatistics(names, suppliers, parameters);
  }

  @Override
  public SufficientStatistics getNewParameters() {
    List<SufficientStatistics> parameters = Lists.newArrayList();
    IndexedList<String> names = IndexedList.create();
    List<Supplier<SufficientStatistics>> suppliers = Lists.newArrayList();
    for (ParameterSpec child : children) {
      parameters.add(null);
      names.add(Integer.toString(child.getId()));
      suppliers.add(new ParameterSpecSupplier(child));
    }

    return new CvsmSufficientStatistics(names, suppliers, parameters);
  }
  
  /**
   * Gets the ith set of parameters in this list.
   * 
   * @param i
   * @return
   */
  public ParameterSpec get(int i) {
    return children.get(i);
  }

  @Override
  public SufficientStatistics getParametersById(int id, SufficientStatistics parameters) {
    if (id == getId()) {
      return parameters;
    } else {
      int index = Arrays.binarySearch(childIdIndex, id);
      if (index >= 0) {
        CvsmSufficientStatistics cvsmParameters = (CvsmSufficientStatistics) parameters;
        int childIndex = childIdIndexValues[index];
        ParameterSpec child = children.get(childIndex);
        SufficientStatistics childParameters = cvsmParameters.getSufficientStatistics(childIndex);
        return child.getParametersById(id, childParameters);
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

    return new ListParameterSpec(getId(), wrapped, childIdIndex, childIdIndexValues);
  }

  private static class ParameterSpecSupplier implements Supplier<SufficientStatistics> {
    private final ParameterSpec parameterSpec;

    public ParameterSpecSupplier(ParameterSpec parameterSpec) {
      this.parameterSpec = Preconditions.checkNotNull(parameterSpec);
    }

    @Override
    public SufficientStatistics get() {
      return parameterSpec.getNewParameters();
    }
  }
}

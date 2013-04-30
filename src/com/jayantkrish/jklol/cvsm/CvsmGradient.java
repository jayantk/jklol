package com.jayantkrish.jklol.cvsm;

import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensors;
import com.jayantkrish.jklol.util.IndexedList;

public class CvsmGradient {

  private IndexedList<String> tensorNames;
  private List<LowRankTensor> tensors;

  public CvsmGradient() {
    tensorNames = IndexedList.create();
    tensors = Lists.newArrayList();
  }
  
  public void incrementValue(String name, LowRankTensor gradient) {
    if (tensorNames.contains(name)) {
      int index = tensorNames.getIndex(name);
      tensors.set(index, LowRankTensors.elementwiseAddition(tensors.get(index), gradient));
    } else {
      tensorNames.add(name);
      tensors.add(gradient);
    }
  }

  public List<String> getTensorNames() {
    return tensorNames.items();
  }

  public List<LowRankTensor> getTensors() {
    return tensors;
  }
}

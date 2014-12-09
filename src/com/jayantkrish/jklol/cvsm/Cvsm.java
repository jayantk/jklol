package com.jayantkrish.jklol.cvsm;

import java.io.Serializable;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.jayantkrish.jklol.ccg.lambda.ApplicationExpression;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;
import com.jayantkrish.jklol.cvsm.lrt.TensorLowRankTensor;
import com.jayantkrish.jklol.cvsm.tree.CvsmAdditionTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmConstantTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmInnerProductTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmLaplaceSigmoidTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmLogTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmLogisticTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmRelabelDimsTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmSoftmaxTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmTanhTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmTensorTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmTree;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * A compositional vector space model.
 * 
 * @author jayant
 */
public class Cvsm implements Serializable {
  private static final long serialVersionUID = 1L;
  
  private final IndexedList<String> tensorNames;
  private final CvsmParameters tensors;

  public Cvsm(IndexedList<String> tensorNames, CvsmParameters tensors) {
    this.tensorNames = Preconditions.checkNotNull(tensorNames);
    this.tensors = Preconditions.checkNotNull(tensors);
    Preconditions.checkArgument(tensors.size() == tensorNames.size());
  }
  
  public static Cvsm fromTensors(IndexedList<String> tensorNames, List<LowRankTensor> tensors) {
    return new Cvsm(tensorNames, new TensorCvsmParameters(tensors));
  }
  
  public LowRankTensor getTensor(String name) {
    int index = tensorNames.getIndex(name);
    return tensors.get(index);
  }

  public CvsmTree getInterpretationTree(Expression logicalForm) {
    if (logicalForm instanceof ConstantExpression) {
      String value = ((ConstantExpression) logicalForm).getName();
      if (tensorNames.contains(value)) {
        return new CvsmTensorTree(value, getTensor(value));
      } else {
        try {
          double doubleValue = Double.parseDouble(value);
          return new CvsmConstantTree(new TensorLowRankTensor(DenseTensor.scalar(doubleValue)));
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("Unknown parameter name: " + value);
        }
      } 
    } else if (logicalForm instanceof ApplicationExpression) {
      ApplicationExpression app = ((ApplicationExpression) logicalForm);
      String functionName = ((ConstantExpression) app.getFunction()).getName();
      List<Expression> args = app.getArguments();
      if (functionName.equals("op:matvecmul")) {
        // Tensor-vector multiplication. First argument is tensor, remaining arguments are
        // multiplied into the the first.
        Preconditions.checkArgument(args.size() >= 1);
        
        CvsmTree result = getInterpretationTree(args.get(0));
        for (int j = 1; j < args.size(); j++) {
          CvsmTree vectorTree = getInterpretationTree(args.get(j));
          result = new CvsmInnerProductTree(result, vectorTree);
        
          BiMap<Integer, Integer> relabeling = HashBiMap.create();
          int[] tensorDims = result.getValue().getDimensionNumbers();
          for (int i = 0; i < tensorDims.length; i++) {
            relabeling.put(tensorDims[i], i);
          }
          result = new CvsmRelabelDimsTree(result, relabeling);
        }
        return result;
      } else if (functionName.equals("op:softmax")) {
        Preconditions.checkArgument(args.size() == 1);

        CvsmTree subtree = getInterpretationTree(args.get(0));
        return CvsmSoftmaxTree.create(subtree);
      } else if (functionName.equals("op:log")) {
        Preconditions.checkArgument(args.size() == 1);

        CvsmTree subtree = getInterpretationTree(args.get(0));
        return CvsmLogTree.create(subtree);
      } else if (functionName.equals("op:logistic")) {
        Preconditions.checkArgument(args.size() == 1);

        CvsmTree subtree = getInterpretationTree(args.get(0));
        return new CvsmLogisticTree(subtree);
      } else if (functionName.equals("op:tanh")) {
        Preconditions.checkArgument(args.size() == 1);

        CvsmTree subtree = getInterpretationTree(args.get(0));
        return new CvsmTanhTree(subtree);
      } else if (functionName.equals("op:laplace")) {
        Preconditions.checkArgument(args.size() == 1);

        CvsmTree subtree = getInterpretationTree(args.get(0));
        return new CvsmLaplaceSigmoidTree(subtree, 2);
      } else if (functionName.equals("op:add")) {
        Preconditions.checkArgument(args.size() > 1, "Addition requires more than 1 argument.");
        CvsmTree value = getInterpretationTree(args.get(0));
        for (int i = 1; i < args.size(); i++) {
          value = new CvsmAdditionTree(value, getInterpretationTree(args.get(i)));
        }
        return value;
      }

      throw new IllegalArgumentException("Unknown function name: " + functionName);
    } else {
      throw new IllegalArgumentException("Unknown expression type: " + logicalForm);
    }
  }
  
  public static interface CvsmParameters extends Serializable {
    public LowRankTensor get(int index);
    
    public int size();
  }
  
  public static class TensorCvsmParameters implements CvsmParameters {
    private static final long serialVersionUID = 1L;

    private final List<LowRankTensor> tensors;
    
    public TensorCvsmParameters(List<LowRankTensor> tensors) {
      this.tensors = ImmutableList.copyOf(tensors);
    }

    public LowRankTensor get(int index) {
      return tensors.get(index);
    }
    
    public int size() {
      return tensors.size();
    }
  }
}

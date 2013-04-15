package com.jayantkrish.jklol.cvsm;

import java.io.Serializable;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.jayantkrish.jklol.ccg.lambda.ApplicationExpression;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * A compositional vector space model.
 * 
 * @author jayant
 */
public class Cvsm implements Serializable {
  private static final long serialVersionUID = 1L;
  
  private final IndexedList<String> tensorNames;
  private final List<LowRankTensor> tensors;

  public Cvsm(IndexedList<String> tensorNames, List<LowRankTensor> tensors) {
    this.tensorNames = Preconditions.checkNotNull(tensorNames);
    this.tensors = Preconditions.checkNotNull(tensors);
    Preconditions.checkArgument(tensors.size() == tensorNames.size());
  }
  
  public CvsmTree getInterpretationTree(Expression logicalForm) {
    if (logicalForm instanceof ConstantExpression) {
      String value = ((ConstantExpression) logicalForm).getName();
      Preconditions.checkArgument(tensorNames.contains(value), "Unknown parameter name: %s", value);
      return new CvsmTensorTree(value, tensors.get(tensorNames.getIndex(value)));
    } else if (logicalForm instanceof ApplicationExpression) {
      ApplicationExpression app = ((ApplicationExpression) logicalForm);
      String functionName = ((ConstantExpression) app.getFunction()).getName();
      List<Expression> args = app.getArguments();
      if (functionName.equals("op:matvecmul")) {
        // Tensor-vector multiplication. First argument is tensor, second is vector.
        Preconditions.checkArgument(args.size() == 2);
        
        CvsmTree tensorTree = getInterpretationTree(args.get(0));
        CvsmTree vectorTree = getInterpretationTree(args.get(1));
        
        CvsmTree result = new CvsmInnerProductTree(tensorTree, vectorTree);
        
        BiMap<Integer, Integer> relabeling = HashBiMap.create();
        int[] tensorDims = result.getValue().getDimensionNumbers();
        for (int i = 0; i < tensorDims.length; i++) {
          relabeling.put(tensorDims[i], i);
        }
        result = new CvsmRelabelDimsTree(result, relabeling);
        return result;
      } else if (functionName.equals("op:softmax")) {
        Preconditions.checkArgument(args.size() == 1);

        CvsmTree subtree = getInterpretationTree(args.get(0));
        return CvsmSoftmaxTree.create(subtree);
      } else if (functionName.equals("op:logistic")) {
        Preconditions.checkArgument(args.size() == 1);

        CvsmTree subtree = getInterpretationTree(args.get(0));
        return new CvsmLogisticTree(subtree);
      } else if (functionName.equals("op:add")) {
        Preconditions.checkArgument(args.size() == 2);
        
        CvsmTree left = getInterpretationTree(args.get(0));
        CvsmTree right = getInterpretationTree(args.get(1));
        return new CvsmAdditionTree(left, right);
      }

      throw new IllegalArgumentException("Unknown function name: " + functionName);
    } else {
      throw new IllegalArgumentException("Unknown expression type: " + logicalForm);
    }
  }
}

package com.jayantkrish.jklol.tensor;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.tensor.TensorProtos.TensorProto;

/**
 * Utility methods for operating on {@link Tensor}s.
 * 
 * @author jayantk
 */
public class Tensors {

  public static Tensor fromProto(TensorProto proto) {
    switch (proto.getType().getNumber()) {
    case TensorProto.TensorType.DENSE_VALUE:
      Preconditions.checkArgument(proto.hasDenseTensor());
      return DenseTensor.fromProto(proto.getDenseTensor());
    case TensorProto.TensorType.SPARSE_VALUE:
      Preconditions.checkArgument(proto.hasSparseTensor());
      return SparseTensor.fromProto(proto.getSparseTensor());
    default:
      throw new IllegalArgumentException("Invalid tensor type: " + proto.getType());
    }
  }
}

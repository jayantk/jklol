package com.jayantkrish.jklol.cvsm.eval;

import java.util.List;

import com.google.common.base.Preconditions;

public class BuiltinFunctions {

  public static class ConsFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Eval eval) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      return new ConsValue(argumentValues.get(0), argumentValues.get(1));
    }
  }

  public static class CarFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Eval eval) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      return ((ConsValue) argumentValues.get(0)).getCar();
    }
  }

  public static class CdrFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Eval eval) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      return ((ConsValue) argumentValues.get(0)).getCdr();
    }
  }
  
  public static class ListFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Eval eval) {
      int numValues = argumentValues.size();
      Object listValue = ConstantValue.NIL;
      for (int i = numValues - 1; i >= 0; i--) {
        listValue = new ConsValue(argumentValues.get(i), listValue);
      }
      return listValue;
    }
  }
  
  public static class NilFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Eval eval) {
      Preconditions.checkArgument(argumentValues.size() == 1, "Wrong number of arguments: " + argumentValues);
      return ConstantValue.NIL.equals(argumentValues.get(0)) ? ConstantValue.TRUE : ConstantValue.FALSE; 
    }
  }

  public static class PlusFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Eval eval) {
      int resultValue = 0;
      for (int i = 0; i < argumentValues.size(); i++) {
        resultValue += (Integer) argumentValues.get(i);
      }
      return resultValue;
    }
  }

  public static class MinusFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Eval eval) {
      int resultValue = 0;
      for (int i = 0; i < argumentValues.size(); i++) {
        if (i == 0) {
          resultValue += (Integer) argumentValues.get(i);
        } else {
          resultValue -= (Integer) argumentValues.get(i);
        }
      }
      return resultValue;
    }
  }
}

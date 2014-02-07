package com.jayantkrish.jklol.lisp;

import java.util.List;

import com.google.common.base.Preconditions;

public class BuiltinFunctions {
  
  public static class ConsFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      return new ConsValue(argumentValues.get(0), argumentValues.get(1));
    }
  }

  public static class CarFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      return ((ConsValue) argumentValues.get(0)).getCar();
    }
  }

  public static class CdrFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      return ((ConsValue) argumentValues.get(0)).getCdr();
    }
  }

  public static class ListFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
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
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 1, "Wrong number of arguments: " + argumentValues);
      return ConstantValue.NIL.equals(argumentValues.get(0)) ? ConstantValue.TRUE : ConstantValue.FALSE; 
    }
  }
  
  public static class NotFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      Object value = argumentValues.get(0);
      Preconditions.checkState(ConstantValue.TRUE.equals(value) || ConstantValue.FALSE.equals(value));
      if (ConstantValue.TRUE.equals(value)) {
        return ConstantValue.FALSE;
      } else {
        return ConstantValue.TRUE;
      }
    }
  }

  public static class AndFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      ConstantValue arg1 = (ConstantValue) argumentValues.get(0);
      ConstantValue arg2 = (ConstantValue) argumentValues.get(1);
      return ConstantValue.fromBoolean(arg1.toBoolean() && arg2.toBoolean());
    }
  }

  public static class OrFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      ConstantValue arg1 = (ConstantValue) argumentValues.get(0);
      ConstantValue arg2 = (ConstantValue) argumentValues.get(1);
      return ConstantValue.fromBoolean(arg1.toBoolean() || arg2.toBoolean());
    }
  }

  public static class PlusFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      int resultValue = 0;
      for (int i = 0; i < argumentValues.size(); i++) {
        resultValue += (Integer) argumentValues.get(i);
      }
      return resultValue;
    }
  }

  public static class MinusFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
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

  public static class MultiplyFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      int resultValue = 1;
      for (int i = 0; i < argumentValues.size(); i++) {
        resultValue *= (Integer) argumentValues.get(i);
      }
      return resultValue;
    }
  }

  public static class DivideFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      return ((Integer) argumentValues.get(0)) / ((Integer) argumentValues.get(1));
    }
  }

  public static class EqualsFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      return argumentValues.get(0).equals(argumentValues.get(1)) ? ConstantValue.TRUE : ConstantValue.FALSE;
    }
  }
  
  public static class DisplayFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      System.out.println(argumentValues.get(0));
      return ConstantValue.UNDEFINED;
    }
  }
}

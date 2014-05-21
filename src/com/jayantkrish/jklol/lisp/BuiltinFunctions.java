package com.jayantkrish.jklol.lisp;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.util.IndexedList;

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
      Preconditions.checkArgument(argumentValues.size() == 1, "Wrong number of arguments: %s",
          argumentValues);
      return ConstantValue.NIL == argumentValues.get(0) ? ConstantValue.TRUE : ConstantValue.FALSE; 
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
      Boolean value = true;
      for (int i = 0; i < argumentValues.size(); i++) {
        value = value && ((ConstantValue) argumentValues.get(i)).toBoolean();
      }
      return ConstantValue.fromBoolean(value);
    }
  }

  public static class OrFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Boolean value = false;
      for (int i = 0; i < argumentValues.size(); i++) {
        value = value || ((ConstantValue) argumentValues.get(i)).toBoolean();
      }
      return ConstantValue.fromBoolean(value);
    }
  }

  public static class PlusFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      if (allArgumentsInteger(argumentValues)) {
        int resultValue = 0;
        for (int i = 0; i < argumentValues.size(); i++) {
          resultValue += (Integer) argumentValues.get(i);
        }
        return resultValue;
      } else {
        double resultValue = 0.0;
        for (int i = 0; i < argumentValues.size(); i++) {
          if (argumentValues.get(i) instanceof Integer) {
            resultValue += (double) ((Integer) argumentValues.get(i));
          } else {
            resultValue += (Double) argumentValues.get(i);
          }
        }
        return resultValue;
      }
    }
  }

  public static class MinusFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      if (allArgumentsInteger(argumentValues)) {
        int resultValue = (Integer) argumentValues.get(0);
        for (int i = 1; i < argumentValues.size(); i++) {
          resultValue -= (Integer) argumentValues.get(i);
        }
        return resultValue;
      } else {
        double resultValue = (Double) argumentValues.get(0);
        for (int i = 1; i < argumentValues.size(); i++) {
          if (argumentValues.get(i) instanceof Integer) {
            resultValue -= (double) ((Integer) argumentValues.get(i));
          } else {
            resultValue -= (Double) argumentValues.get(i);
          }
        }
        return resultValue;
      }
    }
  }

  public static class MultiplyFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      if (allArgumentsInteger(argumentValues)) {
        int resultValue = 1;
        for (int i = 0; i < argumentValues.size(); i++) {
          resultValue *= (Integer) argumentValues.get(i);
        }
        return resultValue;
      } else {
        double resultValue = 1.0;
        for (int i = 0; i < argumentValues.size(); i++) {
          if (argumentValues.get(i) instanceof Integer) {
            resultValue *= (double) ((Integer) argumentValues.get(i));
          } else {
            resultValue *= (Double) argumentValues.get(i);
          }
        }
        return resultValue;
      }
    }
  }

  public static class DivideFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      if (allArgumentsInteger(argumentValues)) {
        return ((Integer) argumentValues.get(0)) / ((Integer) argumentValues.get(1));
      } else {
        return ((Double) argumentValues.get(0)) / ((Double) argumentValues.get(1));
      }
    }
  }

  public static class LogFunction implements FunctionValue {
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      Object value = argumentValues.get(0);
      if (value instanceof Integer) {
        return Math.log((double) (Integer) value);
      } else {
        return Math.log((Double) value);
      }
    }
  }

  public static class ExpFunction implements FunctionValue {
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      Object value = argumentValues.get(0);
      if (value instanceof Integer) {
        return Math.exp((double) (Integer) value);
      } else {
        return Math.exp((Double) value);
      }
    }
  }

  public static class EqualsFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      return argumentValues.get(0).equals(argumentValues.get(1)) ? ConstantValue.TRUE : ConstantValue.FALSE;
    }
  }
  
  public static class LessThanFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      if (allArgumentsInteger(argumentValues)) {
        return ConstantValue.fromBoolean(((Integer) argumentValues.get(0)) < ((Integer) argumentValues.get(1)));
      } else {
        return ConstantValue.fromBoolean(((Double) argumentValues.get(0)) < ((Double) argumentValues.get(1)));
      }
    }
  }

  public static class GreaterThanFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      if (allArgumentsInteger(argumentValues)) {
        return ConstantValue.fromBoolean(((Integer) argumentValues.get(0)) > ((Integer) argumentValues.get(1)));
      } else {
        return ConstantValue.fromBoolean(((Double) argumentValues.get(0)) > ((Double) argumentValues.get(1)));
      }
    }
  }

  public static class DisplayFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      List<String> argumentStrings = Lists.newArrayList();
      for (Object o : argumentValues) {
        argumentStrings.add(formatObjectForDisplay(o));
      }
      System.out.println(Joiner.on(" ").join(argumentStrings));
      return ConstantValue.UNDEFINED;
    }
  }

  public static String formatObjectForDisplay(Object object) {
    if (object instanceof Object[]) {
      return Arrays.toString((Object[]) object);
    } else {
      return object.toString();
    }
  }

  public static void display(Object object) {
    System.out.println(formatObjectForDisplay(object));
  }

  private static boolean allArgumentsInteger(List<Object> argumentValues) {
    boolean integerMultiply = true;
    for (int i = 0; i < argumentValues.size(); i++) {
      if (argumentValues.get(i) instanceof Double) {
        integerMultiply = false;
      }
    }
    return integerMultiply;
  }

  public static class MakeDictionaryFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      return IndexedList.<Object>create(argumentValues);
    }
  }

  public static class DictionaryLookupFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      IndexedList<?> dictionary = (IndexedList<?>) argumentValues.get(1);
      return dictionary.getIndex(argumentValues.get(0));
    }
  }

  public static class DictionaryContainsFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      IndexedList<?> dictionary = (IndexedList<?>) argumentValues.get(1);
      return dictionary.contains(argumentValues.get(0)) ? ConstantValue.TRUE : ConstantValue.FALSE;
    }
  }

  public static class DictionarySizeFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      return ((IndexedList<?>) argumentValues.get(0)).size();
    }
  }

  public static class DictionaryToArrayFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      return ((IndexedList<?>) argumentValues.get(0)).items().toArray();
    }
  }

  public static class MakeArrayFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      return argumentValues.toArray();
    }
  }
  
  public static class ArrayZipFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() > 0);
      Object[][] arrays = new Object[argumentValues.size()][];
      for (int i = 0; i < argumentValues.size(); i++) {
        arrays[i] = (Object[]) argumentValues.get(i);
      }
      
      int size = arrays[0].length;
      for (int i = 0; i < arrays.length; i++) {
        Preconditions.checkArgument(arrays[i].length == size,
            "All arrays passed to zip must have the same length.");
      }
      
      Object[] result = new Object[size];
      for (int i = 0; i < size; i++) {
        List<Object> zippedElts = Lists.newArrayList();
        for (int j = 0; j < arrays.length; j++) {
          zippedElts.add(arrays[j][i]);
        }
        result[i] = ConsValue.listToConsList(zippedElts);
      }
      return result;
    }
  }
  
  public static class ArraySortFunction implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 1
          && argumentValues.get(0) instanceof Object[]);
      Object[] argumentArray = (Object[]) argumentValues.get(0);

      Object[] argumentCopy = Arrays.copyOf(argumentArray, argumentArray.length);
      Arrays.sort(argumentCopy);

      return argumentCopy;
    }
  }
}

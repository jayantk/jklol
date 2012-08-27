package com.jayantkrish.jklol.models;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.jayantkrish.jklol.models.VariableProtos.DiscreteVariableProto;
import com.jayantkrish.jklol.models.VariableProtos.VariableProto;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * Represents a discrete random variable type which takes on one value from a
 * finite set of values. Variables are immutable.
 * 
 * @author jayant
 */
public class DiscreteVariable implements Variable, Serializable {

  private static final long serialVersionUID = 2948903432256540126L;
  
  private String name;
  private IndexedList<Object> values;

  public DiscreteVariable(String name, Collection<? extends Object> values) {
    this.name = name;
    this.values = IndexedList.create(values);
  }

  /**
   * Creates a {@code DiscreteVariable} from its serialization as a protocol
   * buffer.
   * 
   * @param proto
   * @return
   */
  public static DiscreteVariable fromProto(DiscreteVariableProto proto) {
    Preconditions.checkArgument(proto.hasName());

    // This combination of java serialization with protocol buffers is
    // somewhat hacky, but is the only reasonable way to guarantee
    // interoperation with a wide set of value types.
    List<Object> values = Lists.newArrayList();
    for (ByteString serializedValue : proto.getSerializedValueList()) {
      try {
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(serializedValue.toByteArray()));
        values.add(ois.readObject());
        ois.close();
      } catch (IOException e) {
        throw new RuntimeException("Invalid serialized object.", e);
      } catch (ClassNotFoundException e) {
        throw new RuntimeException("Could not find class of serialized objects.", e);
      }
    }

    return new DiscreteVariable(proto.getName(), values);
  }

  /**
   * Creates a discrete variable whose values are {@code 0} to
   * {@code size - 1}.
   * 
   * @param maxNum
   * @return
   */
  public static DiscreteVariable sequence(String name, int size) {
    List<Integer> values = Lists.newArrayList();
    for (int i = 0; i < size; i++) {
      values.add(i);
    }
    return new DiscreteVariable(name, values);
  }

  @Override
  public Object getArbitraryValue() {
    return values.get(0);
  }

  @Override
  public boolean canTakeValue(Object value) {
    return values.contains(value);
  }

  /**
   * Get the number of possible values that this variable can take on.
   * 
   * @return
   */
  public int numValues() {
    return values.size();
  }

  /**
   * Gets all possible values for this variable.
   * 
   * @return
   */
  public List<Object> getValues() {
    return values.items();
  }

  /**
   * Get the value of this enum with the passed-in index.
   * 
   * @param index
   * @return
   */
  public Object getValue(int index) {
    return values.get(index);
  }

  /**
   * Get an integer index which represents the passed in value. Throws a
   * NoSuchElement exception if value is not among the set of values this
   * variable can be assigned.
   */
  public int getValueIndex(Object value) {
    if (!values.contains(value)) {
      throw new NoSuchElementException("Tried accessing nonexistent value \"" + value
          + "\" of variable " + name);
    }
    return values.getIndex(value);
  }

  @Override
  public VariableProto toProto() {
    VariableProto.Builder builder = VariableProto.newBuilder();
    builder.setType(VariableProto.VariableType.DISCRETE);

    DiscreteVariableProto.Builder discreteBuilder = builder.getDiscreteVariableBuilder();
    discreteBuilder.setName(name);

    for (Object value : values.items()) {
      try {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        ObjectOutputStream objectOut = new ObjectOutputStream(byteOut);
        objectOut.writeObject(value);
        objectOut.close();
        discreteBuilder.addSerializedValue(ByteString.copyFrom(byteOut.toByteArray()));
      } catch (IOException e) {
        throw new RuntimeException("Could not serialize " + this.toString(), e);
      }
    }
    return builder.build();
  }

  @Override
  public String toString() {
    return name + " (" + values.size() + " values)";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (o instanceof DiscreteVariable) {
      DiscreteVariable v = (DiscreteVariable) o;
      return name.equals(v.name) && values.equals(v.values);
    }
    return false;
  }
}
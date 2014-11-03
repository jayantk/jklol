package com.jayantkrish.jklol.tensor;

import java.util.Iterator;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.tensor.TensorBase.KeyValue;

/**
 * Maps an iterator over keys to an iterator over key/value pairs. The
 * value for each key is retrieved from a given tensor.
 * 
 * @author jayantk
 */
public class KeyToKeyValueIterator implements Iterator<KeyValue> {

  private final Iterator<int[]> keyIterator;
  private final TensorBase tensor;

  private final KeyValue keyValue;

  public KeyToKeyValueIterator(Iterator<int[]> keyIterator, TensorBase tensor) {
    this.keyIterator = Preconditions.checkNotNull(keyIterator);
    this.tensor = Preconditions.checkNotNull(tensor);

    this.keyValue = new KeyValue(null, 0.0);
  }

  @Override
  public boolean hasNext() {
    return keyIterator.hasNext();
  }

  @Override
  public KeyValue next() {
    int[] key = keyIterator.next();
    keyValue.setKey(key);
    keyValue.setValue(tensor.getByDimKey(key));
    return keyValue;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}

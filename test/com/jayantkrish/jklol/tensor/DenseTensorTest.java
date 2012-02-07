package com.jayantkrish.jklol.tensor;

import java.util.Iterator;

/**
 * Unit tests for {@link DenseTensor}.
 * 
 * @author jayantk
 */
public class DenseTensorTest extends TensorTest {

  public DenseTensorTest() {
    super(DenseTensorBuilder.getFactory());
  }
  
  public void testRandom() {
    DenseTensor randomTensor = DenseTensor.random(new int[] {0, 1}, new int[] {2, 3}, 1.0, 1.0);
    
    assertEquals(6, randomTensor.size());
    Iterator<int[]> keyIter = randomTensor.keyIterator();
    int keyCount = 0;
    while (keyIter.hasNext()) {
      assertTrue(0.0 != randomTensor.getByDimKey(keyIter.next()));
      keyCount++;
    }
    assertEquals(6, keyCount);
  }
}

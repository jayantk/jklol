package com.jayantkrish.jklol.util;

import java.util.Random;

/**
 * Wrapper around Java random number generation that allows the user to set a global seed. This
 * class is intended to allow programs to execute deterministically using an initially-set random
 * seed. (Unfortunately, multithreading may violate this contract for determinism.)
 * <p> 
 * It is always preferred to use the static {@code Random} instance to creating a new random number
 * generator. Using this class enables deterministic execution.
 *
 * @author jayantk
 */
public class Pseudorandom {

  private static Random random = new Random(0);

  public static Random get() {
    return random;
  }
}
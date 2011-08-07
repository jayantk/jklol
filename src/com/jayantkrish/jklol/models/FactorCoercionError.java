package com.jayantkrish.jklol.models;

/**
 * Error that occurs when a {@link Factor} cannot be coerced into another type
 * of {@code Factor}.
 * 
 * @author jayant
 */
public class FactorCoercionError extends RuntimeException {

	/**
	 * Automatically generated version id.
	 */
	private static final long serialVersionUID = -1444725226227728640L;

	public FactorCoercionError(String message) {
		super(message);
	}

	public FactorCoercionError(String message, Exception wrappedException) {
		super(message, wrappedException);
	}
}

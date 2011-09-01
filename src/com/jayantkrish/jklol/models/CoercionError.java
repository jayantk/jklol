package com.jayantkrish.jklol.models;

/**
 * Error that occurs when a {@link Factor} cannot be coerced into another type
 * of {@code Factor}.
 * 
 * @author jayant
 */
public class CoercionError extends RuntimeException {

	/**
	 * Automatically generated version id.
	 */
	private static final long serialVersionUID = -1444725226227728640L;

	public CoercionError(String message) {
		super(message);
	}

	public CoercionError(String message, Exception wrappedException) {
		super(message, wrappedException);
	}
}

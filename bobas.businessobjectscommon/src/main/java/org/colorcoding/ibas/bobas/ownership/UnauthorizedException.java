package org.colorcoding.ibas.bobas.ownership;

public class UnauthorizedException extends OwnershipException {

	private static final long serialVersionUID = -6197956381582100910L;

	public UnauthorizedException() {
		super();
	}

	public UnauthorizedException(String message, Throwable exception) {
		super(message, exception);
	}

	public UnauthorizedException(String message) {
		super(message);
	}

	public UnauthorizedException(Throwable exception) {
		super(exception);
	}
}

package net.foxgenesis.customjail.jail.exception;

import org.springframework.context.MessageSourceResolvable;

public class CannotInteractException extends LocalizedException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6673416740120518621L;
	
	public CannotInteractException(String code, Object... objects ) {
		super(code, objects);
	}

	public CannotInteractException(MessageSourceResolvable resolvable) {
		super(resolvable);
	}
}

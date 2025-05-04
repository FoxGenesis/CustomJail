package net.foxgenesis.customjail.jail.exception;

import org.springframework.context.MessageSourceResolvable;

public class NotSetupException extends LocalizedException {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8062445024482145319L;
	
	public NotSetupException(String code, Object... objects ) {
		super(code, objects);
	}

	public NotSetupException(MessageSourceResolvable resolvable) {
		super(resolvable);
	}
}

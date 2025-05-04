package net.foxgenesis.customjail.jail.exception;

public class NotEnabledException extends LocalizedException {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6112124244929123447L;
	
	public NotEnabledException() {
		super("customjail.not-enabled");
	}
}

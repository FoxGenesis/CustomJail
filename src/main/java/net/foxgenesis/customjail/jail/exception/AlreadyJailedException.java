package net.foxgenesis.customjail.jail.exception;

public class AlreadyJailedException extends LocalizedException {
	/**
	 * 
	 */
	private static final long serialVersionUID = -2405949610041485864L;

	public AlreadyJailedException() {
		super("customjail.alreadyJailed");
	}
}

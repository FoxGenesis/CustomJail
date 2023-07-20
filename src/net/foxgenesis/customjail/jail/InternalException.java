package net.foxgenesis.customjail.jail;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.foxgenesis.customjail.util.Response;

public class InternalException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6994864254084218385L;

	public InternalException(String message) {
		super(message);
	}

	public InternalException(Throwable e) {
		super(e);
	}

	public InternalException(String message, Throwable e) {
		super(message, e);
	}

	public MessageEmbed getDisplayableMessage() {
		return Response.error(getMessage());
	}
}

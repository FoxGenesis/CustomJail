package net.foxgenesis.customjail.jail;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.foxgenesis.customjail.util.Response;

import org.apache.commons.lang3.exception.ExceptionUtils;

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
		String msg = getMessage();
		if (msg == null)
			msg = "```\n" + ExceptionUtils.getStackTrace(this) + "```";
		return Response.error(msg);
	}
}

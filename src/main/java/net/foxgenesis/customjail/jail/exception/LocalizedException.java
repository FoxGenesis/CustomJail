package net.foxgenesis.customjail.jail.exception;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.foxgenesis.watame.util.discord.Response;

public class LocalizedException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 852732728284787489L;
	private final MessageSourceResolvable resolvable;
	
	public LocalizedException(String code, Object... args) {
		this(new DefaultMessageSourceResolvable(new String[] {code}, args, code));
	}

	public LocalizedException(MessageSourceResolvable resolvable) {
		this.resolvable = resolvable;
	}

	public MessageSourceResolvable getErrorMessage() {
		return resolvable;
	}
	
	public MessageEmbed getErrorEmbed(MessageSource source, Locale locale) {
		return Response.error(source.getMessage(getErrorMessage(), locale));
	}
}

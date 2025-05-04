package net.foxgenesis.customjail.event;

import java.util.Locale;
import java.util.function.Supplier;

import org.springframework.context.MessageSource;

import net.foxgenesis.watame.util.discord.LoggableEvent;

public abstract class CustomJailEvent extends LoggableEvent {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6706186171210304828L;

	public CustomJailEvent(Object source) {
		super(source);
	}

	protected Supplier<String> getDefaultReason(MessageSource source, Locale locale) {
		return () -> source.getMessage("customjail.embed.defaultReason", null, locale);
	}

	protected Supplier<String> getNA(MessageSource source, Locale locale) {
		return () -> source.getMessage("customjail.embed.na", null, locale);
	}
}

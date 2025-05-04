package net.foxgenesis.customjail.event;

import java.util.Date;
import java.util.Locale;

import org.springframework.context.MessageSource;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.foxgenesis.watame.util.discord.Colors;
import net.foxgenesis.watame.util.lang.LocalizedEmbedBuilder;

public class JailTimerStartedEvent extends JailEvent {

	/**
	 * 
	 */
	private static final long serialVersionUID = -101361761936637091L;
	private final Date date;

	public JailTimerStartedEvent(Member member, Member moderator, String reason, Date date) {
		super(member, moderator, reason, Colors.WARNING, "customjail.embed.timer-started");
		this.date = date;
	}

	public Date getEndDate() {
		return date;
	}

	@Override
	public void fillEmbed(LocalizedEmbedBuilder builder) {
		MessageSource source = builder.getMessageSource();
		Locale locale = builder.getLocale();
		
		// Row 1
		builder.addLocalizedField("customjail.embed.member", getMember().getAsMention(), true);
		builder.addLocalizedField("customjail.embed.moderator", getModerator(source, locale), true);
		builder.addLocalizedField("customjail.embed.time-left", TimeFormat.RELATIVE.format(date.toInstant()), true);
		
		// Row 2
		builder.addLocalizedField("customjail.embed.reason", getReason(source, locale), false);
	}
}

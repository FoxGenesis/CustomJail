package net.foxgenesis.customjail.event.warning;

import java.util.Locale;

import org.springframework.context.MessageSource;

import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.watame.util.discord.Colors;
import net.foxgenesis.watame.util.discord.ModeratorActionEvent;
import net.foxgenesis.watame.util.lang.LocalizedEmbedBuilder;

public class WarningsClearedEvent extends ModeratorActionEvent {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1993958509358792860L;

	public WarningsClearedEvent(Member member, Member moderator, String reason) {
		super(member, moderator, reason, Colors.WARNING, "customjail.embed.warnings-cleared");
	}

	@Override
	public void fillEmbed(LocalizedEmbedBuilder builder) {
		MessageSource source = builder.getMessageSource();
		Locale locale = builder.getLocale();
		
		// Row 1
		builder.addLocalizedField("customjail.embed.member", getMember().getAsMention(), true);
		builder.addLocalizedField("customjail.embed.moderator", getModerator(source, locale), true);
		
		// Row 2
		builder.addLocalizedField("customjail.embed.reason", getReason(source, locale), false);
	}

}

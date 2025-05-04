package net.foxgenesis.customjail.event;

import java.util.Locale;

import org.springframework.context.MessageSource;

import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.watame.util.discord.Colors;
import net.foxgenesis.watame.util.lang.LocalizedEmbedBuilder;

public class UnjailEvent extends JailEvent {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3321289069004861508L;

	public UnjailEvent(Member member, Member moderator, String reason) {
		super(member, moderator, reason, Colors.SUCCESS, "customjail.embed.member-unjailed");
	}

	@Override
	public void fillEmbed(LocalizedEmbedBuilder builder) {
		MessageSource source = builder.getMessageSource();
		Locale locale = builder.getLocale();
		
		// Row 1
		builder.addLocalizedField("customjail.embed.member", getMember().getAsMention(), true);
		getModerator().ifPresent(moderator -> builder.addLocalizedField("customjail.embed.moderator", moderator.getAsMention(), true));
		
		// Row 2
		builder.addLocalizedField("customjail.embed.reason", getReason(source, locale), false);
	}
}

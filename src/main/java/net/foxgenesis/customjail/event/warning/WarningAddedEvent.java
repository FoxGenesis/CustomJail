package net.foxgenesis.customjail.event.warning;

import java.util.Locale;

import org.springframework.context.MessageSource;

import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.database.warning.Warning;
import net.foxgenesis.watame.util.discord.Colors;
import net.foxgenesis.watame.util.lang.LocalizedEmbedBuilder;

public class WarningAddedEvent extends WarningEvent {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2609252318976100844L;

	public WarningAddedEvent(Member member, Member moderator, Warning warning) {
		super(member, moderator, warning.getReason(), warning, Colors.NOTICE, "customjail.embed.warned");
	}

	@Override
	public void fillEmbed(LocalizedEmbedBuilder builder) {
		MessageSource source = builder.getMessageSource();
		Locale locale = builder.getLocale();
		
		// Row 1
		builder.addLocalizedField("customjail.embed.member", getMember().getAsMention(), true);
		builder.addLocalizedField("customjail.embed.moderator", getModerator(source, locale), true);
		builder.addLocalizedField("customjail.embed.caseid", "" + getWarning().getId(), true);

		// Row 2
		builder.addLocalizedField("customjail.embed.reason", getWarningReason(source, locale), false);
	}
}

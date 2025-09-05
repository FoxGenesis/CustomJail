package net.foxgenesis.customjail.event.warning;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.MessageSource;

import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.database.warning.Warning;
import net.foxgenesis.watame.util.discord.Colors;
import net.foxgenesis.watame.util.discord.DiscordUtils;
import net.foxgenesis.watame.util.lang.LocalizedEmbedBuilder;

public class WarningUpdatedEvent extends WarningEvent {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1052919844161979018L;
	private final Warning oldWarning;

	public WarningUpdatedEvent(Member member, Member moderator, String reason, Warning oldWarning, Warning warning) {
		super(member, moderator, reason, warning, Colors.NOTICE, "customjail.embed.warning-updated");
		this.oldWarning = Objects.requireNonNull(oldWarning);
	}

	public Warning getOldWarning() {
		return oldWarning;
	}

	@Override
	public void fillEmbed(LocalizedEmbedBuilder builder) {
		MessageSource source = builder.getMessageSource();
		Locale locale = builder.getLocale();
		
		// Row 1
		builder.addLocalizedField("customjail.embed.member", DiscordUtils.mentionUser(oldWarning.getMember()), true);
		builder.addLocalizedField("customjail.embed.moderator", DiscordUtils.mentionUser(oldWarning.getModerator()), true);
		builder.addLocalizedField("customjail.embed.caseid", getCaseId(source, locale), true);

		// Row 2
		builder.addLocalizedField("customjail.embed.old-reason", Optional.ofNullable(oldWarning.getReason()).orElseGet(getDefaultReason(source, locale)), true);
		builder.addLocalizedField("customjail.embed.new-reason", getWarningReason(source, locale), true);
		
		// Row 3
		builder.addLocalizedField("customjail.embed.changed-by", getModerator(source, locale), false);
		builder.addLocalizedField("customjail.embed.reason", getReason(source, locale), true);
	}
}

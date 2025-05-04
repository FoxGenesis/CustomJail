package net.foxgenesis.customjail.event.warning;

import java.util.Locale;

import org.springframework.context.MessageSource;

import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.watame.util.discord.Colors;
import net.foxgenesis.watame.util.discord.ModeratorActionEvent;
import net.foxgenesis.watame.util.lang.LocalizedEmbedBuilder;

public class WarningLevelDecreasedEvent extends ModeratorActionEvent {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2575346986414783196L;
	private final int originalLevel;
	private final int newLevel;

	public WarningLevelDecreasedEvent(Member member, Member moderator, String reason, int originalLevel, int newLevel) {
		super(member, moderator, reason, Colors.NOTICE, "customjail.embed.warning-level-decreased");
		this.originalLevel = originalLevel;
		this.newLevel = newLevel;
	}

	public WarningLevelDecreasedEvent(Member member, Member moderator, String reason, int originalLevel) {
		this(member, moderator, reason, originalLevel, originalLevel - 1);
	}

	public int getOriginalLevel() {
		return originalLevel;
	}

	public int getNewLevel() {
		return newLevel;
	}

	@Override
	public void fillEmbed(LocalizedEmbedBuilder builder) {
		MessageSource source = builder.getMessageSource();
		Locale locale = builder.getLocale();
		
		// Row 1
		builder.addLocalizedField("customjail.embed.member", getMember().getAsMention(), true);
		builder.addLocalizedField("customjail.embed.moderator", getModerator(source, locale), true);
		builder.addLocalizedField("customjail.embed.warning-level", originalLevel + " \u2192 " + newLevel, true);

		// Row 2
		builder.addLocalizedField("customjail.embed.reason", getReason(source, locale), false);
	}
}

package net.foxgenesis.customjail.event.warning;

import java.util.Locale;

import org.springframework.context.MessageSource;

import net.dv8tion.jda.api.components.separator.Separator.Spacing;
import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.CommonMessages;
import net.foxgenesis.watame.util.discord.Colors;
import net.foxgenesis.watame.util.discord.components.ModeratorActionEvent;
import net.foxgenesis.watame.util.lang.LocalizedContainerBuilder;
import net.foxgenesis.watame.util.lang.LocalizedEmbedBuilder;
import net.foxgenesis.watame.util.lang.LocalizedSectionBuilder;

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

	@Override
	public void fillContainer(LocalizedContainerBuilder builder) {
		MessageSource source = builder.getMessageSource();
		Locale locale = builder.getLocale();

		builder.addDividingSeparator(Spacing.SMALL);
		builder.addLocalizedFormattedTextDisplay("### %s\n%s", CommonMessages.REASON, getReason(source, locale));
	}

	@Override
	protected void fillContainerDetails(LocalizedSectionBuilder builder) {
		MessageSource source = builder.getMessageSource();
		Locale locale = builder.getLocale();

		String detailsFormat = """
				**%s:** %s
				**%s:** %s
				""";
		builder.addLocalizedFormattedTextDisplay(detailsFormat,
				// Member
				CommonMessages.MEMBER, getMember().getAsMention(),
				// Moderator
				CommonMessages.MODERATOR, getModerator(source, locale));
	}

}

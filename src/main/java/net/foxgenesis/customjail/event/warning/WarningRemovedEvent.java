package net.foxgenesis.customjail.event.warning;

import java.util.Locale;

import org.springframework.context.MessageSource;

import net.dv8tion.jda.api.components.separator.Separator.Spacing;
import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.CommonMessages;
import net.foxgenesis.customjail.database.warning.Warning;
import net.foxgenesis.watame.util.discord.Colors;
import net.foxgenesis.watame.util.discord.DiscordUtils;
import net.foxgenesis.watame.util.lang.Localized;
import net.foxgenesis.watame.util.lang.LocalizedContainerBuilder;
import net.foxgenesis.watame.util.lang.LocalizedEmbedBuilder;
import net.foxgenesis.watame.util.lang.LocalizedSectionBuilder;

public class WarningRemovedEvent extends WarningEvent {

	/**
	 * 
	 */
	private static final long serialVersionUID = -363759311625664168L;

	public WarningRemovedEvent(Member member, Member moderator, String reason, Warning warning) {
		super(member, moderator, reason, warning, Colors.NOTICE, "customjail.embed.warning-deleted");
	}

	@Override
	public void fillEmbed(LocalizedEmbedBuilder builder) {
		MessageSource source = builder.getMessageSource();
		Locale locale = builder.getLocale();

		// Row 1
		Warning warning = getWarning();
		builder.addLocalizedField("customjail.embed.member", DiscordUtils.mentionUser(warning.getMember()), true);
		builder.addLocalizedField("customjail.embed.moderator", DiscordUtils.mentionUser(warning.getModerator()), true);
		builder.addLocalizedField("customjail.embed.caseid", getCaseId(source, locale), true);

		// Row 2
		builder.addLocalizedField("customjail.embed.reason", getWarningReason(source, locale), true);

		// Row 3
		builder.addLocalizedField("customjail.embed.changed-by", getModerator(source, locale), false);
		builder.addLocalizedField("customjail.embed.reasoning", getReason(source, locale), true);
	}

	@Override
	public void fillContainer(LocalizedContainerBuilder builder) {
		MessageSource source = builder.getMessageSource();
		Locale locale = builder.getLocale();

		builder.addDividingSeparator(Spacing.SMALL);
		builder.addLocalizedFormattedTextDisplay("### %s\n%s", CommonMessages.REASON, getWarningReason(source, locale));

		builder.addDividingSeparator(Spacing.SMALL);
		builder.addLocalizedFormattedTextDisplay("**%s:** %s\n### %s\n%s",
				Localized.resolved("customjail.embed.changed-by"), getModerator(source, locale),
				Localized.resolved("customjail.embed.reasoning"), getReason(source, locale));
	}

	@Override
	protected void fillContainerDetails(LocalizedSectionBuilder builder) {
		MessageSource source = builder.getMessageSource();
		Locale locale = builder.getLocale();
		Warning warning = getWarning();

		String detailsFormat = """
				**%s:** %s
				**%s:** %s
				**%s:** %s
				""";
		builder.addLocalizedFormattedTextDisplay(detailsFormat,
				// Member
				CommonMessages.MEMBER, DiscordUtils.mentionUser(warning.getMember()),
				// Moderator
				CommonMessages.MODERATOR, DiscordUtils.mentionUser(warning.getModerator()),
				// Case-ID
				CommonMessages.CASE_ID, getCaseId(source, locale));
	}
}

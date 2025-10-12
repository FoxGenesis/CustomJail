package net.foxgenesis.customjail;

import java.util.Iterator;
import java.util.Objects;

import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.separator.Separator.Spacing;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.foxgenesis.customjail.database.warning.Warning;
import net.foxgenesis.customjail.jail.WarningSystem;
import net.foxgenesis.watame.util.StringUtils;
import net.foxgenesis.watame.util.discord.Colors;
import net.foxgenesis.watame.util.discord.DiscordUtils;
import net.foxgenesis.watame.util.discord.components.ComponentUtils;
import net.foxgenesis.watame.util.lang.Localized;
import net.foxgenesis.watame.util.lang.LocalizedContainerBuilder;
import net.foxgenesis.watame.util.lang.LocalizedContainerPageMenu;
import net.foxgenesis.watame.util.lang.LocalizedSectionBuilder;

public class WarningPageContainer extends LocalizedContainerPageMenu<Warning> {
	private static final MessageSourceResolvable TITLE = Localized.resolved("customjail.embed.warnings");

	private final WarningSystem database;
	private final Member target;

	public WarningPageContainer(GenericCommandInteractionEvent event, WarningSystem database, Member target,
			MessageSource source) {
		super(event, database.getWarningPage(target, PageRequest.of(0, 3, Sort.by("time").descending())), source);
		this.database = Objects.requireNonNull(database);
		this.target = Objects.requireNonNull(target);
		this.sendInitalMessage(event);
	}

	@Override
	protected void populateContainer(LocalizedContainerBuilder builder, Page<Warning> page) {
		builder.setColor(Colors.INFO);

		LocalizedSectionBuilder sb = builder.getNewLocalizedSectionBuilder();

		String headerFormat = """
				%s: `%s`
				%s: `%s`
				""";
		sb.setThumbnailUrl(target.getEffectiveAvatarUrl());
		sb.addLocalizedFormattedTextDisplay("### %s - %s", TITLE, target.getAsMention());
		sb.addLocalizedFormattedTextDisplay(headerFormat,
				// Warning Level
				CommonMessages.WARNING_LEVEL, database.getWarningLevel(target),
				// Total Warnings
				CommonMessages.TOTAL_WARNINGS, database.getTotalWarnings(target));
		builder.addSectionAndClear(sb);

		builder.addDividingSeparator(Spacing.SMALL);

		Iterator<Warning> iterator = page.iterator();
		while (iterator.hasNext()) {
			Warning warning = iterator.next();
			buildWarningSection(builder, warning);
		}
	}

	private void buildWarningSection(LocalizedContainerBuilder builder, Warning warning) {
		String format = """
				#%,d: %s - %s
				>>> %s
				""";
		builder.addLocalizedFormattedTextDisplay(warning.isActive() ? "\u23F2 " + format : format,
				// Case-ID
				warning.getId(),
				// Date
				TimeFormat.RELATIVE.format(warning.getTime()),
				// Moderator
				Localized.resolved("customjail.embed.by", DiscordUtils.mentionUser(warning.getModerator())),
				// Reason
				StringUtils.nullIfBlank(warning.getReason()) == null ? CommonMessages.DEFAULT_REASON
						: warning.getReason());
	}

//	private void buildWarningSection(LocalizedSectionBuilder builder, Warning warning) {
//		builder.setButton(Button.of(ButtonStyle.DANGER, DELETE_PREFIX + warning.getId(), "\u2715"));
//		String format = "%,d: %s - %s";
//		builder.addLocalizedFormattedTextDisplay(warning.isActive() ? "\u23F2 " + format : format,
//				// Case-ID
//				warning.getId(),
//				// Date
//				TimeFormat.RELATIVE.format(warning.getTime()),
//				// Moderator
//				Localized.resolved("customjail.embed.by", DiscordUtils.mentionUser(warning.getModerator())));
//		//builder.addLocalizedTextDisplay("customjail.embed.by", DiscordUtils.mentionUser(warning.getModerator()));
//
//		if (StringUtils.nullIfBlank(warning.getReason()) == null)
//			builder.addLocalizedFormattedTextDisplay(">>> %s", CommonMessages.DEFAULT_REASON);
//		else
//			builder.addTextDisplay(MarkdownUtil.quoteBlock(warning.getReason()));
//	}

	@Override
	protected Container createEmptyPage() {
		return ComponentUtils.Response.error(messages.getMessage("customjail.warnings.empty", null, locale));
	}

	@Override
	protected Page<Warning> getNewPage(Pageable pagable) {
		return database.getWarningPage(target, pagable);
	}

}

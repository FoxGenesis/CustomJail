package net.foxgenesis.customjail;

import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.foxgenesis.customjail.database.warning.Warning;
import net.foxgenesis.customjail.jail.WarningSystem;
import net.foxgenesis.watame.util.discord.Colors;
import net.foxgenesis.watame.util.discord.DiscordUtils;
import net.foxgenesis.watame.util.discord.Response;
import net.foxgenesis.watame.util.lang.LocalizedPageMenu;

public class WarningPage extends LocalizedPageMenu<Warning> {
	private static final MessageSourceResolvable TITLE = new DefaultMessageSourceResolvable(
			"customjail.embed.warnings");
	private static final MessageSourceResolvable WARNING_LEVEL = new DefaultMessageSourceResolvable(
			"customjail.embed.warning-level");
	private static final MessageSourceResolvable WARNING_EXPIRES = new DefaultMessageSourceResolvable(
			"customjail.embed.warning-expires");
	private static final MessageSourceResolvable WARNING_TIMER_NOT_ACTIVE = new DefaultMessageSourceResolvable(
			"customjail.embed.na");

	private final WarningSystem database;
	private final Member target;

	public WarningPage(GenericCommandInteractionEvent event, WarningSystem database, Member target,
			MessageSource source) {
		super(event, database.getWarningPage(target, PageRequest.of(0, 3, Sort.by("time").descending())), source);
		this.database = Objects.requireNonNull(database);
		this.target = Objects.requireNonNull(target);
		this.sendInitalMessage(event);
	}

	@Override
	protected MessageEmbed createEmbed(Page<Warning> page, Locale locale) {
		EmbedBuilder builder = new EmbedBuilder();
		builder.setColor(Colors.INFO);
		builder.setThumbnail(target.getEffectiveAvatarUrl());
		// Title
		builder.appendDescription("### " + messages.getMessage(TITLE, locale) + " - " + target.getAsMention() + "\n");

		// Warning level
		builder.appendDescription(MarkdownUtil.bold(messages.getMessage(WARNING_LEVEL, locale) + ": ")
				+ MarkdownUtil.monospace(database.getWarningLevel(target) + "") + "\n");

		// Warning timer expires
		builder.appendDescription(
				MarkdownUtil.bold(messages.getMessage(WARNING_EXPIRES, locale) + ": ")
						+ database.getWarningEndTimestamp(target).orElseGet(
								() -> MarkdownUtil.monospace(messages.getMessage(WARNING_TIMER_NOT_ACTIVE, locale)))
						+ "\n\n");

		// Write notes
		builder.appendDescription(writeNotes(page, locale));

		Object[] args = { page.getTotalElements(), page.getNumber() + 1, page.getTotalPages() };
		builder.setFooter(messages.getMessage("customjail.warnings.footer", args, locale));
		return builder.build();
	}

	private String writeNotes(Page<Warning> page, Locale locale) {
		// FIXME: clean up and make readable

		// "%1$s#%caseid%:%<s %date% - By **%moderator%**\n**Reason: **%reason%\n"
		StringBuilder sb = new StringBuilder(">>> ");
		Iterator<Warning> iterator = page.iterator();
		while (iterator.hasNext()) {
			Warning warning = iterator.next();

			sb.append(
					warning.isActive() ? MarkdownUtil.bold("#" + warning.getId() + ":") : "#" + warning.getId() + ":");
			sb.append(" " + TimeFormat.RELATIVE.format(warning.getTime()) + " - ");
			sb.append(messages.getMessage("customjail.embed.by",
					new Object[] { DiscordUtils.mentionUser(warning.getModerator()) }, "By {0}", locale));
			sb.append("\n");
			sb.append(MarkdownUtil.bold(messages.getMessage("customjail.embed.reason", null, locale) + ": "));
			sb.append(Optional.ofNullable(warning.getReason())
					.orElseGet(() -> messages.getMessage("customjail.embed.defaultReason", null, locale)));

			if (iterator.hasNext())
				sb.append("\n\n");
		}
		return sb.toString();
	}

	@Override
	protected MessageEmbed createEmptyPageEmbed(Locale locale) {
		return Response
				.error(messages.getMessage("customjail.warnings.empty", null, "customjail.warnings.empty", locale));
	}

	@Override
	protected Page<Warning> getNewPage(Pageable pagable) {
		return database.getWarningPage(target, pagable);
	}

	@Override
	protected MessageEmbed createExpiredEmbed(Locale locale) {
		return Response.error(messages.getMessage(INTERACTION_EXPIRED, locale));
	}
}

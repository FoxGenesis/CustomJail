package net.foxgenesis.customjail;

import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.foxgenesis.customjail.database.warning.Warning;
import net.foxgenesis.customjail.jail.WarningSystem;
import net.foxgenesis.customjail.util.CachedObject;
import net.foxgenesis.watame.util.discord.Colors;
import net.foxgenesis.watame.util.discord.DiscordUtils;
import net.foxgenesis.watame.util.discord.Response;
import net.foxgenesis.watame.util.lang.LocalizedEmbedBuilder;
import net.foxgenesis.watame.util.lang.LocalizedPageMenu;

public class WarningPage extends LocalizedPageMenu<Warning> {
	private static final MessageSourceResolvable NA = new DefaultMessageSourceResolvable("customjail.embed.na");

	private final WarningSystem database;
	private final Member target;

	private final CachedObject<Integer> warningLevelCache;
	private final CachedObject<String> expiresCache;

	public WarningPage(GenericCommandInteractionEvent event, WarningSystem database, Member target,
			MessageSource source) {
		super(event, database.getWarningPage(target, PageRequest.of(0, 3, Sort.by("time").descending())), source);
		this.database = Objects.requireNonNull(database);
		this.target = Objects.requireNonNull(target);

		// Cache Database calls
		this.warningLevelCache = new CachedObject<>(() -> database.getWarningLevel(target), 15, TimeUnit.SECONDS);
		this.expiresCache = new CachedObject<>(
				() -> database.getWarningEndTimestamp(target).orElseGet(() -> messages.getMessage(NA, locale)), 15,
				TimeUnit.SECONDS);

		this.sendInitalMessage(event);
	}

	@Override
	protected MessageEmbed createEmbed(Page<Warning> page, Locale locale) {
		LocalizedEmbedBuilder builder = new LocalizedEmbedBuilder(messages, locale);
		builder.setColor(Colors.INFO);
		builder.setThumbnail(target.getEffectiveAvatarUrl());

		builder.appendLocalizedDescription("customjail.embed.warning.title", target.getAsMention());
		builder.newLine();

		builder.appendLocalizedDescription("customjail.embed.warning.level", warningLevelCache.get());
		builder.newLine();

		builder.appendLocalizedDescription("customjail.embed.warning.expires", expiresCache.get());
		builder.newLine();
		builder.newLine();

		// Write notes
		writeNotes(builder, page, locale);

		Object[] args = { page.getTotalElements(), page.getNumber() + 1, page.getTotalPages() };
		builder.setLocalizedFooter("customjail.warnings.footer", args);

		return builder.build();
	}

	private void writeNotes(LocalizedEmbedBuilder builder, Page<Warning> page, Locale locale) {
		StringBuilder sb = builder.getDescriptionBuilder();
		sb.append(">>> ");

		Iterator<Warning> iterator = page.iterator();
		while (iterator.hasNext()) {
			Warning warning = iterator.next();
			// FIXME: clean up and make readable
			// "%1$s#%caseid%:%<s %date% - By **%moderator%**\n**Reason: **%reason%\n"
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

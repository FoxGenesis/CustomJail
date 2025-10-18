package net.foxgenesis.customjail.jail;

import java.util.Objects;
import java.util.Optional;

import org.quartz.JobDataMap;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.CommonMessages;
import net.foxgenesis.customjail.util.CustomTime;
import net.foxgenesis.customjail.util.Utilities;
import net.foxgenesis.watame.util.StringUtils;
import net.foxgenesis.watame.util.discord.Colors;
import net.foxgenesis.watame.util.discord.DiscordUtils;
import net.foxgenesis.watame.util.lang.LocalizedContainerBuilder;
import net.foxgenesis.watame.util.lang.LocalizedSectionBuilder;

public record JailDetails(long guild, long member, Long moderator, CustomTime duration, String reason, long caseid,
		long timestamp) {

	private static final String BOLD_FIELD = "**%s:** %s";

	private static final String KEY_GUILD = "guild-id";
	private static final String KEY_MEMBER = "member-id";
	private static final String KEY_MODERATOR = "moderator-id";
	private static final String KEY_DURATION = "duration";
	private static final String KEY_REASON = "reason";
	private static final String KEY_CASE_ID = "case-id";
	private static final String KEY_TIMESTAMP = "timestamp";

	public JailDetails {
		if (reason != null && reason.isBlank())
			reason = null;
	}

	public JailDetails(long guild, long member, Long moderator, CustomTime duration, String reason, long caseid) {
		this(guild, member, moderator, duration, reason, caseid, System.currentTimeMillis());
	}

	public JailDetails(Member member, Member moderator, CustomTime duration, String reason, long caseid,
			long timestamp) {
		this(member.getGuild().getIdLong(), member.getIdLong(), moderator != null ? moderator.getIdLong() : null,
				duration, reason, caseid, timestamp);
	}

	public JailDetails(Member member, Member moderator, CustomTime duration, String reason, long caseid) {
		this(member, moderator, duration, reason, caseid, System.currentTimeMillis());
	}

	public JailDetails(Member member, JailDetails details) {
		this(member.getGuild().getIdLong(), member.getIdLong(), details.moderator, details.duration, details.reason,
				details.caseid, details.timestamp);
	}

	public JobDataMap asDataMap() {
		JobDataMap map = new JobDataMap();
		map.put(KEY_GUILD, "" + guild);
		map.put(KEY_MEMBER, "" + member);
		map.put(KEY_MODERATOR, "" + (moderator == null ? -1L : moderator));
		map.put(KEY_DURATION, duration.toString());
		map.put(KEY_REASON, reason);
		map.put(KEY_CASE_ID, "" + caseid);
		map.put(KEY_TIMESTAMP, "" + timestamp);
		return map;
	}

	public static JailDetails resolveFromDataMap(JobDataMap map) {
		Objects.requireNonNull(map);

		long guild = map.getLongValue(KEY_GUILD);
		long member = map.getLongValue(KEY_MEMBER);
		long modId = map.getLongValue(KEY_MODERATOR);

		CustomTime duration = new CustomTime(map.getString(KEY_DURATION));
		String reason = map.getString(KEY_REASON);
		long caseid = map.getLongValue(KEY_CASE_ID);
		long timestamp = map.getLongValue(KEY_TIMESTAMP);

		return new JailDetails(guild, member, modId, duration, reason, caseid, timestamp);
	}

	private static final MessageSourceResolvable JAILED_BY = new DefaultMessageSourceResolvable(
			"customjail.container.jailed-by");
	private static final MessageSourceResolvable TIME_LEFT = new DefaultMessageSourceResolvable(
			"customjail.embed.time-left");
	private static final MessageSourceResolvable NOT_ACCEPTED = new DefaultMessageSourceResolvable(
			"customjail.embed.not-accepted");

	public void applyToContainerBuilder(LocalizedContainerBuilder cb, Member member,
			Optional<String> jailEndTimestamp) {
		cb.setColor(Colors.INFO);
		LocalizedSectionBuilder sb = cb.getNewLocalizedSectionBuilder();
		boolean isTimerRunning = jailEndTimestamp.isPresent();

		Button unjail = sb.newLocalizedButton(ButtonStyle.DANGER,
				Utilities.Interactions.WrappedInteractions.UNJAIL.wrapInteraction(member), CommonMessages.UNJAIL);
		Button forcestart = sb.newLocalizedButton(ButtonStyle.DANGER,
				Utilities.Interactions.WrappedInteractions.FORCE_START.wrapInteraction(member),
				CommonMessages.FORCESTART).withDisabled(isTimerRunning);

		// Section 1
		sb.setThumbnailUrl(member.getEffectiveAvatarUrl());
		sb.addLocalizedTextDisplay(CommonMessages.JAIL_DETAILS);
		sb.addLocalizedTextDisplay("customjail.container.jaildetails-for", DiscordUtils.mentionUser(this.member));
		cb.addSectionAndClear(sb);

		cb.addSmallDividingSeparator();

		// Section 2
		sb.setButton(unjail);
		sb.addLocalizedFormattedTextDisplay(BOLD_FIELD, JAILED_BY, DiscordUtils.mentionUser(moderator));
		sb.addLocalizedFormattedTextDisplay(BOLD_FIELD, CommonMessages.CASE_ID,
				caseid != -1 ? caseid : CommonMessages.NA);
		sb.addLocalizedFormattedTextDisplay(BOLD_FIELD, CommonMessages.DURATION,
				duration.getLocalizedDisplayString(sb.getMessageSource(), sb.getLocale()));
		cb.addSectionAndClear(sb);

		cb.addSmallDividingSeparator();

		// Section 3
		sb.setButton(forcestart);
		sb.addLocalizedFormattedTextDisplay(BOLD_FIELD, CommonMessages.ACCEPTED,
				isTimerRunning ? CommonMessages.YES : CommonMessages.NO);
		sb.addLocalizedFormattedTextDisplay(BOLD_FIELD, TIME_LEFT,
				isTimerRunning ? jailEndTimestamp.get() : NOT_ACCEPTED);
		cb.addSectionAndClear(sb);

		cb.addSmallDividingSeparator();

		cb.addLocalizedFormattedTextDisplay("### %s", CommonMessages.REASON);
		if (StringUtils.nullIfBlank(reason) == null)
			cb.addLocalizedTextDisplay(CommonMessages.DEFAULT_REASON);
		else
			cb.addTextDisplay(reason);
	}
}

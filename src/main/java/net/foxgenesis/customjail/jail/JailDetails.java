package net.foxgenesis.customjail.jail;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.quartz.JobDataMap;
import org.springframework.context.MessageSource;

import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.util.CustomTime;
import net.foxgenesis.watame.util.discord.Colors;
import net.foxgenesis.watame.util.discord.DiscordUtils;
import net.foxgenesis.watame.util.lang.LocalizedEmbedBuilder;

public record JailDetails(long guild, long member, Long moderator, CustomTime duration, String reason, long caseid,
		long timestamp) {

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

	public void applyToEmbedBuilder(LocalizedEmbedBuilder builder, MessageSource source, Locale locale, Member member,
			Optional<String> jailEndTimestamp) {
		boolean isTimerRunning = jailEndTimestamp.isPresent();

		builder.setColor(Colors.INFO);
		builder.setThumbnail(member.getEffectiveAvatarUrl());
		builder.setLocalizedTitle("customjail.embed.jaildetails");
		builder.addLocalizedField("customjail.embed.member", DiscordUtils.mentionUser(this.member), true);
		builder.addLocalizedField("customjail.embed.moderator", DiscordUtils.mentionUser(moderator), true);

		builder.addLocalizedField("customjail.embed.caseid",
				caseid != -1 ? "" + caseid : source.getMessage("customjail.embed.na", null, locale), true);

		builder.addLocalizedFieldAndValue("customjail.embed.accepted",
				isTimerRunning ? "customjail.embed.yes" : "customjail.embed.no", true, null);

		builder.addLocalizedField("customjail.embed.duration", duration.getLocalizedDisplayString(source, locale),
				true);

		String endTime = isTimerRunning
				? jailEndTimestamp.orElseGet(() -> source.getMessage("customjail.embed.na", null, locale))
				: source.getMessage("customjail.embed.not-accepted", null, locale);
		builder.addLocalizedField("customjail.embed.time-left", endTime, true);

		builder.addLocalizedField("customjail.embed.reason", Optional.ofNullable(reason)
				.orElseGet(() -> source.getMessage("customjail.embed.defaultReason", null, locale)), false);
		builder.setLocalizedFooter("customjail.footer");

		builder.setTimestamp(Instant.ofEpochMilli(timestamp));
	}
}

package net.foxgenesis.customjail.jail;

import java.util.Objects;

import org.quartz.JobDataMap;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.time.CustomTime;

public record JailDetails(Member member, Member moderator, CustomTime duration, String reason, int caseid,
		long timestamp) {

	private static final String KEY_GUILD = "guild-id";
	private static final String KEY_MEMBER = "member-id";
	private static final String KEY_MODERATOR = "moderator-id";
	private static final String KEY_DURATION = "duration";
	private static final String KEY_REASON = "reason";
	private static final String KEY_CASE_ID = "case-id";
	private static final String KEY_TIMESTAMP = "timestamp";

	public JailDetails(Member member, Member moderator, CustomTime duration, String reason, int caseid) {
		this(member, moderator, duration, reason, caseid, System.currentTimeMillis());
	}
	
	public JailDetails(Member member, JailDetails details) {
		this(member, details.moderator, details.duration, details.reason, details.caseid);
	}

	public JobDataMap asDataMap() {
		JobDataMap map = new JobDataMap();
		map.put(KEY_GUILD, member.getGuild().getIdLong());
		map.put(KEY_MEMBER, member.getIdLong());
		map.put(KEY_MODERATOR, moderator == null ? -1L : moderator.getIdLong());
		map.put(KEY_DURATION, duration.toString());
		map.put(KEY_REASON, reason == null ? "No Reason Provided" : reason);
		map.put(KEY_CASE_ID, caseid);
		map.put(KEY_TIMESTAMP, timestamp);
		return map;
	}

	public static JailDetails resolveFromDataMap(JDA jda, JobDataMap map) {
		Objects.requireNonNull(jda);
		Objects.requireNonNull(map);

		Guild guild = jda.getGuildById(map.getLongValue(KEY_GUILD));
		Member m = guild.getMemberById(map.getLongValue(KEY_MEMBER));

		long modId = map.getLongValue(KEY_MODERATOR);
		Member moderator = modId == -1 ? null : guild.getMemberById(modId);

		CustomTime duration = new CustomTime(map.getString(KEY_DURATION));
		String reason = map.getString(KEY_REASON);
		int caseid = map.getIntValue(KEY_CASE_ID);
		long timestamp = map.getLongValue(KEY_TIMESTAMP);

		return new JailDetails(m, moderator, duration, reason, caseid, timestamp);
	}
}

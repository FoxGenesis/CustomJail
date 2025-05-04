package net.foxgenesis.customjail.jail;

import java.util.Objects;

import org.quartz.JobDataMap;

import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.util.CustomTime;

public record WarningDetails(long guild, long member, CustomTime duration) {
	private static final String KEY_GUILD = "guild-id";
	private static final String KEY_MEMBER = "member-id";
	private static final String KEY_DURATION = "duration";
	
	public WarningDetails(Member member, CustomTime time) {
		this(member.getGuild().getIdLong(), member.getIdLong(), time);
	}
	
	public JobDataMap asDataMap() {
		JobDataMap map = new JobDataMap();
		map.put(KEY_GUILD, "" + guild);
		map.put(KEY_MEMBER, "" + member);
		map.put(KEY_DURATION, duration.toString());
		return map;
	}
	
	public static WarningDetails resolveFromDataMap(JobDataMap map) {
		Objects.requireNonNull(map);

		long guild = map.getLongValue(KEY_GUILD);
		long member = map.getLongValue(KEY_MEMBER);
		CustomTime duration = new CustomTime(map.getString(KEY_DURATION));
		
		return new WarningDetails(guild, member, duration);
	}
}

package net.foxgenesis.customjail.jail;

import java.time.Duration;
import java.util.Date;
import java.util.Optional;

import org.quartz.SchedulerException;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.util.CustomTime;

public interface JailScheduler {

	default boolean createJailTimer(Member member, Member moderator, CustomTime duration, String reason, long caseId)
			throws SchedulerException {
		return createJailTimer(new JailDetails(member, moderator, duration, reason, caseId));
	}

	boolean createJailTimer(JailDetails details) throws SchedulerException;

	Date startJailTimer(Member member);

	boolean isJailed(long guild, long member);

	default boolean isJailed(Member member) {
		return isJailed(member.getGuild().getIdLong(), member.getIdLong());
	}
	
	boolean isJailTimerRunning(long guild, long member);

	default boolean isJailTimerRunning(Member member) {
		return isJailTimerRunning(member.getGuild().getIdLong(), member.getIdLong());
	}
	
	boolean removeJailTimer(long guild, long member);

	default boolean removeJailTimer(Member member) {
		return removeJailTimer(member.getGuild().getIdLong(), member.getIdLong());
	}
	
	Optional<JailDetails> getJailDetails(long guild, long member) throws SchedulerException;

	default Optional<JailDetails> getJailDetails(Member member) throws SchedulerException {
		return getJailDetails(member.getGuild().getIdLong(), member.getIdLong());
	}

	Date createWarningTimer(long guild, long member, CustomTime time);
	
	default Date createWarningTimer(Member member, CustomTime time) {
		return createWarningTimer(member.getGuild().getIdLong(), member.getIdLong(), time);
	}

	boolean rescheduleWarningTimer(long guild, long member, CustomTime time);

	boolean isWarningTimerRunning(long guild, long member);
	
	default boolean isWarningTimerRunning(Member member) {
		return isWarningTimerRunning(member.getGuild().getIdLong(), member.getIdLong());
	}

	boolean removeWarningTimer(long guild, long member);
	
	default boolean removeWarningTimer(Member member) {
		return removeWarningTimer(member.getGuild().getIdLong(), member.getIdLong());
	}
	
	default void stopWarningTimerIfRunning(long guild, long member) {
		if(isWarningTimerRunning(guild, member))
			removeWarningTimer(guild, member);
	}
	
	default void stopWarningTimerIfRunning(Member member) {
		stopWarningTimerIfRunning(member.getGuild().getIdLong(), member.getIdLong());
	}

	Optional<String> getWarningEndTimestamp(long guild, long member);
	
	default Optional<String> getWarningEndTimestamp(Member member) {
		return getWarningEndTimestamp(member.getGuild().getIdLong(), member.getIdLong());
	}

	Optional<String> getJailEndTimestamp(Member member);

	Optional<Duration> getRemainingJailTime(Member member);

	Date getJailEndDate(Member member);

	Date getWarningEndDate(long guild, long member);
	
	default Date getWarningEndDate(Member member) {
		return getWarningEndDate(member.getGuild().getIdLong(), member.getIdLong());
	}
	
	boolean removeAllTimers(long guild) throws SchedulerException;
	
	default boolean removeAllTimer(Guild guild) throws SchedulerException {
		return removeAllTimers(guild.getIdLong());
	}
}

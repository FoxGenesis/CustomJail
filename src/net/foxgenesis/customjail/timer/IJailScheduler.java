package net.foxgenesis.customjail.timer;

import java.time.Duration;
import java.util.Date;
import java.util.Optional;

import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;

import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.jail.JailDetails;
import net.foxgenesis.customjail.time.CustomTime;
import net.foxgenesis.customjail.time.UnixTimestamp;

public interface IJailScheduler {

	boolean createJailTimer(JailDetails details) throws SchedulerException;

	boolean startJailTimer(Member member);

	boolean isJailed(Member member);

	boolean isJailTimerRunning(Member member);

	boolean removeJailTimer(Member member);

	Optional<JailDetails> getJailDetails(Member member) throws SchedulerException;

	boolean createWarningTimer(Member member, CustomTime time);

	boolean rescheduleWarningTimer(Member member, CustomTime time);

	boolean isWarningTimerRunning(Member member);

	boolean removeWarningTimer(Member member);

	Optional<UnixTimestamp> getWarningEndTimestamp(Member member);

	Optional<UnixTimestamp> getJailEndTimestamp(Member member);

	Optional<Duration> getRemainingJailTime(Member member);

	Date getJailEndDate(Member member);

	Date getWarningEndDate(Member member);

	SimpleTrigger createWarningTrigger(JobKey key, CustomTime time);
}

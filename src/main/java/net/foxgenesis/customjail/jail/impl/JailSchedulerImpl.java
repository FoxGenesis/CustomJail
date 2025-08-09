package net.foxgenesis.customjail.jail.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.foxgenesis.customjail.jail.JailDetails;
import net.foxgenesis.customjail.jail.JailJob;
import net.foxgenesis.customjail.jail.JailScheduler;
import net.foxgenesis.customjail.jail.WarningDetails;
import net.foxgenesis.customjail.jail.WarningJob;
import net.foxgenesis.customjail.util.CustomTime;

public class JailSchedulerImpl implements JailScheduler {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final Scheduler scheduler;

	public JailSchedulerImpl(Scheduler scheduler) {
		this.scheduler = Objects.requireNonNull(scheduler);
	}

	@Override
	public boolean createJailTimer(JailDetails details) throws SchedulerException {
		long guild = details.guild();
		long member = details.member();

		logger.debug("Creating jail timer for {} in {} | {}", member, guild, details.duration());

		JobKey key = jailJob(guild, member);

		JobDetail job = JobBuilder.newJob(JailJob.class).storeDurably().withIdentity(key)
				.usingJobData(details.asDataMap()).build();

		try {
			scheduler.addJob(job, false);
			if (isWarningTimerRunning(guild, member))
				removeWarningTimer(guild, member);
			return true;
		} catch (SchedulerException e) {
			logger.error("Failed to add job " + key + " in scheduler", e);
			throw new RuntimeException(e);
		}
	}

	@Override
	public Date startJailTimer(Member member) {
		logger.debug("Starting jail timer for {}", member);
		JobKey key = jailJob(member);

		try {
			TriggerBuilder<Trigger> builder = TriggerBuilder.newTrigger();
			builder.forJob(key);
			builder.withIdentity(key.getGroup() + ":" + key.getName(), "jail-timer");
			builder.usingJobData("start-time", "" + System.currentTimeMillis());
			builder.withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow());
			builder.startAt(new CustomTime(scheduler.getJobDetail(key).getJobDataMap().getString("duration"))
					.normalize().addTo(new Date()));
			Trigger trigger = builder.build();

			return scheduler.scheduleJob(trigger);
		} catch (SchedulerException e) {
			logger.error("Failed to start jail timer for " + key, e);
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean isJailed(long guild, long member) {
		JobKey key = jailJob(guild, member);

		try {
			return scheduler.checkExists(key);
		} catch (SchedulerException e) {
			logger.error("Failed to check if job " + key + " exists in scheduler", e);
			return false;
		}
	}

	@Override
	public boolean isJailTimerRunning(long guild, long member) {
		return getTriggerForJob(jailJob(guild, member)).isPresent();
	}

	@Override
	public boolean removeJailTimer(long guild, long member) {
		logger.debug("Removing jail timer for {} in {}", member, guild);
		JobKey key = jailJob(guild, member);

		try {
			return scheduler.deleteJob(key);
		} catch (SchedulerException e) {
			logger.error("Failed to remove jail job " + key + " from the scheduler", e);
			throw new RuntimeException(e);
		}
	}

	@Override
	public Optional<JailDetails> getJailDetails(long guild, long member) throws SchedulerException {
		if (!isJailed(guild, member))
			return Optional.empty();

		return Optional.ofNullable(scheduler.getJobDetail(jailJob(guild, member)))
				// Get job data
				.map(JobDetail::getJobDataMap)
				// Map to jail details
				.map(JailDetails::resolveFromDataMap);
	}

	// =================================================================================================================

	@Override
	public Date createWarningTimer(long guild, long member, CustomTime time) {
		logger.info("Starting warning timer for {} with {}", member, time);
		JobKey key = warningJob(guild, member);

		if (isWarningTimerRunning(guild, member))
			removeWarningTimer(guild, member);

		JobBuilder builder = JobBuilder.newJob(WarningJob.class);
		builder.withIdentity(key);
		builder.usingJobData(new WarningDetails(guild, member, time).asDataMap());
		JobDetail detail = builder.build();

		try {
			return scheduler.scheduleJob(detail, createWarningTrigger(key, time));
		} catch (SchedulerException e) {
			logger.error("Failed to start jail timer for " + key, e);
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean rescheduleWarningTimer(long guild, long member, CustomTime time) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isWarningTimerRunning(long guild, long member) {
		JobKey key = warningJob(guild, member);

		try {
			return scheduler.checkExists(key);
		} catch (SchedulerException e) {
			logger.error("Failed to check if job " + key + " exists in scheduler", e);
			return false;
		}
	}

	@Override
	public boolean removeWarningTimer(long guild, long member) {
		logger.debug("Removing warning timer for {} in {}", member, guild);
		JobKey key = warningJob(guild, member);
		try {
			return scheduler.deleteJob(key);
		} catch (SchedulerException e) {
			logger.error("Failed to pause warning timer for" + key, e);
			return false;
		}
	}

	@Override
	public Optional<String> getWarningEndTimestamp(long guild, long member) {
		return getFireTimestamp(warningJob(guild, member));
	}

	@Override
	public Optional<String> getJailEndTimestamp(Member member) {
		return getFireTimestamp(jailJob(member));
	}

	private Optional<String> getFireTimestamp(JobKey key) {
		return getTriggerForJob(key)
				// Get next fire date
				.map(Trigger::getNextFireTime)
				// Get epoch time
				.map(Date::getTime)
				// Format to relative time
				.map(TimeFormat.RELATIVE::format);
	}

	@Override
	public Optional<Duration> getRemainingJailTime(Member member) {
		return getTriggerForJob(jailJob(member)).map(JailSchedulerImpl::getTimeLeft);
	}

	@Override
	public Date getJailEndDate(Member member) {
		return getTriggerForJob(jailJob(member)).map(t -> t.getNextFireTime()).orElse(null);
	}

	@Override
	public Date getWarningEndDate(long guild, long member) {
		return getTriggerForJob(warningJob(guild, member)).map(t -> t.getNextFireTime()).orElse(null);
	}
	
	@Override
	public boolean removeAllTimers(long guild) throws SchedulerException {
		return scheduler.deleteJobs(new ArrayList<>(scheduler.getJobKeys(GroupMatcher.jobGroupEquals(guild + ""))));
	}

	private SimpleTrigger createWarningTrigger(JobKey key, CustomTime time) {
		return TriggerBuilder.newTrigger().startAt(time.addTo(new Date())).forJob(key)
				.withIdentity(key.getGroup() + ":" + key.getName(), "warning-timer")
				.withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow()).build();
	}

	// =================================================================================================================

	private static Duration getTimeLeft(Trigger trigger) {
		return Duration.ofMillis(trigger.getStartTime().getTime() - System.currentTimeMillis());
	}

	private static JobKey jailJob(Member member) {
		return jailJob(member.getGuild().getIdLong(), member.getIdLong());
	}

	private static JobKey jailJob(long guild, long member) {
		return JobKey.jobKey("jail:" + member, "" + guild);
	}

	private static JobKey warningJob(long guild, long member) {
		return JobKey.jobKey("warning:" + member, "" + guild);
	}

	private Optional<Trigger> getTriggerForJob(JobKey key) {
		try {
			List<? extends Trigger> triggers = scheduler.getTriggersOfJob(key);
			return triggers.isEmpty() ? Optional.empty() : Optional.of(triggers.get(0));
		} catch (SchedulerException e) {
			logger.error("Failed to find jail trigger for " + key, e);
			return Optional.empty();
		}
	}
}

package net.foxgenesis.customjail.timer;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import net.foxgenesis.customjail.jail.JailDetails;
import net.foxgenesis.customjail.time.CustomTime;
import net.foxgenesis.customjail.time.UnixTimestamp;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Member;

public class JailScheduler implements AutoCloseable, IJailScheduler {
	private static final Logger logger = LoggerFactory.getLogger(JailScheduler.class);
	private static final long MISFIRE_DELAY = 2;
	private static final long MAX_DELAY = 3_600;

	private final Scheduler scheduler;

	public JailScheduler(Properties properties, Map<String, Object> contextProperties) throws SchedulerException {
		properties.putIfAbsent("org.quartz.dataSource.quartzDataSource.user", properties.getProperty("username"));
		properties.remove("username");

		properties.putIfAbsent("org.quartz.dataSource.quartzDataSource.password", properties.getProperty("password"));
		properties.remove("password");

		String type = properties.getProperty("databaseType", "mysql");
		properties.remove("databaseType");

		String ip = properties.getProperty("ip", "localhost");
		properties.remove("ip");

		String port = properties.getProperty("port", "3306");
		properties.remove("port");

		properties.put("org.quartz.dataSource.quartzDataSource.URL",
				"jdbc:%s://%s:%s/%s".formatted(type, ip, port, "QUARTZ_SCHEMA"));

		logger.debug("Creating scheduler factory");
		SchedulerFactory schedulerFactory = new StdSchedulerFactory(properties);
		scheduler = schedulerFactory.getScheduler();
		scheduler.getContext().putAll(contextProperties);
		scheduler.getContext().put("scheduler", this);
		scheduler.getListenerManager().addJobListener(new JobErrorListener(MISFIRE_DELAY, MAX_DELAY));
	}

	// =================================================================================================================

	@Override
	public boolean isJailed(Member member) {
		JobKey key = jailJob(member);

		try {
			return scheduler.checkExists(key);
		} catch (SchedulerException e) {
			logger.error("Failed to check if job " + key + " exists in scheduler", e);
			return false;
		}
	}

	@Override
	public boolean isJailTimerRunning(Member member) {
		return isJailed(member) && getTriggerForJob(jailJob(member)).isPresent();
	}

	@Override
	public boolean createJailTimer(JailDetails details) {
		Member member = details.member();

		logger.debug("Creating jail timer for {} | {}", member, details.duration());

		JobKey key = jailJob(member);

		JobDetail job = JobBuilder.newJob(JailTimer.class).storeDurably().withIdentity(key)
				.usingJobData(details.asDataMap()).build();

		try {
			scheduler.addJob(job, false);
			if (isWarningTimerRunning(member))
				removeWarningTimer(member);
			return true;
		} catch (SchedulerException e) {
			logger.error("Failed to add job " + key + " in scheduler", e);
			throw new RuntimeException(e);
		}
	}

	@Override
	public Optional<JailDetails> getJailDetails(Member member) throws SchedulerException {
		Objects.requireNonNull(member);
		if (!isJailed(member))
			return Optional.empty();

		return Optional.ofNullable(scheduler.getJobDetail(jailJob(member))).map(JobDetail::getJobDataMap)
				.map(map -> JailDetails.resolveFromDataMap(member.getJDA(), map));
	}

	@Override
	public boolean startJailTimer(Member member) {
		logger.info("Starting jail timer for {}", member);
		JobKey key = jailJob(member);

		try {
			return scheduler.scheduleJob(TriggerBuilder.newTrigger()
					.startAt(new CustomTime(scheduler.getJobDetail(key).getJobDataMap().getString("duration"))
							.normalize().addTo(new Date()))
					.forJob(key).withIdentity(key.getGroup() + ":" + key.getName(), "jail-timer")
					.usingJobData("start-time", System.currentTimeMillis())
					.withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow())
					.build()) != null;
		} catch (SchedulerException e) {
			logger.error("Failed to start jail timer for " + key, e);
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean removeJailTimer(Member member) {
		logger.info("Removing jail timer for {}", member);
		JobKey key = jailJob(member);

		try {
			return scheduler.deleteJob(key);
		} catch (SchedulerException e) {
			logger.error("Failed to remove jail job " + key + " from the scheduler", e);
			throw new RuntimeException(e);
		}
	}

	// =================================================================================================================

	@Override
	public boolean createWarningTimer(Member member, CustomTime time) {
		logger.info("Starting warning timer for {}", member);
		JobKey key = warningJob(member);

		if (isWarningTimerRunning(member))
			removeWarningTimer(member);

		try {
			return scheduler.scheduleJob(
					JobBuilder.newJob(WarningTimer.class).withIdentity(key)
							.usingJobData("guild-id", member.getGuild().getIdLong())
							.usingJobData("member-id", member.getIdLong()).build(),
					createWarningTrigger(key, time)) != null;
		} catch (SchedulerException e) {
			logger.error("Failed to start jail timer for " + key, e);
			throw new RuntimeException(e);
		}
	}

	@Override
	public SimpleTrigger createWarningTrigger(JobKey key, CustomTime time) {
		return TriggerBuilder.newTrigger().startAt(time.addTo(new Date())).forJob(key)
				.withIdentity(key.getGroup() + ":" + key.getName(), "warning-timer")
				.withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow()).build();
	}

	@Override
	public boolean isWarningTimerRunning(Member member) {
		JobKey key = warningJob(member);

		try {
			return scheduler.checkExists(key);
		} catch (SchedulerException e) {
			logger.error("Failed to check if job " + key + " exists in scheduler", e);
			return false;
		}
	}

	@Override
	public boolean rescheduleWarningTimer(Member member, CustomTime time) {
		logger.debug("Rescheduling warning timer for {}", member);
		JobKey key = warningJob(member);
		return getTriggerForJob(key).map(trigger -> {
			try {
				return scheduler.rescheduleJob(trigger.getKey(),
						createWarningTrigger(trigger.getJobKey(), time)) != null;
			} catch (SchedulerException e) {
				logger.error("Failed to reschedule warning timer for" + key, e);
				return false;
			}
		}).orElseGet(() -> createWarningTimer(member, time));
	}

	@Override
	public boolean removeWarningTimer(Member member) {
		JobKey key = warningJob(member);
		try {
			return scheduler.deleteJob(key);
		} catch (SchedulerException e) {
			logger.error("Failed to pause warning timer for" + key, e);
			return false;
		}
	}

	// =================================================================================================================

	public void start() throws SchedulerException {
		scheduler.start();
	}

	@Override
	public void close() throws SchedulerException {
		logger.info("Closing jail scheduler");
		scheduler.shutdown(true);
	}

	@Override
	public Date getJailEndDate(Member member) {
		return getTriggerForJob(jailJob(member)).map(t -> t.getNextFireTime()).orElse(null);
	}

	@Override
	public Date getWarningEndDate(Member member) {
		return getTriggerForJob(warningJob(member)).map(t -> t.getNextFireTime()).orElse(null);
	}

	@Override
	public Optional<UnixTimestamp> getWarningEndTimestamp(Member member) {
		return getTriggerForJob(warningJob(member)).map(t -> t.getNextFireTime()).map(UnixTimestamp::fromDate);
	}

	@Override
	public Optional<UnixTimestamp> getJailEndTimestamp(Member member) {
		return getTriggerForJob(jailJob(member)).map(t -> t.getNextFireTime()).map(UnixTimestamp::fromDate);
	}

	@Override
	public Optional<Duration> getRemainingJailTime(Member member) {
		return getTriggerForJob(jailJob(member)).map(JailScheduler::getTimeLeft);
	}

	// =================================================================================================================

	private static Duration getTimeLeft(Trigger trigger) {
		return Duration.ofMillis(trigger.getStartTime().getTime() - System.currentTimeMillis());
	}

	private static JobKey jailJob(Member member) {
		return JobKey.jobKey(member.getId(), "jail");
	}

	private static JobKey warningJob(Member member) {
		return JobKey.jobKey(member.getId(), "warning");
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

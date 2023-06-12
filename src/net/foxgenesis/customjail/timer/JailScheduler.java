package net.foxgenesis.customjail.timer;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.apache.commons.lang3.time.DateUtils;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
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

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.requests.RestAction;
import net.foxgenesis.customjail.CustomJailPlugin;
import net.foxgenesis.customjail.database.IWarningDatabase;
import net.foxgenesis.customjail.time.CustomTime;
import net.foxgenesis.watame.Constants;

public class JailScheduler implements AutoCloseable, IJailScheduler {
	private static final Logger logger = LoggerFactory.getLogger(JailScheduler.class);

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
	public boolean createJailTimer(Member member, CustomTime time) {
		logger.debug("Creating jail timer for {} | {}", member, time);

		JobKey key = jailJob(member);

		JobDetail job = JobBuilder.newJob(JailTimer.class).storeDurably().withIdentity(key)
				.usingJobData("guild-id", member.getGuild().getIdLong()).usingJobData("member-id", member.getIdLong())
				.usingJobData("duration", time.toString()).build();

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
	public boolean extendJailTimer(Member member, TemporalAmount newTime) {
		logger.info("Extending jail timer for {} -> {}", member, newTime);
		JobKey key = jailJob(member);
		return getTriggerForJob(key).map(trigger -> {
			try {
				scheduler.rescheduleJob(trigger.getKey(), extendTriggerTime(trigger, newTime));
				return true;
			} catch (SchedulerException e) {
				logger.error("Failed to extend jail timer for " + key, e);
				return false;
			}
		}).orElseGet(() -> {
			try {
				JobDataMap map = scheduler.getJobDetail(key).getJobDataMap();
				map.put("duration", temporalToSeconds(newTime) + map.getIntValue("duration"));
				return true;
			} catch (SchedulerException e) {
				logger.error("Failed to extend jail timer for " + key, e);
				return false;
			}
		});
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
	public boolean resetWarningTimer(Member member) {
		logger.debug("Resetting warning timer for {}", member.getEffectiveName());
		JobKey key = warningJob(member);
		return getTriggerForJob(key).map(trigger -> {
			try {
				scheduler.rescheduleJob(trigger.getKey(), resetTriggerTime(trigger));
				return true;
			} catch (SchedulerException e) {
				logger.error("Failed to extend warning timer for" + key, e);
				return false;
			}
		}).orElse(false);
	}

	@Override
	public boolean updateWarningTimer(Member member, TemporalAmount newTime) {
		// TODO implement method to update warning timer
		throw new UnsupportedOperationException("Not yet implemented!");
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

	// =================================================================================================================

	@Override
	public void unjail(Member member, Role timeoutRole, Member moderator, String reason, IWarningDatabase database,
			Consumer<Member> success, Consumer<Throwable> err) {
		Guild guild = member.getGuild();

		// Create moderation log embed
		EmbedBuilder builder = new EmbedBuilder().setColor(Constants.Colors.SUCCESS).setTitle("Member Unjailed")
				.setThumbnail(member.getEffectiveAvatarUrl()).addField("User", member.getAsMention(), true);

		if (moderator != null)
			builder.addField("Moderator", moderator.getAsMention(), true);

		builder.addField("Reason", reason != null ? reason : "Time is up", false).setTimestamp(Instant.now())
				.setFooter("via Custom Jail").build();

		// Un-jail and log to moderation log
		CustomJailPlugin.modlog(guild.removeRoleFromMember(member, timeoutRole), guild, () -> builder.build())
				.queue(v -> {
					if (database.getWarningLevelForMember(member) > 0) {
						createWarningTimer(member, CustomJailPlugin.getWarningTime(guild));
					}
					success.accept(member);
				}, err);
	}

	@Override
	@Nullable
	public RestAction<?> updateWarningLevel(Member member, int newLevel) {
		Guild guild = member.getGuild();

		List<Role> currentRoles = member.getRoles().stream().filter(role -> role.getName().startsWith("Warning"))
				.sorted((a, b) -> a.getName().compareTo(b.getName())).toList();

		List<Role> warningRoles = guild.getRoles().stream().filter(role -> role.getName().startsWith("Warning"))
				.filter(guild.getSelfMember()::canInteract).sorted((a, b) -> a.getName().compareTo(b.getName()))
				.toList();

		int maxWarnings = CustomJailPlugin.getMaxWarnings(guild);
		int currentLevel = currentRoles.size();

		// Clamp warning level
		newLevel = clamp(newLevel, 0, Math.max(maxWarnings, warningRoles.size()));

		logger.info("Updating {}'s warning roles from {} to {}", member.getUser().getName(), currentLevel, newLevel);

		// Fail fast
		if (newLevel == currentLevel)
			return null;

		// Add warning
		else if (newLevel > currentLevel)
			return RestAction.allOf(warningRoles.subList(currentLevel, newLevel).stream()
					.map(role -> guild.addRoleToMember(member, role).reason("Warning level update")).toList());

		// Remove warning
		else
			return RestAction.allOf(warningRoles.subList(newLevel, currentLevel).stream()
					.map(role -> guild.removeRoleFromMember(member, role).reason("Warning level update")).toList());
	}

	private static int clamp(int in, int min, int max) {
		return in < min ? min : in > max ? max : in;
	}

	// =================================================================================================================

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

	public Optional<Duration> getRemainingJailTime(Member member) {
		return getTriggerForJob(jailJob(member)).map(JailScheduler::getTimeLeft);
//		JobKey key = jailJob(member);
//		return getTriggerForJob(jailJob(member)).map(JailScheduler::getTimeLeft).orElseGet(() -> {
//			try {
//				return Duration.of(scheduler.getJobDetail(key).getJobDataMap().getIntValue("duration"),
//						ChronoUnit.SECONDS);
//			} catch (SchedulerException e) {
//				logger.error("Failed to get jail duration for " + key, e);
//				return null;
//			}
//		});
	}

	public TemporalAmount getJailDuration(Member member) {
		JobKey key = jailJob(member);
		return getTriggerForJob(jailJob(member)).map(JailScheduler::getTriggerDuration).orElseGet(() -> {
			try {
				return Duration.of(scheduler.getJobDetail(key).getJobDataMap().getIntValue("duration"),
						ChronoUnit.SECONDS);
			} catch (SchedulerException e) {
				logger.error("Failed to get jail duration for " + key, e);
				return null;
			}
		});
	}

	@Deprecated
	private static Trigger extendTriggerTime(Trigger trigger, TemporalAmount newTime) {
		return trigger.getTriggerBuilder()
				.startAt(DateUtils.addSeconds(trigger.getStartTime(), temporalToSeconds(newTime))).build();
	}

	private static Trigger resetTriggerTime(Trigger trigger) {
		return trigger.getTriggerBuilder().startAt(Date.from(Instant.now().plus(getTriggerDuration(trigger)))).build();
	}

	private static Duration getTimeLeft(Trigger trigger) {
		return Duration.ofMillis(trigger.getStartTime().getTime() - System.currentTimeMillis());
	}

	private static Duration getTriggerDuration(Trigger trigger) {
		return Duration.ofMillis(trigger.getStartTime().getTime() - trigger.getJobDataMap().getLongValue("start-time"));
	}

	@SuppressWarnings("unused")
	private static long temporalToMilli(TemporalAmount time) {
		return time.getUnits().stream().map(time::get).map(t -> t * 1000).reduce((a, b) -> a + b).orElse(-1L);
	}

	@Deprecated
	private static int temporalToSeconds(TemporalAmount time) {
		return time.getUnits().stream().map(time::get).map(BigInteger::valueOf).map(BigInteger::intValue)
				.reduce((a, b) -> a + b).orElse(-1);
	}
}

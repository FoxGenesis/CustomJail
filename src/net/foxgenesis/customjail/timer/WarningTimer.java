package net.foxgenesis.customjail.timer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.CustomJailPlugin;
import net.foxgenesis.customjail.database.IWarningDatabase;
import net.foxgenesis.watame.WatameBot;

public class WarningTimer implements Job {

	private static final Logger logger = LoggerFactory.getLogger(WarningTimer.class);

	@Override
	public void execute(JobExecutionContext jobContext) throws JobExecutionException {
		JobDataMap data = jobContext.getMergedJobDataMap();
		logger.debug("WarningTimer finished: data = [{}]", data.getWrappedMap());

		SchedulerContext context;
		try {
			context = jobContext.getScheduler().getContext();
		} catch (SchedulerException e) {
			throw error(e);
		}

		Object obj = context.get("scheduler");
		IJailScheduler scheduler;
		if (obj instanceof IJailScheduler) {
			scheduler = (IJailScheduler) obj;
		} else {
			throw error("Failed to get JailScheduler!");
		}

		obj = context.get("warningDatabase");
		IWarningDatabase database;
		if (obj instanceof IWarningDatabase) {
			database = (IWarningDatabase) obj;
		} else {
			throw error("Context property \"warningDatabase\" is not of type IWarningDatabase!");
		}

		long guildID = data.getLongValue("guild-id");
		Guild guild = WatameBot.INSTANCE.getJDA().getGuildById(guildID);

		if (guild != null) {
			// Get member
			long memberID = data.getLongValue("member-id");
			CompletableFuture<Member> futureMember = guild.retrieveMemberById(memberID).submit();
			futureMember
					.thenAcceptBoth(futureMember.thenApply(database::decreaseAndGetWarningLevel), (member, level) -> {
						// Update warning roles for member
						scheduler.updateWarningLevel(member, level).queue();

						// If warning level is not zero, reschedule job
						if (level > 0) {
							Trigger oldTrigger = jobContext.getTrigger();
							Trigger newTrigger = scheduler.createWarningTrigger(oldTrigger.getJobKey(),
									CustomJailPlugin.getWarningTime(guild));
							Scheduler scheduler2 = jobContext.getScheduler();
							try {
								if (scheduler2.rescheduleJob(oldTrigger.getKey(), newTrigger) == null)
									if (!scheduler.createWarningTimer(member, CustomJailPlugin.getWarningTime(guild)))
										throw error("Failed to create warning timer for " + memberID + ":" + guildID);

								logger.debug("Rescheduled warning timer for {}:{}", guildID, memberID);
							} catch (SchedulerException e) {
								throw new CompletionException("Failed to reschedule warning timer", e);
							}
						}
					}).whenComplete((v, err) -> {
						if (err != null)
							logger.error("Error occured during warning timer", err);
						else
							logger.debug("warning timer for {}:{} completed", guildID, memberID);
					});
		} else {
			logger.warn("Failed to find guild [{}]", guildID);
			throw error("Guild id is invalid");
		}
	}

	private static JobExecutionException error(String message) {
		JobExecutionException e = new JobExecutionException(message);
		e.setUnscheduleFiringTrigger(true);
		return e;
	}

	private static JobExecutionException error(Throwable err) {
		JobExecutionException e = new JobExecutionException(err);
		e.setUnscheduleFiringTrigger(true);
		return e;
	}
}

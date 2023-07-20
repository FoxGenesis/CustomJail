package net.foxgenesis.customjail.timer;

import java.util.Optional;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.jail.IJailSystem;
import net.foxgenesis.customjail.jail.IJailSystem.ErrorHandler;
import net.foxgenesis.watame.WatameBot;
import net.foxgenesis.watame.WatameBot.State;

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

		Object obj = context.get("jailSystem");
		IJailSystem jail;
		if (obj instanceof IJailSystem) {
			jail = (IJailSystem) obj;
		} else {
			throw error("Context property \"jailSystem\" is not of type IJailSystem!");
		}

		// Wait for the system to be in a ready state
		while (WatameBot.INSTANCE.getState() != State.RUNNING)
			Thread.onSpinWait();

		long guildID = data.getLongValue("guild-id");
		Guild guild = WatameBot.INSTANCE.getJDA().getGuildById(guildID);

		if (guild == null) {
			logger.error("Failed to find guild [{}]. Trying again...", guildID);
			throw error("Unable to find guild with id " + guildID);
		}

		// Get member
		long memberID = data.getLongValue("member-id");
		Member member = guild.retrieveMemberById(memberID).complete();

		if (member == null) {
			logger.error("Failed to find member [{}] from guild [{}]. Trying again...", memberID, guildID);
			throw error("Unable to find member with id " + memberID);
		}

		jail.decreaseWarningLevel(member, Optional.empty(), Optional.empty(), (oldLevel, newLevel) -> {
			logger.info("Warning timer finished for {}. Level decreased {} -> {}", member, oldLevel, newLevel);
		}, ErrorHandler.identity());
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

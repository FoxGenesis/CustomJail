package net.foxgenesis.customjail.timer;

import java.util.Optional;

import net.foxgenesis.customjail.jail.IJailSystem;
import net.foxgenesis.customjail.jail.IJailSystem.ErrorHandler;
import net.foxgenesis.watame.State;
import net.foxgenesis.watame.WatameBot;

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

public class JailTimer implements Job {
	private static final Logger logger = LoggerFactory.getLogger(JailTimer.class);

	@Override
	public void execute(JobExecutionContext jobContext) throws JobExecutionException {
		JobDataMap data = jobContext.getMergedJobDataMap();
		logger.debug("JailTimer finished: data = [{}]", data.getWrappedMap());

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
		while (WatameBot.getState() != State.RUNNING)
			Thread.onSpinWait();

		long guildID = data.getLongValue("guild-id");
		for (String id : WatameBot.getJDA().getUnavailableGuilds())
			if (id.equals("" + guildID))
				error("Guild " + guildID + " is unavailable!");

		Guild guild = WatameBot.getJDA().getGuildById(guildID);

		if (guild == null) {
			logger.error("Failed to find guild [{}]. Trying again...", guildID);
			throw error("Unable to find guild with id " + guildID);
		}

		// Get member
		long memberID = data.getLongValue("member-id");
		Member member = guild.retrieveMemberById(memberID).complete();

		if (member == null) {
			logger.warn("Member [{}] from {} is no longer in the server. Skipping...", memberID, guild);
			return;
		}

		// Finish if member isn't jailed
		if (!jail.isJailed(member))
			return;

		jail.unjail(member, Optional.empty(), Optional.of("Time is up"), () -> logger.info("Unjailed {}", member),
				ErrorHandler.identity());
	}

	private static JobExecutionException error(String message) {
		JobExecutionException e = new JobExecutionException(message);
		return e;
	}

	private static JobExecutionException error(Throwable err) {
		JobExecutionException e = new JobExecutionException(err);
		return e;
	}
}

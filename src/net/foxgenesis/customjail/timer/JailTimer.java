package net.foxgenesis.customjail.timer;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.foxgenesis.customjail.CustomJailPlugin;
import net.foxgenesis.customjail.database.IWarningDatabase;
import net.foxgenesis.watame.Constants;
import net.foxgenesis.watame.WatameBot;

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
			CompletableFuture<Member> futureMember = guild.retrieveMemberById(data.getLongValue("member-id")).submit();

			CompletableFuture.allOf(
					// Un-jail and log to moderation log
					futureMember.thenAcceptAsync(member -> CustomJailPlugin
							.modlog(guild.removeRoleFromMember(member, CustomJailPlugin.getTimeoutRole(guild)), guild,
									() -> logUnjail(member))
							.submit().thenRun(() -> logger.info("Un-Jailed {}", member))),

					// Start warning timer
					futureMember.thenAcceptAsync(member -> {
						if (database.getWarningLevelForMember(member) > 0) {
							scheduler.createWarningTimer(member, CustomJailPlugin.getWarningTime(guild));
						}
					})).whenComplete((v, err) -> {
						if (err != null)
							logger.error("Error occured during unjailing", err);
						else
							logger.info("Finished unjail job {}", jobContext.getJobDetail().getKey());
					});
		} else {
			logger.warn("Failed to find guild [{}]", guildID);
			throw error("Guild id is invalid");
		}

	}

	private static MessageEmbed logUnjail(Member member) {
		return new EmbedBuilder().setColor(Constants.Colors.SUCCESS).setTitle("Member Unjailed")
				.setThumbnail(member.getEffectiveAvatarUrl()).addField("User", member.getAsMention(), true)
				.addField("Reason", "Time is up", false).setTimestamp(Instant.now()).setFooter("via Custom Jail")
				.build();
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

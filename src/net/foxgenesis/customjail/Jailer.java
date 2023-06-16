package net.foxgenesis.customjail;

import static net.foxgenesis.customjail.CustomJailPlugin.modlog;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.RestAction;
import net.foxgenesis.customjail.database.IWarningDatabase;
import net.foxgenesis.customjail.time.CustomTime;
import net.foxgenesis.customjail.timer.JailScheduler;
import net.foxgenesis.customjail.util.Response;
import net.foxgenesis.watame.Constants;
import net.foxgenesis.watame.property.IGuildPropertyMapping;

public class Jailer implements IJailHandler, AutoCloseable {
	private static final Logger logger = LoggerFactory.getLogger(Jailer.class);

	private final JailScheduler scheduler;
	private final IWarningDatabase warningDatabase;

	@SuppressWarnings({ "exports", "resource" })
	public Jailer(@Nonnull Properties prop, @Nonnull IWarningDatabase database) throws SchedulerException {
		this.warningDatabase = Objects.requireNonNull(database);
		this.scheduler = Objects.requireNonNull(new JailScheduler(prop, Map.of("warningDatabase", warningDatabase)));
	}

	@Override
	public boolean isJailed(Member member) {
		return scheduler.isJailed(member);
	}

	@Override
	public void jail(InteractionHook hook, Member member, Member moderator, CustomTime time, String reason,
			boolean addWarning) {
		if (isJailed(member)) {
			hook.editOriginalEmbeds(Response.error("User is already jailed!")).setReplace(true).queue();
			return;
		}

		Guild guild = member.getGuild();
		Member self = guild.getSelfMember();
		Role timeoutRole = CustomJailPlugin.timeoutRole.get(guild, IGuildPropertyMapping::getAsRole);
		TextChannel timeoutChannel = CustomJailPlugin.timeoutChannel.get(guild, () -> null,
				IGuildPropertyMapping::getAsTextChannel);

		// Validate information
		if (timeoutRole == null) {
			hook.editOriginalEmbeds(Response.error("Timeout role is invalid or not set!")).setReplace(true).queue();
			return;
		}

		if (timeoutChannel == null) {
			hook.editOriginalEmbeds(Response.error("Timeout channel is invalid or not set!")).setReplace(true).queue();
			return;
		}

		if (!(timeoutChannel.canTalk() && self.hasPermission(timeoutChannel, Permission.MESSAGE_EMBED_LINKS))) {
			hook.editOriginalEmbeds(Response.error("Bot is unable to send embeds in timeout channel!")).setReplace(true)
					.queue();
			return;
		}

		if (!self.canInteract(timeoutRole)) {
			hook.editOriginalEmbeds(Response.error("Bot is unable to give the timeout role!")).setReplace(true).queue();
			return;
		}

		if (!self.canInteract(member)) {
			hook.editOriginalEmbeds(Response.error("Bot is unable assign timeout role to member!")).setReplace(true)
					.queue();
			return;
		}

		// Jail member
		String prettyPrintDuration = time.getDisplayString();
		guild.addRoleToMember(member, timeoutRole).reason(reason)
				.addCheck(() -> !member.getRoles().contains(timeoutRole)).flatMap(v -> {
					scheduler.createJailTimer(member, time);

					int case_id = addWarning ? warningDatabase.addWarningForMember(member, moderator, reason, true)
							: -1;

					// Send notice to moderation log
					RestAction<?> log = modlog(guild, () -> {
						EmbedBuilder builder = new EmbedBuilder().setColor(Constants.Colors.ERROR)
								.setTitle("Jailed User").setThumbnail(member.getEffectiveAvatarUrl());

						builder.addField("User", member.getAsMention(), true)
								.addField("Moderator", moderator.getAsMention(), true).addBlankField(true);

						// If user was given a warning, add the case id
						if (addWarning)
							builder.addField("Case ID", "" + case_id, true);

						builder.addField("Duration", prettyPrintDuration, true).addField("Reason", reason, false)
								.setTimestamp(Instant.now()).setFooter("via Custom Jail");
						return builder.build();
					});

					// Send embed to jail channel
					RestAction<?> jailEmbed = timeoutChannel.sendMessage(member.getAsMention())
							.addEmbeds(new EmbedBuilder().setColor(Constants.Colors.ERROR).setTitle("Jailed")
									.setThumbnail(member.getEffectiveAvatarUrl())
									.addField("User", member.getAsMention(), true)
									.addField("Case ID", addWarning ? "" + case_id : "N/A", true)
									.addField("Duration", prettyPrintDuration, true).addField("Reason", reason, false)
									.setTimestamp(Instant.now()).setFooter("via Custom Jail").build())
							.addActionRow(Button.primary("start-jail", "Accept"));

					log = log.and(jailEmbed);

					// Update warning roles
					if (addWarning) {
						RestAction<?> updateWarnings = scheduler.updateWarningLevel(member,
								warningDatabase.getWarningLevelForMember(member));
						if (updateWarnings != null)
							log = log.and(updateWarnings);
					}

					return log;
				}).queue(v -> {
					hook.editOriginalEmbeds(
							Response.success("Jailed " + member.getAsMention() + " for " + prettyPrintDuration))
							.setReplace(true).queue();
					logger.info("Jailed {} for {}", member, time);
				}, err -> {
					logger.error("Error while jailing user: ", err);
					CustomJailPlugin.displayError(hook, err).queue();
				});
	}

	@Override
	public void extendJailTime(InteractionHook hook, Member member, Member moderator, TemporalAmount time) {
		hook.editOriginal("Not yet implemented").setReplace(true).queue();
	}

	@Override
	public void startJailTime(InteractionHook hook, Member member) {
		if (scheduler.isJailTimerRunning(member))
			hook.editOriginalEmbeds(Response.info("Timer is running. You have "
					+ prettyPrintDuration(scheduler.getRemainingJailTime(member)) + " left.")).queue();
		else if (scheduler.startJailTimer(member))
			hook.editOriginalEmbeds(Response.success("Started timer. You have "
					+ prettyPrintDuration(scheduler.getRemainingJailTime(member)) + " left.")).queue();
		else
			hook.editOriginalEmbeds(Response.error("Failed to start jail timer! Please tell a moderator!")).queue();

	}

	@Override
	public void startJailTime(InteractionHook hook, Member member, Member moderator) {
		try {
			if (scheduler.isJailTimerRunning(member))
				hook.editOriginalEmbeds(Response.notice("Timer is already running. "
						+ prettyPrintDuration(scheduler.getRemainingJailTime(member)) + " left.")).queue();
			else if (scheduler.startJailTimer(member))
				modlog(hook.editOriginalEmbeds(Response.success(
						"Started timer. " + prettyPrintDuration(scheduler.getRemainingJailTime(member)) + " left.")),
						member.getGuild(),
						() -> new EmbedBuilder().setColor(Constants.Colors.WARNING).setTitle("Timer Force Started")
								.setThumbnail(member.getEffectiveAvatarUrl())
								.addField("Member", member.getAsMention(), true)
								.addField("Moderator", moderator.getAsMention(), true).setTimestamp(Instant.now())
								.setFooter("via Custom Jail").build())
						.queue();
			else
				hook.editOriginalEmbeds(Response.error("Failed to start jail timer!")).queue();
		} catch (Exception e) {
			CustomJailPlugin.displayError(hook, e).queue();
		}
	}

	@Override
	public void unjail(InteractionHook hook, Member member, Member moderator, String reason) {
		Guild guild = member.getGuild();
		Role timeoutRole = CustomJailPlugin.timeoutRole.get(guild, () -> null, IGuildPropertyMapping::getAsRole);

		if (timeoutRole == null) {
			hook.editOriginalEmbeds(Response.error("Timeout role is invalid or not set!")).queue();
			return;
		}

		if (!isJailed(member)) {
			if (member.getRoles().contains(timeoutRole))
				hook.editOriginalEmbeds(
						Response.notice("User not jailed but has timeout role. Removing their timeout role."))
						.and(guild.removeRoleFromMember(member, timeoutRole).reason(reason)).queue();

			else
				hook.editOriginalEmbeds(Response.notice("User is not jailed!")).queue();
			return;
		}

		if (!scheduler.removeJailTimer(member)) {
			hook.editOriginalEmbeds(Response.error("Failed to remove member from database!")).queue();
			return;
		}

		scheduler.unjail(member, timeoutRole, moderator, reason, warningDatabase, v -> {
			hook.editOriginalEmbeds(Response.success("Un-Jailed " + member.getAsMention())).setReplace(true).queue();
			logger.info("Un-Jailed {}", member);
		}, err -> {
			logger.error("Error while unjailing user: ", err);
			CustomJailPlugin.displayError(hook, err).queue();
		});
	}

	public void start() throws SchedulerException {
		scheduler.start();
	}

	@Override
	public void close() throws Exception {
		if (scheduler != null)
			scheduler.close();
	}

	private static String prettyPrintDuration(Optional<Duration> duration) {
		return duration.map(Duration::toMillis).map(n -> DurationFormatUtils.formatDurationWords(n, true, true))
				.orElse("null");
	}
}

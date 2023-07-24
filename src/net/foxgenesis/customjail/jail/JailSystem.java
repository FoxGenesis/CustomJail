package net.foxgenesis.customjail.jail;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.foxgenesis.customjail.CustomJailPlugin;
import net.foxgenesis.customjail.database.IWarningDatabase;
import net.foxgenesis.customjail.database.Warning;
import net.foxgenesis.customjail.jail.event.IJailEventBus;
import net.foxgenesis.customjail.jail.event.JailEventBus;
import net.foxgenesis.customjail.jail.event.impl.JailTimerStartEvent;
import net.foxgenesis.customjail.jail.event.impl.MemberJailEvent;
import net.foxgenesis.customjail.jail.event.impl.MemberUnjailEvent;
import net.foxgenesis.customjail.jail.event.impl.WarningAddedEvent;
import net.foxgenesis.customjail.jail.event.impl.WarningReasonUpdateEvent;
import net.foxgenesis.customjail.jail.event.impl.WarningRemovedEvent;
import net.foxgenesis.customjail.jail.event.impl.WarningTimerFinishEvent;
import net.foxgenesis.customjail.jail.event.impl.WarningTimerStartEvent;
import net.foxgenesis.customjail.time.CustomTime;
import net.foxgenesis.customjail.time.UnixTimestamp;
import net.foxgenesis.customjail.timer.JailScheduler;
import net.foxgenesis.customjail.util.Utilities;
import net.foxgenesis.util.resource.ModuleResource;
import net.foxgenesis.watame.util.Colors;

public class JailSystem implements Closeable, IJailSystem {
	private static final Logger logger = LoggerFactory.getLogger(JailSystem.class);

	private static final String EMBED_FOOTER = "via Custom Jail";

	private final JailScheduler scheduler;
	private final IWarningDatabase database;
	private final JailEventBus bus;

	public JailSystem(IWarningDatabase database) throws IOException, SchedulerException {
		logger.info("Creating jail scheduler");
		Properties prop = new ModuleResource("watamebot.customjail", "/META-INF/quartz.properties").asProperties();
		Properties prop2 = new Properties();
		try (InputStream in = Files.newInputStream(Path.of("config", "database.properties"), StandardOpenOption.READ)) {
			prop2.load(in);
		}
		prop.putAll(prop2);

		this.scheduler = new JailScheduler(prop, Map.of("jailSystem", this));
		this.database = Objects.requireNonNull(database);
		bus = new JailEventBus(null);
	}

	public void start() throws SchedulerException {
		logger.info("Starting Jail System...");
		scheduler.start();
	}

	@Override
	public void close() throws IOException {
		try {
			logger.info("Shutting down Jail System...");
			scheduler.close();
		} catch (SchedulerException e) {
			throw new IOException(e);
		} finally {
			logger.debug("Closing event bus");
			bus.close();
			try {
				bus.awaitTermination(15, TimeUnit.SECONDS);
			} catch (InterruptedException e) {}
		}
	}

	@Override
	public void jail(Member member, Member moderator, CustomTime time, String reason, boolean addWarning,
			Consumer<JailDetails> jailDetails, ErrorHandler<InternalException> e) {
		// Check if member is already jailed
		if (scheduler.isJailed(member)) {
			handleError(e, InternalException::new, "User is already jailed!", null);
			return;
		}

		// Do not allow operations on self
		if (moderator.equals(member)) {
			handleError(e, InternalException::new, "Unable to unjail self", null);
			return;
		}

		// Check if moderator can interact with user
		if (!moderator.canInteract(member)) {
			handleError(e, InternalException::new, "You are unable to interact with this member", null);
			return;
		}

		Guild guild = member.getGuild();
		try {
			validateSettings(guild);
		} catch (InternalException e1) {
			handleError(e, InternalException::new, null, e1);
			return;
		}

		Role timeoutRole = CustomJailPlugin.getTimeoutRole(guild);
		GuildMessageChannel timeoutChannel = CustomJailPlugin.getJailChannel(guild);
		String validReason = reason == null ? getDefaultReason() : reason;

		// Retrieve other jail information
		String prettyPrintDuration = time.getDisplayString();

		// Add warning in database if needed
		Optional<Integer> caseid = addWarning
				? Optional.ofNullable(addWarningForMember(member, moderator, Optional.ofNullable(reason), true, true))
						.filter(id -> id != -1)
				: Optional.empty();

		// Assert that a warning was added to the database if needed
		if (addWarning && caseid.isEmpty()) {
			handleError(e, InternalException::new,
					"Failed to insert warning into database! Please report this to the developers if this issue persists!",
					null);
			return;
		}

		// Create jail timer
		JailDetails details = new JailDetails(member, moderator, time, validReason, caseid.orElse(-1));
		if (!scheduler.createJailTimer(details)) {
			// Failed to create jail timer. undo jail
			caseid.ifPresent(id -> database.deleteWarning(guild, id));
			handleError(e, InternalException::new,
					"Failed to create jail timer! Please report this to the developers if the issue persists!", null);
			return;
		}

		// Update member warning roles if we added a warning otherwise get current
		Set<Role> currentRoles = new HashSet<>(member.getRoles());
		Set<Role> roles = caseid.isPresent()
				? Utilities.Warnings.modifyWarningRoles(member, getWarningLevelForMember(member))
				: new HashSet<>(member.getRoles());

		// Add the jail role
		roles.add(timeoutRole);

		// Create role modification request
		RestAction<Void> modifyRoles = guild.modifyMemberRoles(member, roles).reason(validReason);

		// Create the jail embed
		EmbedBuilder jailEmbedBuilder = new EmbedBuilder();
		jailEmbedBuilder.setColor(Colors.ERROR);
		jailEmbedBuilder.setTitle("Jailed");
		jailEmbedBuilder.setThumbnail(member.getEffectiveAvatarUrl());

		// Row 1
		jailEmbedBuilder.addField("Member", member.getAsMention(), true);
		jailEmbedBuilder.addField("Moderator", moderator.getAsMention(), true);
		jailEmbedBuilder.addField("Case ID", caseid.map(Object::toString).orElse("N/A"), true);

		// Row 2
		jailEmbedBuilder.addField("Duration", prettyPrintDuration, true);

		// Row 3
		jailEmbedBuilder.addField("Reason", validReason, false);

		jailEmbedBuilder.setTimestamp(Instant.now());
		jailEmbedBuilder.setFooter(EMBED_FOOTER, CustomJailPlugin.EMBED_FOOTER_ICON);

		// Create jail embed request
		MessageCreateAction jailEmbed = timeoutChannel.sendMessage(member.getAsMention())
				.addEmbeds(jailEmbedBuilder.build())
				.addActionRow(Button.primary(Utilities.Interactions.wrapInteraction("startjail", member), "Accept"));

		// Modify roles and send jail embed
		modifyRoles.and(jailEmbed).queue(v -> {
			logger.info("Jailed {} for {}", member, time);

			// Fire event if moderator is not self member
			if (!guild.getSelfMember().equals(moderator))
				bus.fireEvent(new MemberJailEvent(details));

			// Callback with successful jailing
			jailDetails.accept(details);
		}, err -> {
			// Failed to send jail embed or modify roles. Revert jailing
			guild.modifyMemberRoles(member, currentRoles).queue(v -> {}, e2 -> {});
			caseid.ifPresent(id -> database.deleteWarning(guild, id)); // use database method to avoid firing event
			scheduler.removeJailTimer(member);

			// Display error
			handleError(e, InternalException::new, null, err);
		});
	}

	@Override
	public void unjail(Member member, Optional<Member> moderator, Optional<String> reason, Runnable success,
			ErrorHandler<InternalException> err) {
		// Check if not jailed
		if (!scheduler.isJailed(member)) {
			handleError(err, InternalException::new, "Member is not jailed!", null);
			return;
		}

		// Do not allow operations on self
		if (moderator.isPresent() && moderator.get().equals(member)) {
			handleError(err, InternalException::new, "Unable to unjail self", null);
			return;
		}

		Guild guild = member.getGuild();
		try {
			validateSettings(guild);
		} catch (InternalException e) {
			handleError(err, InternalException::new, null, e);
			return;
		}

		Role timeoutRole = CustomJailPlugin.getTimeoutRole(guild);
		String validReason = reason.orElseGet(this::getDefaultReason);

		// Remove jail timer
		if (!scheduler.removeJailTimer(member)) {
			handleError(err, InternalException::new, "Failed to remove jail timer!", null);
			return;
		}

		// Remove jail role
		guild.removeRoleFromMember(member, timeoutRole).reason(validReason).queue(v -> {
			// Fire event if moderator is not self member
			if ((moderator.isPresent() && !moderator.get().equals(guild.getSelfMember())) || moderator.isEmpty())
				bus.fireEvent(new MemberUnjailEvent(member, moderator, reason));

			// If warning level is higher than zero, start warning timer otherwise fail
			CustomTime time = CustomJailPlugin.getWarningTime(guild);
			if (getWarningLevelForMember(member) > 0 && !scheduler.createWarningTimer(member, time))
				handleError(err, InternalException::new, "Member unjailed but failed to start warning timer!", null);
			else {
				logger.info("Unjailed {}", member);
				bus.fireEvent(new WarningTimerStartEvent(member, time.addTo(new Date())));
				success.run();
			}
		}, e -> handleError(err, InternalException::new, "Timer removed but failed to remove jail role!", e));
	}

	@Override
	public void startJailTimer(Member member, Optional<Member> moderator, Optional<String> reason,
			Consumer<UnixTimestamp> timeLeft, ErrorHandler<InternalException> err) {

		// Ensure member is jailed
		if (!scheduler.isJailed(member)) {
			handleError(err, InternalException::new, "Member is not jailed", null);
			return;
		}

		// If timer not started, start it
		if (!scheduler.isJailTimerRunning(member)) {

			// Start timer
			logger.info("Starting jail timer for {}", member);
			if (scheduler.startJailTimer(member)) {
				try {
					// Fire event if moderator is not self member
					if ((moderator.isPresent() && !moderator.get().equals(member.getGuild().getSelfMember()))
							|| moderator.isEmpty())
						scheduler.getJailDetails(member).ifPresent(details -> bus.fireEvent(
								new JailTimerStartEvent(details, moderator, reason, scheduler.getJailEndDate(member))));

				} catch (SchedulerException e) {
					handleError(err, InternalException::new, "Timer started but failed to validate", e);
				}
			} else {
				handleError(err, InternalException::new, "Failed to start jail timer!", null);
				return;
			}
		}

		// Display time left
		timeLeft.accept(scheduler.getJailEndTimestamp(member).get());
	}

	@Override
	public void decreaseWarningLevel(Member member, Optional<Member> moderator, Optional<String> reason,
			BiConsumer<Integer, Integer> newLevel, ErrorHandler<InternalException> err) {
		// Get current level
		int currentLevel = getWarningLevelForMember(member);

		// Check that level is higher than 0
		if (currentLevel <= 0) {
			handleError(err, InternalException::new, "Level is already 0", null);
			return;
		}

		// Decrease warning level and update roles
		int newLvl = database.decreaseAndGetWarningLevel(member);
		Utilities.Warnings.updateWarningLevel(member, newLvl, reason).queue();
		bus.fireEvent(new WarningTimerFinishEvent(member, moderator, currentLevel, newLvl));

		// If new level is greater than 0, reschedule warning timer
		if (newLvl > 0) {
			CustomTime time = CustomJailPlugin.getWarningTime(member.getGuild());
			if (!scheduler.rescheduleWarningTimer(member, time))
				if (!scheduler.createWarningTimer(member, time)) {
					handleError(err, InternalException::new, "Failed to reschedule warning timer", null);
					return;
				}
			// Fire timer start event
			bus.fireEvent(new WarningTimerStartEvent(member, scheduler.getWarningEndDate(member)));
		}
		// Attempt to stop warning timer if new level is zero
		else if (newLvl <= 0 && scheduler.isWarningTimerRunning(member) && !scheduler.removeWarningTimer(member)) {
			handleError(err, InternalException::new, "Failed to stop warning timer", null);
			return;
		}

		// Callback with old and new level
		newLevel.accept(currentLevel, newLvl);
	}

	@Override
	public int addWarningForMember(Member member, Member moderator, Optional<String> reason, boolean active) {
		return addWarningForMember(member, moderator, reason, active, false);
	}

	/**
	 * Internal method to add a warning to a {@link Member} with an option to bypass
	 * member information update.
	 * 
	 * @param member    - member to add a warning for
	 * @param moderator - moderator who added this warning
	 * @param reason    - (optional) warning reason
	 * @param active    - is this an active warning
	 * @param internal  - is this an internal call
	 * 
	 * @return Returns the case id of the warning or {@code -1} otherwise
	 */
	private int addWarningForMember(Member member, Member moderator, Optional<String> reason, boolean active,
			boolean internal) {
		int caseID = database.addWarningForMember(member, moderator, reason.orElse(getDefaultReason()), active);

		// Fire event if warning was this is an internal call
		if (!(caseID == -1 || internal)) {
			database.getWarning(member.getGuild(), caseID).ifPresent(
					warning -> bus.fireEvent(new WarningAddedEvent(WarningDetails.fromData(member.getJDA(), warning))));

			// If active warning, update member information
			if (active) {
				// Get new level
				int newLevel = getWarningLevelForMember(member);

				// Update warning roles and timer
				Utilities.Warnings.updateWarningLevel(member, newLevel, Optional.empty()).queue();
				if (!scheduler.isWarningTimerRunning(member))
					scheduler.createWarningTimer(member, CustomJailPlugin.getWarningTime(member.getGuild()));
			}
		}

		return caseID;
	}

	/**
	 * {@inheritDoc} <br>
	 * <br>
	 * If the specified warning was {@code active}, then the member linked to this
	 * case will have their roles updated to match their new level. Additionally, if
	 * the member's new warning level is 0, then their warning timer will be
	 * removed.
	 * 
	 * @throws IllegalArgumentException Thrown if {@code case_id} is less than
	 *                                  {@code 0}
	 */
	@Override
	public boolean deleteWarning(Guild guild, int case_id, Optional<Member> moderator, Optional<String> reason)
			throws IllegalArgumentException {
		return getWarning(guild, case_id).map(warning -> {
			boolean removed = database.deleteWarning(guild, case_id);

			if (removed) {
				logger.info("Deleted warning [{}]", case_id);

				if (warning.active()) {
					Member member = guild.getMemberById(warning.memberID());
					// Get new level
					int newLevel = getWarningLevelForMember(member);

					// Remove warning timer if no more active warnings left
					if (newLevel == 0 && isWarningTimerRunning(member))
						scheduler.removeWarningTimer(member);

					// Update warning roles
					Utilities.Warnings.updateWarningLevel(member, newLevel, Optional.empty()).queue();
				}

				// Fire event if moderator is not self member
				if ((moderator.isPresent() && !moderator.get().equals(guild.getSelfMember())) || moderator.isEmpty())
					bus.fireEvent(new WarningRemovedEvent(WarningDetails.fromData(guild.getJDA(), warning), moderator,
							reason));
			}

			return removed;
		}).orElse(false);
	}

	@Override
	public boolean deleteWarnings(Member member) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @throws IllegalArgumentException Thrown if {@code case_id} is less than zero
	 */
	@Override
	public boolean updateWarningReason(Guild guild, int case_id, Optional<Member> moderator, String newReason,
			Optional<String> reason) throws IllegalArgumentException {
		// Get warning if present
		return getWarning(guild, case_id).map(warning -> {
			// Update warning reason
			boolean updated = database.updateWarningReason(guild, case_id, newReason);
			// If updated, fire event
			if (updated)
				bus.fireEvent(
						new WarningReasonUpdateEvent(guild.getJDA(), database.getWarning(guild, case_id).orElseThrow(),
								moderator, reason, Optional.ofNullable(warning.reason())));
			return updated;
		}).orElse(false);
	}

	@Override
	public boolean isJailed(Member member) {
		return scheduler.isJailed(member);
	}

	@Override
	public boolean isJailTimerRunning(Member member) {
		return scheduler.isJailTimerRunning(member);
	}

	@Override
	public Optional<Duration> getRemainingJailTime(Member member) {
		return scheduler.getRemainingJailTime(member);
	}

	@Override
	@NotNull
	public Optional<UnixTimestamp> getWarningEndTimestamp(@NotNull Member member) {
		return scheduler.getWarningEndTimestamp(member);
	}

	@Override
	@NotNull
	public Optional<UnixTimestamp> getJailEndTimestamp(@NotNull Member member) {
		return scheduler.getJailEndTimestamp(member);
	}

	@Override
	public boolean isWarningTimerRunning(Member member) {
		return scheduler.isWarningTimerRunning(member);
	}

	@Override
	public int getWarningLevelForMember(Member member) {
		return database.getWarningLevelForMember(member);
	}

	@Override
	public Optional<Warning> getWarning(Guild guild, int case_id) throws IllegalArgumentException {
		return database.getWarning(guild, case_id);
	}

	@Override
	public int getTotalWarnings(Member member) {
		return database.getTotalWarnings(member);
	}

	@Override
	public Warning[] getWarningsPageForMember(Member member, int itemsPerPage, int page) {
		return database.getWarningsPageForMember(member, itemsPerPage, page);
	}

	@Override
	public Optional<JailDetails> getJailDetails(Member member) throws InternalException {
		try {
			return scheduler.getJailDetails(member);
		} catch (SchedulerException e) {
			throw new InternalException(e);
		}
	}

	@Override
	public CompletableFuture<Void> updateRolesToDatabase(Guild guild) {
		CompletableFuture<Void> cf = new CompletableFuture<>();
		logger.warn("Updating roles to database in {}", guild);

		Member self = guild.getSelfMember();
		CustomTime warningTime = CustomJailPlugin.getWarningTime(guild);
		List<Role> warningRoles = CustomJailPlugin.getWarningRoles(guild);

		guild.findMembers(m -> {
			for (Role r : m.getRoles())
				if (warningRoles.contains(r))
					return true;
			return false;
		}).onSuccess(members -> {
			logger.warn("Found {} members with warning roles in {}", members.size(), guild);
			try {
				members.forEach(member -> {
					int databaseLevel = getWarningLevelForMember(member);
					int roleLevel = CustomJailPlugin.getWarningLevelFromRoles(member);

					if (databaseLevel == roleLevel)
						return;

					if (databaseLevel < roleLevel) {
						for (int i = databaseLevel; i < roleLevel; i++)
							addWarningForMember(member, self, Optional.of("Fixing Warning Levels"), true, true);
					}

					if (!scheduler.isWarningTimerRunning(member)) {
						if (scheduler.createWarningTimer(member, warningTime))
							bus.fireEvent(new WarningTimerStartEvent(member, scheduler.getWarningEndDate(member)));
					}

					Utilities.Warnings.updateWarningLevel(member, getWarningLevelForMember(member), Optional.empty())
							.queue();
				});
				cf.complete(null);
			} catch (Exception e) {
				cf.completeExceptionally(e);
			}
		}).onError(err -> cf.completeExceptionally(err));
		return cf;
	}

	@Override
	public IJailEventBus getEventManager() {
		return bus;
	}

	@Override
	public String getEmbedFooter() {
		return EMBED_FOOTER;
	}

	/**
	 * Validate a {@link Guild}'s jail settings.
	 * 
	 * @param guild - guild to validate
	 * 
	 * @throws InternalException Thrown if settings are invalid
	 */
	private static void validateSettings(Guild guild) throws InternalException {
		Member self = guild.getSelfMember();
		GuildMessageChannel timeoutChannel = CustomJailPlugin.getJailChannel(guild);
		Role timeoutRole = CustomJailPlugin.getTimeoutRole(guild);

		if (timeoutRole == null)
			throw new InternalException("Jail role is invalid or not set!");

		if (timeoutChannel == null)
			throw new InternalException("Jail channel is invalid or not set!");

		if (!(timeoutChannel.canTalk() && self.hasPermission(timeoutChannel, Permission.MESSAGE_EMBED_LINKS)))
			throw new InternalException("Bot is unable to send embeds in jail channel!");

		List<Role> warningRoles = CustomJailPlugin.getWarningRoles(guild);

		if (CustomJailPlugin.getWarningRoles(guild).isEmpty())
			throw new InternalException("Warning roles are invalid or not set!");

		if (!self.canInteract(timeoutRole) || warningRoles.stream().anyMatch(r -> !self.canInteract(r)))
			throw new InternalException("Bot is unable to give the timeout/warning role!");
	}

	/**
	 * Handle an {@link Exception}. Any {@link Exception} thrown by the
	 * {@link ErrorHandler} is wrapped in a {@link RuntimeException} and thrown
	 * again.
	 * 
	 * @param <E>                  Exception type
	 * @param err                  - error handler
	 * @param exceptionConstructor - exception constructor
	 * @param message              - message of thrown error
	 * @param cause                - exception cause
	 */
	private static <E extends Exception> void handleError(ErrorHandler<E> err,
			BiFunction<String, Throwable, E> exceptionConstructor, String message, Throwable cause) {
		try {
			err.accept(exceptionConstructor.apply(message, cause));
		} catch (Exception e1) {
			throw new RuntimeException(e1);
		}
	}
}
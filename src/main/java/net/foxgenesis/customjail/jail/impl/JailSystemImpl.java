package net.foxgenesis.customjail.jail.impl;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.lang3.function.TriConsumer;
import org.jetbrains.annotations.NotNull;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.foxgenesis.customjail.database.CustomJailConfiguration;
import net.foxgenesis.customjail.database.CustomJailConfigurationService;
import net.foxgenesis.customjail.database.warning.Warning;
import net.foxgenesis.customjail.database.warning.WarningDatabase;
import net.foxgenesis.customjail.event.JailTimerStartedEvent;
import net.foxgenesis.customjail.event.JailedEvent;
import net.foxgenesis.customjail.event.UnjailEvent;
import net.foxgenesis.customjail.event.UnresolvedUnjailEvent;
import net.foxgenesis.customjail.event.warning.WarningAddedEvent;
import net.foxgenesis.customjail.event.warning.WarningLevelDecreasedEvent;
import net.foxgenesis.customjail.event.warning.WarningRemovedEvent;
import net.foxgenesis.customjail.event.warning.WarningUpdatedEvent;
import net.foxgenesis.customjail.event.warning.WarningsClearedEvent;
import net.foxgenesis.customjail.jail.JailDetails;
import net.foxgenesis.customjail.jail.JailScheduler;
import net.foxgenesis.customjail.jail.JailSystem;
import net.foxgenesis.customjail.jail.exception.AlreadyJailedException;
import net.foxgenesis.customjail.jail.exception.CannotInteractException;
import net.foxgenesis.customjail.jail.exception.NotEnabledException;
import net.foxgenesis.customjail.jail.exception.NotJailedException;
import net.foxgenesis.customjail.jail.exception.NotSetupException;
import net.foxgenesis.customjail.util.CustomTime;
import net.foxgenesis.customjail.util.Utilities;
import net.foxgenesis.springJDA.SpringJDA;
import net.foxgenesis.watame.util.discord.Colors;
import net.foxgenesis.watame.util.discord.DiscordLogger;
import net.foxgenesis.watame.util.discord.DiscordUtils;
import net.foxgenesis.watame.util.discord.ModeratorActionEvent;
import net.foxgenesis.watame.util.discord.Response;
import net.foxgenesis.watame.util.lang.LocalizedEmbedBuilder;

/**
 * Standard implementation of a {@link JailSystem}.
 * 
 * @author Ashley
 */
public class JailSystemImpl extends ListenerAdapter
		implements JailSystem, ApplicationEventPublisherAware, MessageSourceAware {

	private final Logger logger = LoggerFactory.getLogger(JailSystem.class);

	private final String[] timings;

	private ApplicationEventPublisher publisher;

	private MessageSource messages;

	@Autowired
	private CustomJailConfigurationService service;

	@Autowired
	private WarningDatabase warningDatabase;

	@Autowired
	private DiscordLogger discordLogger;

	@Autowired
	private JailScheduler scheduler;

	@Autowired
	private SpringJDA jda;

	public JailSystemImpl(String[] timings) {
		this.timings = Objects.requireNonNull(timings);
	}

	@Override
	public String[] getJailTimings() {
		return Arrays.copyOf(timings, timings.length);
	}

	@Override
	public boolean isJailed(long guild, long member) {
		return scheduler.isJailed(guild, member);
	}

	@Override
	public boolean isJailTimerRunning(Member member) {
		return scheduler.isJailTimerRunning(member);
	}

	@Override
	public void jail(Member member, Member moderator, CustomTime time, String reason, boolean addWarning, boolean anon)
			throws AlreadyJailedException {
		if (member == null)
			throw new CannotInteractException("customjail.no-target");

		User user = member.getUser();
		if (user.isBot() || user.isSystem())
			throw new CannotInteractException("customjail.bot-user");

		if (moderator != null)
			if (moderator.getGuild().getIdLong() != member.getGuild().getIdLong())
				throw new CannotInteractException("customjail.not-from-same-guild");

		if (moderator != null && !moderator.isOwner()) {
			if (moderator.getIdLong() == member.getIdLong())
				throw new CannotInteractException("customjail.self");

			if (!moderator.canInteract(member))
				throw new CannotInteractException("customjail.no-interact");
		}

		if (isJailed(member))
			throw new AlreadyJailedException();

		Guild guild = member.getGuild();

		// Validate settings
		validateSettings(guild, (config, jailChannel, jailRole) -> {
			// Add role to member
			logger.debug("Attempting to jail {} in {}", member, guild);
			logger.debug("member {}\ttime {}\treason {}\taddWarning {}", member, time, reason, addWarning);
			guild.addRoleToMember(member, jailRole)
					// create warning if applicable, create jail timer and post embed in jail
					// channel
					.flatMap(v -> {
						Optional<Warning> warning = addWarning
								? Optional.of(warningDatabase.addWarning(member, moderator, reason, true))
								: Optional.empty();

						try {
							scheduler.createJailTimer(member, moderator, time, reason,
									warning.map(Warning::getId).orElse(-1L));
						} catch (SchedulerException e) {
							warning.ifPresent(warningDatabase::delete);
							throw new RuntimeException(e);
						}

						// Create the jail embed
						Locale locale = discordLogger.getEffectiveLocale(guild);
						MessageEmbed embed = createJailEmbed(member, moderator, warning.map(Warning::getId), time,
								reason, anon, locale);
						Button button = Button.primary(Utilities.Interactions.wrapInteraction("startjail", member),
								messages.getMessage("customjail.embed.accept", null, locale));

						// Send jail message
						return jailChannel.sendMessage(member.getAsMention())
								// Set embeds
								.setEmbeds(embed)
								// Add accept button
								.addActionRow(button)
								// Publish event on success
								.onSuccess(m -> {
									logger.info("{} jailed {} in {} for {} \"{}\"", moderator, member, guild,
											time.getDisplayString(), reason);
									kickFromVoiceChat(member);

									warning.ifPresent(w -> {
										logger.info("Adding warning for {}: member={} mod={} active={} reason={}",
												member, member, moderator, true, reason);
										if (w.isActive())
											onWarningLevelChanged(config, member, reason);
										fireEvent(new WarningAddedEvent(member, moderator, w));
									});

									fireEvent(new JailedEvent(member, moderator, time, reason,
											warning.map(Warning::getId)));
								});
					}).queue();
		});
	}

	@Override
	public void unjail(long guild, long member, Member moderator, String reason) throws NotJailedException {
		if (member < 10000000000000000L || member > 9223372036854775807L)
			throw new CannotInteractException("customjail.no-target");

		if (moderator != null)
			if (moderator.getGuild().getIdLong() != guild)
				throw new CannotInteractException("customjail.not-from-same-guild");

		ifEnabledOrError(guild, config -> {
			if (!isJailed(guild, member))
				throw new NotJailedException();

			logger.debug("attempting unjailing {} in {}", member, guild);

			if (!scheduler.removeJailTimer(guild, member))
				throw new RuntimeException("Failed to unjail member [" + member + "] in " + guild);

			Guild _guild = jda.getGuildById(guild);
			if (_guild == null)
				throw new IllegalArgumentException("JDA unable to resolve guild " + guild);

			// Attempt to find member in guild
			_guild.retrieveMemberById(member).queue(m -> {
				// Attempt to remove jail role from member
				Role jailRole = _guild.getRoleById(config.getJailRole());
				_guild.removeRoleFromMember(m, jailRole).queue();

				// Start warning timer if there are active warnings
				if (getWarningLevel(guild, member) > 0)
					scheduler.createWarningTimer(m, config.getWarningTime());

				logger.info("Unjailed {} in {}", m, guild);

				// Fire un-jail event
				fireEvent(new UnjailEvent(m, moderator, reason));

			}, new ErrorHandler()
					// Handle unknown user/member
					.handle(Set.of(ErrorResponse.UNKNOWN_MEMBER, ErrorResponse.UNKNOWN_USER),
							e -> fireEvent(new UnresolvedUnjailEvent(_guild, member, moderator, reason)))
					// Log errors
					.andThen(e -> logger.error("Error while unjailing " + member + " in guild " + _guild, e)));
		});
	}

	@Override
	public Date startJailTimer(Member member, Member moderator, String reason) throws NotJailedException {
		if (moderator != null)
			if (moderator.getGuild().getIdLong() != member.getGuild().getIdLong())
				throw new CannotInteractException("customjail.not-from-same-guild");

		if (!isJailed(member))
			throw new NotJailedException();

		logger.info("Starting jail timer for {}", member);
		Date date = scheduler.startJailTimer(member);

		fireEvent(new JailTimerStartedEvent(member, moderator, reason, date));

		return date;
	}

	@Override
	public JailDetails getJailDetails(Member member) throws NotJailedException {
		logger.debug("Getting jail details for {}", member);
		try {
			return scheduler.getJailDetails(member).orElseThrow(NotJailedException::new);
		} catch (SchedulerException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Optional<String> getJailEndTimestamp(Member member) {
		return scheduler.getJailEndTimestamp(member);
	}

	private MessageEmbed createJailEmbed(Member member, Member moderator, Optional<Long> caseId, CustomTime time,
			String reason, boolean anon, Locale locale) {
		// Create the jail embed
		LocalizedEmbedBuilder jailEmbedBuilder = new LocalizedEmbedBuilder(messages, locale);
		jailEmbedBuilder.setColor(Colors.ERROR);
		jailEmbedBuilder.setLocalizedTitle("customjail.embed.jailed");
		jailEmbedBuilder.setThumbnail(member.getEffectiveAvatarUrl());

		// Row 1
		jailEmbedBuilder.addLocalizedField("customjail.embed.member", member.getAsMention(), true);
		if (anon)
			jailEmbedBuilder.addLocalizedFieldAndValue("customjail.embed.moderator", "customjail.anonymous", true,
					null);
		else
			jailEmbedBuilder.addLocalizedField("customjail.embed.moderator", moderator.getAsMention(), true);
		jailEmbedBuilder.addLocalizedField("customjail.embed.caseid",
				caseId.map(id -> "" + id).orElseGet(() -> messages.getMessage("customjail.embed.na", null, locale)),
				true);

		// Row 2
		jailEmbedBuilder.addLocalizedField("customjail.embed.duration",
				time.getLocalizedDisplayString(messages, locale), true);

		// Row 3
		jailEmbedBuilder.addLocalizedField("customjail.embed.reason",
				Optional.ofNullable(reason).filter(r -> !r.isBlank())
						.orElseGet(() -> messages.getMessage("customjail.embed.defaultReason", null, locale)),
				false);

		jailEmbedBuilder.setTimestamp(Instant.now());
		jailEmbedBuilder.setLocalizedFooter("customjail.footer");

		return jailEmbedBuilder.build();
	}

	// ===========================================================================================================

	@Override
	public int getTotalWarnings(Member member) {
		return warningDatabase.getWarningCount(member);
	}

	@Override
	public int getWarningLevel(long guild, long member) {
		return warningDatabase.getWarningLevel(guild, member);
	}

	@Override
	public Optional<Warning> findWarning(long id) {
		return warningDatabase.findById(id);
	}

	@Override
	public Page<Warning> getWarningPage(Member member, Pageable pageable) {
		return warningDatabase.findAllByMember(member, pageable);
	}

	@Override
	public Warning addWarning(Member member, Member moderator, String reason, boolean active) {
		if (moderator != null)
			if (moderator.getGuild().getIdLong() != member.getGuild().getIdLong())
				throw new CannotInteractException("customjail.not-from-same-guild");

		// TODO: Sanitize warning before adding to database
		return mapEnabled(member.getGuild(), config -> {
			logger.info("Adding warning for {}: member={} mod={} active={} reason={}", member, member, moderator,
					active, reason);
			Warning out = warningDatabase.addWarning(member, moderator, reason, active);
			if (active)
				onWarningLevelChanged(config, member, reason);
			fireEvent(new WarningAddedEvent(member, moderator, out));
			return out;
		});
	}

	@Override
	public void deleteWarning(Warning warning, Member moderator, String reason) {
		if (moderator != null)
			if (moderator.getGuild().getIdLong() != warning.getGuild())
				throw new CannotInteractException("customjail.warning-not-from-guild");

		ifEnabledOrError(warning.getGuild(), config -> {
			logger.info("Deleting warning {}", warning);
			warningDatabase.delete(warning);
			Guild guild = jda.getGuildById(warning.getGuild());

			if (guild == null)
				throw new IllegalArgumentException(
						"Failed to find guild " + warning.getGuild() + " while deleting warning");

			guild.retrieveMemberById(warning.getMember()).queue(member -> {
				if (warning.isActive())
					onWarningLevelChanged(config, member, reason);

				fireEvent(new WarningRemovedEvent(member, moderator, reason, warning));
			});
		});
	}

	@Override
	public Warning updateWarningReason(Warning warning, Member moderator, String newReason, String reason) {
		if (moderator != null)
			if (moderator.getGuild().getIdLong() != warning.getGuild())
				throw new CannotInteractException("customjail.warning-not-from-guild");

		logger.info("Updating warning reason {} -> {}", warning, newReason);

		Warning old = warning.copy();
		warning.setReason(newReason);
		Warning newW = warningDatabase.save(warning);

		Guild guild = jda.getGuildById(warning.getGuild());

		if (guild == null)
			throw new IllegalArgumentException(
					"Failed to find guild " + warning.getGuild() + " while updating warning");

		guild.retrieveMemberById(warning.getMember()).queue(member -> {
			fireEvent(new WarningUpdatedEvent(member, moderator, reason, old, newW));
		});

		return newW;
	}

	@Override
	public void clearWarnings(Member member, Member moderator, String reason) {
		ifEnabledOrError(member.getGuild(), config -> {
			if (moderator != null)
				if (moderator.getGuild().getIdLong() != member.getGuild().getIdLong())
					throw new CannotInteractException("customjail.warning-not-from-guild");

			logger.info("Clearing warnings for {}", member);
			warningDatabase.deleteByMember(member);
			onWarningLevelChanged(config, member, reason);
			fireEvent(new WarningsClearedEvent(member, moderator, reason));
		});
	}

	@Override
	public void clearWarnings(Guild guild) {
		logger.info("Clearing all warnings in {}", guild);
		warningDatabase.deleteByGuild(guild);
		try {
			scheduler.removeAllTimer(guild);
		} catch (SchedulerException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void decreaseWarningLevel(Member member, Member moderator, String reason) {
		if (moderator != null)
			if (moderator.getGuild().getIdLong() != member.getGuild().getIdLong())
				throw new CannotInteractException("customjail.not-from-same-guild");

		ifEnabledOrError(member.getGuild(), config -> {
			int currentWarningLevel = getWarningLevel(member);
			if (currentWarningLevel > 0) {
				logger.info("Decreasing warning level for {}: {} -> {}", member, currentWarningLevel,
						currentWarningLevel - 1);
				warningDatabase.decreaseWarningLevel(member.getGuild().getIdLong(), member.getIdLong());
				onWarningLevelChanged(config, member, reason);
				fireEvent(new WarningLevelDecreasedEvent(member, moderator, reason, currentWarningLevel));
			}
		});
	}

	@Override
	public void decreaseWarningLevel(long guild, long member, Member moderator, String reason) {
		if (moderator != null)
			if (moderator.getGuild().getIdLong() != guild)
				throw new CannotInteractException("customjail.not-from-same-guild");

		Guild _guild = jda.getGuildById(guild);
		if (_guild == null)
			throw new IllegalArgumentException("Unknown guild");

		_guild.retrieveMemberById(member).queue(_member -> decreaseWarningLevel(_member, moderator, reason));
	}

	@Override
	public void fixMember(Member member) {
		ifEnabled(member.getGuild(), config -> {
			logger.debug("Fixing warning/jail timers for {}", member);
			Guild guild = member.getGuild();
			if (isJailed(member)) {
				Role jailRole = getJailRole(guild, config);
				guild.addRoleToMember(member, jailRole).queue();
			} else
				onWarningLevelChanged(config, member,
						messages.getMessage("customjail.reason.fix", null, discordLogger.getEffectiveLocale(guild)));
		});
	}

	// ===========================================================================================================

	@Override
	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		fixMember(event.getMember());
	}

	@Override
	public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
		if (scheduler.isWarningTimerRunning(event.getGuild().getIdLong(), event.getUser().getIdLong())) {
			logger.info("Member {} left {} while warning timer running. Removing timer...", event.getUser(),
					event.getGuild());
			scheduler.removeWarningTimer(event.getGuild().getIdLong(), event.getUser().getIdLong());
		}
	}

	@Override
	public void onGuildLeave(GuildLeaveEvent event) {
		Guild guild = event.getGuild();
		logger.info("Left guild {}. Removing all timers and warnings");
		clearWarnings(guild);
		service.delete(guild);
	}

	@Override
	public void onButtonInteraction(ButtonInteractionEvent event) {
		if (!event.isFromGuild())
			return;

		if (Utilities.Interactions.unwrapInteraction(event, (id, unwrappedMember, variant) -> {
			unwrappedMember.ifPresentOrElse(member -> {
				Member pressed = event.getMember();

				switch (id) {
				case "startjail" -> {
					// Check if enabled
					if (!service.isEnabled(event.getGuild())) {
						error(event, "customjail.not-enabled").queue();
						return;
					}

					// Check if member is jailed
					if (!isJailed(member)) {
						error(event, "customjail.notJailed").queue();
						return;
					}

					// Ensure button belongs to who pressed it
					if (!pressed.equals(member)) {
						error(event, "customjail.not-your-punishment").queue();
						return;
					}

					Locale locale = event.getUserLocale().toLocale();
					Button successButton = Button
							.success("jailAccepted", messages.getMessage("customjail.embed.accepted", null, locale))
							.asDisabled();

					if (scheduler.isJailTimerRunning(member)) {
						error(event, "customjail.timerAlreadyStarted").queue();

						// Set accepted if not already
						if (event.getButton().getStyle() != ButtonStyle.SUCCESS)
							event.editButton(successButton).queue();
						return;
					}

					// Display working
					event.deferReply(true).flatMap(hook -> {

						Date date = startJailTimer(member, null, null);

						return hook.editOriginalEmbeds(
								Response.success(messages.getMessage("customjail.embed.time-remaining",
										new Object[] { TimeFormat.RELATIVE.atInstant(date.toInstant()) }, locale)))
								.and(event.editButton(successButton));
					}).queue();
				}
				}
			},
					// Unable to find member button was wrapped to
					() -> {
						MessageEmbed errorMsg = Response.error(messages.getMessage("customjail.embed.no-target", null,
								event.getUserLocale().toLocale()));
						RestAction<?> edit = (event.isAcknowledged()
								? event.getHook().editOriginalEmbeds(errorMsg).setReplace(true)
								: event.replyEmbeds(errorMsg).setEphemeral(true));
						event.editButton(event.getButton().asDisabled()).flatMap(o -> edit).queue();
					});
		}))
			return;
	}

	// ===========================================================================================================

	@EventListener
	public void onWarningAdded(WarningAddedEvent event) {
		defaultEventHandle(event, true,
				(locale, guild, user) -> notifyMember(locale, guild, user, "customjail.notify.warned", Colors.WARNING,
						// Case ID
						"customjail.embed.caseid", "" + event.getWarning().getId(),
						// Reason
						"customjail.embed.reason", '*' + event.getReason(messages, locale) + '*'));

	}

	@EventListener
	public void onWarningRemoved(WarningRemovedEvent event) {
		defaultEventHandle(event, true,
				(locale, guild, user) -> notifyMember(locale, guild, user, "customjail.notify.warning-deleted",
						Colors.NOTICE,
						// Case ID
						"customjail.embed.caseid", "" + event.getWarning().getId(),
						// Reason
						"customjail.embed.reason", '*' + event.getReason(messages, locale) + '*'));
	}

	@EventListener
	public void onWarningUpdatedEvent(WarningUpdatedEvent event) {
		defaultEventHandle(event, true, null);
	}

	@EventListener
	public void onWarningLevelDecreased(WarningLevelDecreasedEvent event) {
		defaultEventHandle(event, false,
				(locale, guild, user) -> notifyMember(locale, guild, user, "customjail.embed.warning-level-decreased",
						Colors.NOTICE,
						// Warning level
						"customjail.embed.warning-level", event.getOriginalLevel() + " \u2192 " + event.getNewLevel(),
						// Reason
						"customjail.embed.reason", '*' + event.getReason(messages, locale) + '*'));
	}

	@EventListener
	public void onJailed(JailedEvent event) {
		defaultEventHandle(event, true,
				(locale, guild, user) -> notifyMember(locale, guild, user, "customjail.notify.jailed", Colors.ERROR,
						// Case ID
						"customjail.embed.caseid", event.getCaseId(messages, locale),
						// Reason
						"customjail.embed.reason", '*' + event.getReason(messages, locale) + '*'));
	}

	@EventListener
	public void onUnJail(UnjailEvent event) {
		defaultEventHandle(event, false,
				(locale, guild, user) -> notifyMember(locale, guild, user, "customjail.notify.unjailed", Colors.NOTICE,
						// Reason
						"customjail.embed.reason", '*' + event.getReason(messages, locale) + '*'));
	}

	@EventListener
	public void onJailTimerStarted(JailTimerStartedEvent event) {
		defaultEventHandle(event, false,
				(locale, guild, user) -> notifyMember(locale, guild, user, "customjail.notify.timer-started",
						Colors.NOTICE,
						// Reason
						"customjail.embed.reason", '*' + event.getReason(messages, locale) + '*',
						// Time left
						"customjail.embed.time-left", TimeFormat.RELATIVE.format(event.getEndDate().getTime())));
	}

	private <E extends ModeratorActionEvent> void defaultEventHandle(E event, boolean onlyIfModerator,
			@Nullable TriConsumer<Locale, Guild, User> notifyMember) {
		logger.debug("Loggable Event fired: {}", event);

		if (onlyIfModerator && event.getModerator().isEmpty())
			return;

		// Only if enabled
		ifEnabled(event.getGuild(), config -> {
			Guild guild = event.getGuild();
			Member member = event.getMember();
			Locale locale = discordLogger.getEffectiveLocale(guild);

			// Send DM message to user
			if (notifyMember != null && config.isNotifyMember()
					&& !(member.getUser().isBot() && member.getUser().isSystem()))
				notifyMember.accept(locale, guild, member.getUser());

			discordLogger.modlog(guild, config.getLogChannel(), event);
		});
	}

	/**
	 * Send a notification to a {@link Member} with the specified {@code title},
	 * {@code color} and {@code fields}.
	 * 
	 * @param member - member to send the notification to
	 * @param title  - title of the notification
	 * @param color  - color of the notification
	 * @param fields - map containing the content of the notification
	 * 
	 * @return Returns a {@link RestAction} that will send the notification
	 * 
	 * @throws NullPointerException     Thrown if {@code member} or {@code title} is
	 *                                  {@code null}
	 * @throws IllegalArgumentException Thrown if {@code title} is blank
	 */
	private void notifyMember(Locale locale, Guild guild, User user, String title, int color,
			@NotNull Map<String, String> fields) {
		Objects.requireNonNull(user);
		Objects.requireNonNull(title);
		if (title.isBlank())
			throw new IllegalArgumentException("Title must not be empty!");

		// Send DM message to member
		if (user.isBot() || user.isSystem())
			return;

		user.openPrivateChannel()
				// Send embed if we can talk in the channel
				.flatMap(PrivateChannel::canTalk, channel -> {
					// Create embed
					LocalizedEmbedBuilder builder = new LocalizedEmbedBuilder(messages, locale);
					builder.setColor(color);
					builder.setLocalizedTitle(title, locale);
					builder.setThumbnail(guild.getIconUrl());
					builder.setTimestamp(Instant.now());
					builder.setLocalizedFooter("customjail.footer");

					String field = "**%s: ** %s\n";
					StringBuilder b = builder.getDescriptionBuilder();

					String guildName = MarkdownUtil.maskedLink(guild.getName(),
							DiscordUtils.getGuildProtocolLink(guild));
					b.append(field.formatted(messages.getMessage("customjail.notify.in", null, locale), guildName));

					fields.forEach(
							(key, value) -> b.append(field.formatted(messages.getMessage(key, null, locale), value)));

					return channel.sendMessageEmbeds(builder.build());
				})
				// Send
				.queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
	}

	/**
	 * Send a notification to a {@link Member} with the specified {@code title},
	 * {@code color} and {@code fields}.
	 * <p>
	 * This method is effectively equivalent to:
	 * </p>
	 * 
	 * <pre>
	 * Map<String, String> map = new HashMap<>();
	 * for (int i = 0; i < fields.length; i += 2)
	 * 	map.put(fields[i], fields[i + 1]);
	 * return notifyMember(member, title, color, map);
	 * </pre>
	 * 
	 * @param member - member to send the notification to
	 * @param title  - title of the notification
	 * @param color  - color of the notification
	 * @param fields - array containing key value pairs with the content of the
	 *               notification
	 * 
	 * @return Returns a {@link RestAction} that will send the notification
	 * 
	 * @throws NullPointerException     Thrown if {@code member}, {@code title} or
	 *                                  {@code fields} is {@code null}
	 * @throws IllegalArgumentException Thrown if {@code title} is blank,
	 *                                  {@code fields} is empty or
	 *                                  {@code fields.length} is not a multiple of
	 *                                  two
	 */
	private void notifyMember(Locale locale, Guild guild, User user, @NotNull String title, int color,
			@NotNull String... fields) {
		Objects.requireNonNull(fields);
		if (fields.length == 0)
			throw new IllegalArgumentException("Fields must not be empty!");
		if ((fields.length & 1) == 1)
			throw new IllegalArgumentException("Fields must be a multiple of two!");
		Map<String, String> map = new HashMap<>();
		for (int i = 0; i < fields.length; i += 2)
			map.put(fields[i], fields[i + 1]);
		notifyMember(locale, guild, user, title, color, map);
	}

	// ===========================================================================================================

	private void validateSettings(Guild guild,
			TriConsumer<CustomJailConfiguration, GuildMessageChannel, Role> validSettings) {
		ifEnabledOrError(guild,
				config -> validSettings.accept(config, getJailChannel(guild, config), getJailRole(guild, config)));
	}

	private Role getJailRole(Guild guild, CustomJailConfiguration config) {
		long jailRoleId = Optional.ofNullable(config.getJailRole())
				.orElseThrow(() -> new NotSetupException("customjail.invalid-jail-role"));

		Role jailRole = guild.getRoleById(jailRoleId);
		if (jailRole == null)
			throw new NotSetupException("customjail.invalid-jail-role");

		if (!guild.getSelfMember().canInteract(jailRole))
			throw new NotSetupException("customjail.can-not-assign", jailRole.getName());

		return jailRole;
	}

	private GuildMessageChannel getJailChannel(Guild guild, CustomJailConfiguration config) {
		GuildChannel union = Optional.ofNullable(config.getJailChannel())
				// Get as channel union
				.map(id -> guild.getChannelById(GuildChannel.class, id))
				// Or throw invalid channel
				.orElseThrow(() -> new NotSetupException("customjail.no-jail-channel"));

		if (union instanceof GuildMessageChannel jailChannel) {
			if (!(jailChannel.canTalk()
					&& guild.getSelfMember().hasPermission(jailChannel, Permission.MESSAGE_EMBED_LINKS)))
				throw new NotSetupException("customjail.jail-channel-missing-permissions",
						"VIEW_CHANNEL, MESSAGE_SEND, MESSAGE_SEND_IN_THREADS, MESSAGE_EMBED_LINKS");

			return jailChannel;
		}

		throw new NotSetupException("customjail.not-message-channel", union.getName());

	}

	// ===========================================================================================================

	private void fireEvent(ApplicationEvent event) {
		CompletableFuture.runAsync(() -> publisher.publishEvent(event));
	}

	private void ifEnabled(Guild guild, Consumer<CustomJailConfiguration> config) {
		service.get(guild).filter(CustomJailConfiguration::isEnabled).ifPresent(config);
	}

	private void ifEnabledOrError(Guild guild, Consumer<CustomJailConfiguration> config) {
		config.accept(
				service.get(guild).filter(CustomJailConfiguration::isEnabled).orElseThrow(NotEnabledException::new));
	}

	private void ifEnabledOrError(long guild, Consumer<CustomJailConfiguration> config) {
		config.accept(
				service.get(guild).filter(CustomJailConfiguration::isEnabled).orElseThrow(NotEnabledException::new));
	}

	private <T> T mapEnabled(Guild guild, Function<CustomJailConfiguration, T> mapper) {
		return mapper.apply(
				service.get(guild).filter(CustomJailConfiguration::isEnabled).orElseThrow(NotEnabledException::new));
	}

	private void onWarningLevelChanged(CustomJailConfiguration config, Member member, String reason) {
		logger.debug("Warning level changed for {}: {}", member, reason);
		scheduler.stopWarningTimerIfRunning(member);

		int newLevel = getWarningLevel(member);
		updateWarningRoles(config, member, newLevel, Optional.ofNullable(reason)).queue();

		if (!isJailed(member) && newLevel > 0)
			scheduler.createWarningTimer(member, config.getWarningTime());
	}

	private RestAction<Void> updateWarningRoles(CustomJailConfiguration config, Member member, int level,
			Optional<String> reason) {
		logger.info("Updating warning roles for {} [level: {}]: {}", member, level,
				reason.orElse("No Reason Provided"));

		// Modify user roles
		return Objects.requireNonNull(member)
				// Get guild
				.getGuild()
				// Modify roles
				.modifyMemberRoles(member, modifyWarningRoles(config, member, level))
				// Add reason
				.reason(reason.orElseGet(() -> messages.getMessage("customjail.embed.defaultReason", null,
						discordLogger.getEffectiveLocale(member.getGuild()))));
	}

	private Set<Role> modifyWarningRoles(CustomJailConfiguration config, Member member, int level) {
		Objects.requireNonNull(member);

		Guild guild = member.getGuild();
		List<Role> warningRoles = getWarningRoles(config, guild);

		int maxLevel = Math.min(config.getMaxWarnings(), warningRoles.size());
		int newLevel = Utilities.clamp(level, 0, maxLevel);

		List<List<Role>> split = Utilities.split(warningRoles, newLevel);

		Set<Role> roles = new HashSet<>(member.getRoles());
		roles.addAll(split.get(0));
		roles.removeAll(split.get(1));
		return roles;
	}

	private List<Role> getWarningRoles(CustomJailConfiguration config, Guild guild) {
		return guild.getRoles()
				// Stream
				.stream()
				// Filter roles that start with prefix
				.filter(r -> r.getName().startsWith(config.getWarningsPrefix()))
				// Reverse order
				.sorted(Comparator.reverseOrder())
				// As set
				.toList();
	}

	private ReplyCallbackAction error(IReplyCallback event, String code, Object... args) {
		return event
				.replyEmbeds(Response.error(messages.getMessage(code, args, code, event.getUserLocale().toLocale())))
				.setEphemeral(true);
	}

	private void kickFromVoiceChat(Member member) {
		Optional.ofNullable(member.getVoiceState())
				// Only if in voice chat
				.filter(GuildVoiceState::inAudioChannel)
				// Only if we have permission
				.filter(state -> member.getGuild().getSelfMember().hasPermission(state.getChannel(),
						Permission.VOICE_MOVE_OTHERS))
				// Kick from voice chat
				.ifPresent(vcState -> member.getGuild().kickVoiceMember(member).queue());
	}

	@Override
	public void setMessageSource(MessageSource messageSource) {
		this.messages = messageSource;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.publisher = applicationEventPublisher;
	}
}

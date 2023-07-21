package net.foxgenesis.customjail;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quartz.SchedulerException;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.foxgenesis.customjail.database.WarningDatabase;
import net.foxgenesis.customjail.jail.IJailSystem;
import net.foxgenesis.customjail.jail.JailSystem;
import net.foxgenesis.customjail.jail.event.JailEventAdapter;
import net.foxgenesis.customjail.jail.event.impl.JailTimerStartEvent;
import net.foxgenesis.customjail.jail.event.impl.MemberJailEvent;
import net.foxgenesis.customjail.jail.event.impl.MemberUnjailEvent;
import net.foxgenesis.customjail.jail.event.impl.WarningAddedEvent;
import net.foxgenesis.customjail.jail.event.impl.WarningReasonUpdateEvent;
import net.foxgenesis.customjail.jail.event.impl.WarningRemovedEvent;
import net.foxgenesis.customjail.time.CustomTime;
import net.foxgenesis.customjail.util.Response;
import net.foxgenesis.database.IDatabaseManager;
import net.foxgenesis.property.IProperty;
import net.foxgenesis.util.MethodTimer;
import net.foxgenesis.util.StringUtils;
import net.foxgenesis.watame.WatameBot;
import net.foxgenesis.watame.plugin.IEventStore;
import net.foxgenesis.watame.plugin.Plugin;
import net.foxgenesis.watame.plugin.PluginConfiguration;
import net.foxgenesis.watame.plugin.SeverePluginException;
import net.foxgenesis.watame.property.IGuildPropertyMapping;
import net.foxgenesis.watame.property.IGuildPropertyProvider;
import net.foxgenesis.watame.util.Colors;

@PluginConfiguration(defaultFile = "/META-INF/configuration/jail.ini", identifier = "jail", outputFile = "jail.ini")
public class CustomJailPlugin extends Plugin {

	/**
	 * Icon to be used on all embed footers
	 */
	public static String EMBED_FOOTER_ICON = "https://icons-for-free.com/iconfiles/png/512/jail+justice+law+police+prison+security+icon-1320190820835732524.png";
	
	/**
	 * Time selection for jailing
	 */
	public static final String[][] jailingTimes = { { "30 Min", "30m" }, { "1 Hour", "1h" }, { "5 Hours", "5h" },
			{ "12 Hours", "12h" }, { "1 Day", "1D" }, { "2 Days", "2D" }, { "3 Days", "3D" }, { "1 Week", "1W" },
			{ "2 Weeks", "2W" }, { "1 Month", "1M" } };

	/**
	 * Muted role
	 */
	private static final IProperty<String, Guild, IGuildPropertyMapping> timeoutRole;

	/**
	 * Jail channel
	 */
	private static final IProperty<String, Guild, IGuildPropertyMapping> timeoutChannel;

	/**
	 * Logging channel for jails
	 */
	private static final IProperty<String, Guild, IGuildPropertyMapping> logChannel;

	/**
	 * Warning duration
	 */
	private static final IProperty<String, Guild, IGuildPropertyMapping> warningTime;

	/**
	 * Max amount of warnings in a guild
	 */
	private static final IProperty<String, Guild, IGuildPropertyMapping> maxWarnings;

	/**
	 * Warning role prefix
	 */
	private static final IProperty<String, Guild, IGuildPropertyMapping> warningPrefix;

	static {
		IGuildPropertyProvider provider = WatameBot.INSTANCE.getPropertyProvider();
		timeoutRole = provider.getProperty("jail_role");
		timeoutChannel = provider.getProperty("jail_channel");
		logChannel = provider.getProperty("jail_log_channel");
		warningTime = provider.getProperty("jail_warning_time");
		maxWarnings = provider.getProperty("jail_max_warnings");
		warningPrefix = provider.getProperty("jail_warning_prefix");
	}

	// =================================================================================================================

	private volatile boolean updateRolesToDatabase;

	private final WarningDatabase database = new WarningDatabase();
	private JailSystem jail;

	@Override
	protected void onPropertiesLoaded(Properties properties) {}

	@Override
	protected void onConfigurationLoaded(String identifier, Configuration properties) {
		switch (identifier) {
			case "jail" -> {
				this.updateRolesToDatabase = properties.getBoolean("updateRolesToDatabase", false);

				if (this.updateRolesToDatabase)
					logger.warn(
							"*** updateRolesToDatabase is set to TRUE. Updating will start once program reaches ready state! ***");
			}
		}
	}

	@Override
	protected void preInit() {
		try {
			IDatabaseManager manager = WatameBot.INSTANCE.getDatabaseManager();
			manager.register(this, database);

			jail = new JailSystem(database);
		} catch (IOException e) {
			throw new SeverePluginException("Failed to register warning database", e, true);
		} catch (SchedulerException e) {
			throw new SeverePluginException("Failed to create scheduler", e, true);
		}
	}

	@Override
	protected void init(IEventStore builder) {
		// User interaction handler
		builder.registerListeners(this, new JailFrontend(jail));

		// Configuration command
		builder.registerListeners(this, new ListenerAdapter() {
			@Override
			public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
				switch (event.getFullCommandName()) {
					case "customjail configure" -> {
						event.deferReply(true).queue();
						InteractionHook hook = event.getHook();

						// Check for empty
						if (event.getOptions().isEmpty()) {
							hook.editOriginalEmbeds(Response.error("Please specify at least one option.")).queue();
							return;
						}

						// Get guild and self
						Guild guild = event.getGuild();
						Member self = guild.getSelfMember();

						// Get options
						Role role = event.getOption("jail_role", OptionMapping::getAsRole);
						GuildChannelUnion channel = event.getOption("jail_channel", OptionMapping::getAsChannel);
						GuildChannelUnion logChannel = event.getOption("log_channel", OptionMapping::getAsChannel);
						Integer months = event.getOption("warning_time", OptionMapping::getAsInt);
						Integer maxWarnings = event.getOption("max_warnings", OptionMapping::getAsInt);
						String prefix = event.getOption("warning_role_prefix", OptionMapping::getAsString);

						// Get warning roles
						List<Role> warningRoles = guild.getRoles().stream()
								.filter(r -> r.getName().startsWith(prefix != null ? prefix : "Warning"))
								.sorted(Comparator.reverseOrder()).limit(maxWarnings != null ? maxWarnings : 3)
								.toList();

						// Validate options
						if (warningRoles.isEmpty()) {
							hook.editOriginalEmbeds(Response.error("Unable to find any warning roles with prefix \""
									+ (prefix != null ? prefix : "Warning") + "\"")).queue();
							return;
						}

						// Check if we are able to assign roles
						StringBuilder roleBuilder = new StringBuilder();
						for (Role r : warningRoles)
							if (self.canInteract(r))
								roleBuilder.append("* \u2705 Able to assign " + r.getAsMention() + "\n");
							else {
								roleBuilder.append("* \u274C **NOT** able to assign " + r.getAsMention() + "!");
								hook.editOriginalEmbeds(
										Response.error("Role Assignment", roleBuilder.toString().strip())).queue();
								return;
							}

						// Validate options
						if (!(role == null || self.canInteract(role))) {
							hook.editOriginalEmbeds(Response.error("Unable to assign " + role.getAsMention() + "!"))
									.queue();
							return;
						}

						// Validate jail channel
						if (channel != null && !isValidChannel(hook, channel))
							return;

						// Validate logging channel
						if (logChannel != null && !isValidChannel(hook, logChannel))
							return;

						// Set and build output
						StringBuilder builder = new StringBuilder();
						if (role != null && timeoutRole.set(guild, role.getIdLong(), true))
							builder.append("* **Jail Role** -> " + role.getAsMention() + "\n");

						if (channel != null && timeoutChannel.set(guild, channel.getIdLong(), true))
							builder.append("* **Jail Channel** -> " + channel.getAsMention() + "\n");

						if (logChannel != null && CustomJailPlugin.logChannel.set(guild, logChannel.getIdLong(), true))
							builder.append("* **Log Channel** -> " + logChannel.getAsMention() + "\n");

						if (months != null && warningTime.set(guild, months, true))
							builder.append("* **Warning Time** -> " + months + " Month(s)\n");

						if (prefix != null && !prefix.isBlank() && warningPrefix.set(guild, prefix, true))
							builder.append("* **Warning Role Prefix** -> " + prefix + "\n");

						if (maxWarnings != null && CustomJailPlugin.maxWarnings.set(guild, maxWarnings, true))
							builder.append("* **Max Warnings** -> " + maxWarnings + "\n");

						hook.editOriginalEmbeds(Response.success("Updated Configuration", builder.toString().strip()),
								Response.success("Role Assignment", roleBuilder.toString().strip())).queue();
					}
				}
			}
		});

		// Moderation logging
		jail.getEventManager().addListener(new JailEventAdapter() {

			@Override
			public void onMemberJail(MemberJailEvent event) {
				Member member = event.getMember();
				Optional<Member> mod = event.getModerator().or(() -> Optional.of(event.getGuild().getSelfMember()));

				modlog(event.getGuild(), () -> {
					EmbedBuilder builder = new EmbedBuilder();
					builder.setColor(Colors.ERROR);
					builder.setTitle("Member Jailed");
					builder.setThumbnail(member.getEffectiveAvatarUrl());

					// Row 1
					builder.addField("Member", member.getAsMention(), true);
					builder.addField("Moderator", mod.map(Member::getAsMention).get(), true);
					builder.addField("Case ID", event.getCaseID().map(Object::toString).orElse("N/A"), true);

					// Row 2
					builder.addField("Duration", event.getDuration().getDisplayString(), true);

					// Row 3
					builder.addField("Reason", event.getReason().orElseGet(jail::getDefaultReason), false);

					builder.setTimestamp(Instant.now());
					builder.setFooter(jail.getEmbedFooter(), EMBED_FOOTER_ICON);
					return builder.build();
				}).ifPresent(RestAction::queue);
			}

			@Override
			public void onMemberUnjail(MemberUnjailEvent event) {
				Member member = event.getMember();

				modlog(event.getGuild(), () -> {
					EmbedBuilder builder = new EmbedBuilder();
					builder.setColor(Colors.SUCCESS);
					builder.setTitle("Member Unjailed");
					builder.setThumbnail(member.getEffectiveAvatarUrl());

					// Row 1
					builder.addField("User", member.getAsMention(), true);
					event.getModerator().ifPresent(mod -> builder.addField("Moderator", mod.getAsMention(), true));

					// Row 2
					builder.addField("Reason", event.getReason().orElseGet(jail::getDefaultReason), false);

					builder.setTimestamp(Instant.now());
					builder.setFooter(jail.getEmbedFooter(), EMBED_FOOTER_ICON);
					return builder.build();
				}).ifPresent(RestAction::queue);
			}

			@Override
			public void onWarningAdded(WarningAddedEvent event) {
				event.getModerator().ifPresent(moderator -> {
					Member member = event.getDetails().member();

					modlog(member.getGuild(), () -> {
						EmbedBuilder builder = new EmbedBuilder();
						builder.setColor(Colors.NOTICE);
						builder.setTitle("Warning Added");
						builder.setThumbnail(member.getEffectiveAvatarUrl());

						// Row 1
						builder.addField("Member", member.getAsMention(), true);
						builder.addField("Moderator", moderator.getAsMention(), true);
						builder.addField("Case ID", "" + event.getDetails().caseID(), true);

						// Row 2
						builder.addField("Reason", event.getReason().orElseGet(jail::getDefaultReason), false);

						builder.setTimestamp(Instant.now());
						builder.setFooter(jail.getEmbedFooter(), EMBED_FOOTER_ICON);
						return builder.build();
					}).ifPresent(RestAction::queue);
				});
			}

			@Override
			public void onJailTimerStart(JailTimerStartEvent event) {
				event.getModerator().ifPresent(moderator -> {
					Member member = event.getMember();

					modlog(member.getGuild(), () -> {
						EmbedBuilder builder = new EmbedBuilder();
						builder.setColor(Colors.WARNING);
						builder.setTitle("Started Jail Timer");
						builder.setThumbnail(member.getEffectiveAvatarUrl());

						// Row 1
						builder.addField("Member", member.getAsMention(), true);
						builder.addField("Moderator", moderator.getAsMention(), true);

						// Row 2
						builder.addField("Reason", event.getStartReason().orElseGet(jail::getDefaultReason), false);

						builder.setTimestamp(Instant.now());
						builder.setFooter(jail.getEmbedFooter(), EMBED_FOOTER_ICON);
						return builder.build();
					}).ifPresent(RestAction::queue);
				});
			}

			@Override
			public void onWarningRemove(WarningRemovedEvent event) {
				if (event.getModerator().isPresent())
					modlog(event.getGuild(), () -> {
						EmbedBuilder builder = new EmbedBuilder();
						builder.setColor(Colors.NOTICE);
						builder.setTitle("Warning Removed");

						// Row 1
						builder.addField("Case ID", event.getDetails().caseID() + "", true);
						event.getModerator().ifPresent(mod -> builder.addField("Moderator", mod.getAsMention(), true));

						// Row 2
						builder.addField("Reason", event.getReason().orElseGet(jail::getDefaultReason), false);

						builder.setTimestamp(Instant.now());
						builder.setFooter(jail.getEmbedFooter(), EMBED_FOOTER_ICON);
						return builder.build();
					}).ifPresent(RestAction::queue);
			}

			@Override
			public void onWarningReasonUpdated(WarningReasonUpdateEvent event) {
				modlog(event.getGuild(), () -> {
					EmbedBuilder builder = new EmbedBuilder();
					builder.setColor(Colors.NOTICE);
					builder.setTitle("Warning Reason Updated");

					// Row 1
					builder.addField("case-id", event.getDetails().caseID() + "", true);
					builder.addBlankField(true);
					builder.addField("Moderator", event.getModerator().map(Member::getAsMention).get(), true);

					// Row 2
					builder.addField("Old Reason", event.getOldReason().orElseGet(jail::getDefaultReason), true);
					builder.addBlankField(true);
					builder.addField("New Reason", event.getNewReason().orElseGet(jail::getDefaultReason), true);

					// Row 3
					builder.addField("Reason for Update", event.getReason().orElseGet(jail::getDefaultReason), false);

					builder.setTimestamp(Instant.now());
					builder.setFooter(jail.getEmbedFooter(), EMBED_FOOTER_ICON);
					return builder.build();
				}).ifPresent(RestAction::queue);
			}

		});
	}

	@Override
	protected void postInit(WatameBot bot) {
		try {
			jail.start();
		} catch (Exception e) {
			throw new SeverePluginException("Failed to start jailer", e, true);
		}
	}

	@Override
	protected void onReady(WatameBot bot) {
		if (this.updateRolesToDatabase) {
			logger.warn("Updating roles to database...");
			long start = System.nanoTime();
			bot.getJDA().getGuildCache().forEach(jail::updateRolesToDatabase);
			long end = System.nanoTime();
			logger.warn("Finished updating roles to database in {} seconds",
					MethodTimer.formatToSeconds(end - start, 2));
		}
	}

	@Override
	protected void close() throws Exception {
		if (jail != null)
			jail.close();
	}

	@Override
	public Collection<CommandData> getCommands() {
		DefaultMemberPermissions perm = DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS);
		return Set.of(
				// ====== Configuration Commands ======
				Commands.slash("customjail", "Commands relating to the CustomJail plugin").setGuildOnly(true)
						.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
						.addSubcommands(new SubcommandData("configure", "Configure CustomJail")
								.addOption(OptionType.ROLE, "jail_role", "Role to give when a user is jailed")
								.addOptions(
										new OptionData(OptionType.CHANNEL, "jail_channel", "Jail channel")
												.setChannelTypes(ChannelType.TEXT, ChannelType.GUILD_PRIVATE_THREAD,
														ChannelType.GUILD_PUBLIC_THREAD),
										new OptionData(OptionType.CHANNEL, "log_channel", "Channel to log jailings")
												.setChannelTypes(ChannelType.TEXT, ChannelType.GUILD_PRIVATE_THREAD,
														ChannelType.GUILD_PUBLIC_THREAD),
										new OptionData(OptionType.INTEGER, "warning_time",
												"Amount of time (in months) warnings should last").setMinValue(1)
												.setMaxValue(12),
										new OptionData(OptionType.STRING, "warning_role_prefix",
												"Prefix for warning roles"),
										new OptionData(OptionType.INTEGER, "max_warnings",
												"Max amount of warnings someone can have").setMinValue(0)
												.setMaxValue(99))),
				// ====== Jail Commands ======

				// Jail
				Commands.user("Jail User").setGuildOnly(true).setDefaultPermissions(perm),
				Commands.slash("jail", "Jail a user").setGuildOnly(true).setDefaultPermissions(perm)
						.addOption(OptionType.USER, "user", "User to jail", true)
						.addOptions(new OptionData(OptionType.STRING, "duration", "Amount of time to jail user", true)
								.addChoices(Arrays.stream(jailingTimes).map(arr -> new Command.Choice(arr[0], arr[1]))
										.toList()))
						.addOption(OptionType.STRING, "reason", "Reason for jail")
						.addOption(OptionType.BOOLEAN, "add-warning", "Should this jail result in a warning"),

				// Un-jail
				Commands.slash("unjail", "Unjail a user").setGuildOnly(true).setDefaultPermissions(perm)
						.addOption(OptionType.USER, "user", "User to unjail", true)
						.addOption(OptionType.STRING, "reason", "Reason for unjail"),

				// Force jail timer
				Commands.slash("forcestart", "Force start someone's jail timer").setGuildOnly(true)
						.setDefaultPermissions(perm).addOption(OptionType.USER, "user", "User to start timer for", true)
						.addOption(OptionType.STRING, "reason", "Reason for warning update"),

				// Jail Details
				Commands.user("Jail Details").setGuildOnly(true).setDefaultPermissions(perm),

				// ====== Warning Commands ======
				Commands.user("Warnings").setGuildOnly(true).setDefaultPermissions(perm),
				Commands.slash("warnings", "Commands that involve warnings").setGuildOnly(true)
						.setDefaultPermissions(perm).addSubcommands(
								// Decrease warning level
								new SubcommandData("decrease", "Decrease a users warning level")
										.addOption(OptionType.USER, "user", "User to decrease warning level for", true)
										.addOption(OptionType.STRING, "reason", "Reason for decreasing warning level"),

								// Add warning
								// new SubcommandData("add", "Add a warning")
								// .addOption(OptionType.USER, "user", "User to add warning to", true)
								// .addOption(OptionType.STRING, "reason", "Reason for the warning"),

								// Get warnings
								// new SubcommandData("get", "Get warnings for user").addOption(OptionType.USER,
								// "user",
								// "User to get warning from", true),

								// Remove warning
								new SubcommandData("remove", "Remove a warning")
										.addOptions(new OptionData(OptionType.INTEGER, "case-id", "Warning id", true)
												.setMinValue(0).setMaxValue(Integer.MAX_VALUE))
										.addOption(OptionType.STRING, "reason", "Reason for warning removal"),

								// Update warning
								new SubcommandData("update", "Update a warning")
										.addOption(OptionType.INTEGER, "case-id", "Warning id", true)
										.addOption(OptionType.STRING, "new-reason", "New warning reason", true)
										.addOption(OptionType.STRING, "reason", "Reason for warning update")));
	}

	public IJailSystem getJailSystem() {
		return jail;
	}

	// =================================================================================================================

	public static RestAction<?> modlog(@NotNull RestAction<?> action, @NotNull Guild guild,
			@NotNull Supplier<MessageEmbed> embed) {
		Optional<RestAction<?>> action2 = modlog(guild, embed);
		return action2.isPresent() ? action2.map(a -> a.and(action)).get() : action;
	}

	public static Optional<RestAction<?>> modlog(@NotNull Guild guild, @NotNull Supplier<MessageEmbed> embed) {
		GuildMessageChannel channel = CustomJailPlugin.logChannel
				.get(guild,
						() -> WatameBot.INSTANCE.getGuildLoggingChannel().get(guild,
								IGuildPropertyMapping::getAsMessageChannel),
						IGuildPropertyMapping::getAsMessageChannel);
		return Optional.ofNullable(channel).map(c -> c.sendMessageEmbeds(embed.get()).addCheck(channel::canTalk)
				.addCheck(() -> guild.getSelfMember().hasPermission(Permission.MESSAGE_EMBED_LINKS)));
	}

	/**
	 * Display an error to the user.
	 * 
	 * @param hook - {@link InteractionHook} to display the error on
	 * @param err  - error to display
	 * 
	 * @return Returns a {@link RestAction} that will display an error message to
	 *         the user
	 */
	public static WebhookMessageEditAction<Message> displayError(@NotNull InteractionHook hook,
			@NotNull Throwable err) {
		return hook.editOriginalEmbeds(Response.error("An error has occured! Please report this to the developers.",
				"```" + StringUtils.limit(ExceptionUtils.getStackTrace(err), 1970) + "```")).setReplace(true);
	}

	/**
	 * Check if a {@link GuildChannelUnion} is a valid channel that can be used.
	 * 
	 * @param hook    - hook to log errors to
	 * @param channel - channel to check
	 * 
	 * @return Returns {@code true} if the specified channel is <b>not</b>
	 *         {@code locked} or {@code archived} and<b> we have</b> permission to
	 *         {@code access}, {@code send embeds} and {@code send
	 *         messages}.
	 */
	private static boolean isValidChannel(@NotNull InteractionHook hook, @NotNull GuildChannelUnion channel) {
		Objects.requireNonNull(hook);
		Objects.requireNonNull(channel);

		Member self = channel.getGuild().getSelfMember();
		ChannelType type = channel.getType();
		boolean isThread = type == ChannelType.GUILD_PRIVATE_THREAD || type == ChannelType.GUILD_PUBLIC_THREAD;

		// Check permissions
		if (!(self.hasAccess(channel)
				&& self.hasPermission(channel, isThread ? Permission.MESSAGE_SEND_IN_THREADS : Permission.MESSAGE_SEND,
						Permission.MESSAGE_EMBED_LINKS))) {
			hook.editOriginalEmbeds(
					Response.error("Missing permissions [SEND MESSAGES, SEND EMBEDS and VIEW CHANNEL] in "
							+ channel.getAsMention() + "!"))
					.queue();
			return false;
		}

		// Validate thread
		if (isThread) {
			ThreadChannel thread = channel.asThreadChannel();

			// Fail if locked or archived
			if (thread.isLocked() || thread.isArchived()) {
				hook.editOriginalEmbeds(Response.error("Unable to join locked/archived thread!")).queue();
				return false;
			}

			// Join if not already
			if (!thread.isJoined())
				thread.join().queue();
		}

		// Valid channel
		return true;
	}

	@Nullable
	public static Role getTimeoutRole(@NotNull Guild guild) {
		return timeoutRole.get(guild, IGuildPropertyMapping::getAsRole);
	}

	@Nullable
	public static GuildMessageChannel getJailChannel(@NotNull Guild guild) {
		return timeoutChannel.get(guild, IGuildPropertyMapping::getAsMessageChannel);
	}

	public static int getMaxWarnings(@NotNull Guild guild) {
		return maxWarnings.get(guild, 3, IGuildPropertyMapping::getAsInt);
	}

	@NotNull
	public static String getWarningPrefix(@NotNull Guild guild) {
		return warningPrefix.get(guild, "Warning", IGuildPropertyMapping::getAsString);
	}

	@NotNull
	public static CustomTime getWarningTime(@NotNull Guild guild) {
		return new CustomTime(warningTime.get(guild, 1, IGuildPropertyMapping::getAsInt) + "M");
	}

	// =================================================================================================================
	@NotNull
	public static List<Role> getWarningRoles(@NotNull Guild guild) {
		String prefix = getWarningPrefix(guild);
		return guild.getRoles().stream().filter(r -> r.getName().startsWith(prefix)).sorted(Comparator.reverseOrder())
				.toList();
	}

	public static int getWarningLevelFromRoles(@NotNull Member member) {
		String prefix = getWarningPrefix(member.getGuild());
		return member.getRoles().stream().filter(role -> role.getName().startsWith(prefix)).toList().size();
	}

	@Nullable
	public static Role getRoleForWarningLevel(@NotNull Guild guild, int level) {
		if (level <= 0)
			return null;

		List<Role> roles = getWarningRoles(guild);
		return level > roles.size() ? null : roles.get(level - 1);
	}
}

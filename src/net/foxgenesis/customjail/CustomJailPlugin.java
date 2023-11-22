package net.foxgenesis.customjail;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import net.foxgenesis.customjail.database.WarningDatabase;
import net.foxgenesis.customjail.jail.IJailSystem;
import net.foxgenesis.customjail.jail.JailSystem;
import net.foxgenesis.customjail.jail.WarningDetails;
import net.foxgenesis.customjail.jail.event.JailEventAdapter;
import net.foxgenesis.customjail.jail.event.impl.JailTimerStartEvent;
import net.foxgenesis.customjail.jail.event.impl.MemberJailEvent;
import net.foxgenesis.customjail.jail.event.impl.MemberLeaveWhileJailedEvent;
import net.foxgenesis.customjail.jail.event.impl.MemberUnjailEvent;
import net.foxgenesis.customjail.jail.event.impl.WarningAddedEvent;
import net.foxgenesis.customjail.jail.event.impl.WarningReasonUpdateEvent;
import net.foxgenesis.customjail.jail.event.impl.WarningRemovedEvent;
import net.foxgenesis.customjail.time.CustomTime;
import net.foxgenesis.customjail.time.UnixTimestamp;
import net.foxgenesis.customjail.util.Response;
import net.foxgenesis.property.PropertyMapping;
import net.foxgenesis.property.PropertyType;
import net.foxgenesis.util.MethodTimer;
import net.foxgenesis.util.StringUtils;
import net.foxgenesis.util.resource.ConfigType;
import net.foxgenesis.watame.WatameBot;
import net.foxgenesis.watame.plugin.IEventStore;
import net.foxgenesis.watame.plugin.Plugin;
import net.foxgenesis.watame.plugin.SeverePluginException;
import net.foxgenesis.watame.plugin.require.CommandProvider;
import net.foxgenesis.watame.plugin.require.PluginConfiguration;
import net.foxgenesis.watame.property.PluginProperty;
import net.foxgenesis.watame.property.PluginPropertyMapping;
import net.foxgenesis.watame.util.Colors;
import net.foxgenesis.watame.util.DiscordUtils;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quartz.SchedulerException;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
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
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.utils.MarkdownUtil;

@PluginConfiguration(defaultFile = "/META-INF/configuration/jail.ini", identifier = "jail", outputFile = "jail.ini", type = ConfigType.INI)
public class CustomJailPlugin extends Plugin implements CommandProvider {

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
	private static PluginProperty timeoutRole;

	/**
	 * Jail channel
	 */
	private static PluginProperty timeoutChannel;

	/**
	 * Logging channel for jails
	 */
	private static PluginProperty logChannel;

	/**
	 * Warning duration
	 */
	private static PluginProperty warningTime;

	/**
	 * Max amount of warnings in a guild
	 */
	private static PluginProperty maxWarnings;

	/**
	 * Warning role prefix
	 */
	private static PluginProperty warningPrefix;

	/**
	 * Should members be banned if they leave while jailed
	 */
	private static PluginProperty banOnLeave;

	/**
	 * Should member's be notified via a DM
	 */
	private static PluginProperty notifyMember;

	// =================================================================================================================
	private final WarningDatabase database;
	private final SchedulerSettings settings;

	private final boolean updateRolesToDatabase;

	private JailSystem jail;

	public CustomJailPlugin() {
		super();
		// Load jail Settings
		if (hasConfiguration("jail")) {
			Configuration config = getConfiguration("jail");
			// [Scheduler] settings
			String name = config.getString("Scheduler.name", "JailScheduler");
			String id = config.getString("Scheduler.id", "NON_CLUSTERED");
			boolean update = config.getBoolean("Scheduler.skipUpdateCheck", true);
			String count = config.getString("Scheduler.threadCount", "1");
			this.settings = new SchedulerSettings(name, id, update, count);

			// [Miscellaneous] settings
			this.updateRolesToDatabase = config.getBoolean("Miscellaneous.updateRolesToDatabase", false);
		} else {
			this.settings = new SchedulerSettings();
			this.updateRolesToDatabase = false;
		}

		database = new WarningDatabase();
	}

	@Override
	protected void preInit() {
		if (this.updateRolesToDatabase)
			logger.warn(
					"*** updateRolesToDatabase is set to TRUE. Updating will start once program reaches ready state! ***");

		try {
			WatameBot.getDatabaseManager().register(this, database);
			jail = new JailSystem(database, settings);
		} catch (IOException e) {
			throw new SeverePluginException("Failed to register warning database", e, true);
		} catch (SchedulerException e) {
			throw new SeverePluginException("Failed to create scheduler", e, true);
		}
	}

	@Override
	protected void init(IEventStore builder) {
		timeoutRole = upsertProperty("jail_role", true, PropertyType.NUMBER);
		timeoutChannel = upsertProperty("jail_channel", true, PropertyType.NUMBER);
		logChannel = upsertProperty("jail_log_channel", true, PropertyType.NUMBER);
		warningTime = upsertProperty("jail_warning_time", true, PropertyType.NUMBER);
		maxWarnings = upsertProperty("jail_max_warnings", true, PropertyType.NUMBER);
		banOnLeave = upsertProperty("jail_ban_on_leave", true, PropertyType.NUMBER);
		warningPrefix = upsertProperty("jail_warning_prefix", true, PropertyType.PLAIN);
		notifyMember = upsertProperty("jail_notify_member", true, PropertyType.NUMBER);

		// User interaction handler
		builder.registerListeners(this, new JailFrontend(jail), jail);

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
						Boolean ban = event.getOption("ban_on_leave", OptionMapping::getAsBoolean);
						Boolean notify = event.getOption("notify_member", OptionMapping::getAsBoolean);

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
						if (channel != null && !isValidChannel(hook, channel)) {
							hook.editOriginalEmbeds(Response.error(channel.getAsMention()
									+ " is either locked/archived or bot is missing permissions to view channel and send embeds"))
									.queue();
							return;
						}

						// Validate logging channel
						if (logChannel != null && !isValidChannel(hook, logChannel)) {
							hook.editOriginalEmbeds(Response.error(logChannel.getAsMention()
									+ " is either locked/archived or bot is missing permissions to view channel and send embeds"))
									.queue();
							return;
						}

						// Check if we have ban permissions
						if (ban != null && ban && !self.hasPermission(Permission.BAN_MEMBERS)) {
							hook.editOriginalEmbeds(
									Response.error("Ban on leave is set to true but bot is missing ban permissions!"))
									.queue();
							return;
						}

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

						if (ban != null && banOnLeave.set(guild, ban, true))
							builder.append("* **Ban On Leave** -> " + ban + "\n");

						if (notify != null && notifyMember.set(guild, notify, true))
							builder.append("* **Notify Member** -> " + notify + "\n");

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
				Guild guild = event.getGuild();
				Member member = event.getMember();
				Optional<Member> mod = event.getModerator().or(() -> Optional.of(guild.getSelfMember()));

				// Send DM message to warned member
				notifyMember(member, "You have been jailed", Colors.ERROR, "Case ID",
						event.getCaseID().map(Object::toString).orElse("N/A"), "Reason",
						'*' + event.getReason().orElseGet(jail::getDefaultReason) + '*')
						.queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));

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

				// Disconnect member from voice chat
				Optional.ofNullable(member.getVoiceState()).filter(GuildVoiceState::inAudioChannel)
						.filter(v -> guild.getSelfMember().hasPermission(v.getChannel(), Permission.VOICE_MOVE_OTHERS))
						.ifPresent(vcState -> guild.kickVoiceMember(member).queue());
			}

			@Override
			public void onMemberUnjail(MemberUnjailEvent event) {
				Member member = event.getMember();

				// Send DM message to unjailed member
				notifyMember(member, "You have been unjailed", Colors.NOTICE, "Reason",
						'*' + event.getReason().orElseGet(jail::getDefaultReason) + '*')
						.queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));

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
					WarningDetails details = event.getDetails();
					Member member = details.member();

					// Send DM message to warned member
					notifyMember(details.member(), "You Have Been Warned", Colors.WARNING, "Case ID",
							"" + details.caseID(), "Reason",
							'*' + details.reason().orElseGet(jail::getDefaultReason) + '*')
							.queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));

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
			public void onWarningRemove(WarningRemovedEvent event) {
				if (event.getModerator().isPresent()) {
					Guild guild = event.getGuild();
					WarningDetails details = event.getDetails();

					// Send DM message to member
					notifyMember(details.member(), "Warning Removed", Colors.NOTICE, "Case ID", "" + details.caseID(),
							"Reason", '*' + event.getReason().orElseGet(jail::getDefaultReason) + '*')
							.queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));

					modlog(guild, () -> {
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

			@Override
			public void onJailTimerStart(JailTimerStartEvent event) {
				Guild guild = event.getGuild();
				Member member = event.getMember();
				Member mod = event.getModerator().orElseGet(guild::getSelfMember);

				String endTime = UnixTimestamp.fromEpochMilli(event.getEndDate().getTime())
						.getRelativeTimeStringInSeconds();
				String startReason = event.getStartReason().orElseGet(jail::getDefaultReason);

				// Send DM message to member
				notifyMember(member, "Started Jail Timer", Colors.NOTICE, "Case ID", "" + event.getDetails().caseid(),
						"End Date", endTime, "Reason", '*' + startReason + '*')
						.queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));

				modlog(member.getGuild(), () -> {
					EmbedBuilder builder = new EmbedBuilder();
					builder.setColor(Colors.WARNING);
					builder.setTitle("Started Jail Timer");
					builder.setThumbnail(member.getEffectiveAvatarUrl());

					// Row 1
					builder.addField("Member", member.getAsMention(), true);
					builder.addField("Moderator", mod.getAsMention(), true);
					builder.addField("End Date", endTime, true);

					// Row 2
					builder.addField("Reason", startReason, false);

					builder.setTimestamp(Instant.now());
					builder.setFooter(jail.getEmbedFooter(), EMBED_FOOTER_ICON);
					return builder.build();
				}).ifPresent(RestAction::queue);
			}

			@Override
			public void onMemberLeaveWhileJailed(MemberLeaveWhileJailedEvent event) {
				Guild guild = event.getGuild();
				Member member = event.getMember();

				modlog(guild, () -> {
					EmbedBuilder builder = new EmbedBuilder();
					builder.setColor(Colors.WARNING);
					builder.setTitle("Member left while jailed");
					builder.setThumbnail(member.getEffectiveAvatarUrl());

					// Row 1
					builder.addField("Member", member.getAsMention(), true);
					builder.addField("Warning Level", "" + jail.getWarningLevelForMember(member), true);

					builder.setTimestamp(Instant.now());
					builder.setFooter(jail.getEmbedFooter(), EMBED_FOOTER_ICON);

					return builder.build();
				}).ifPresent(RestAction::queue);

				// Check if member should be banned for leaving before punishment was over
				if (shouldBanOnLeave(guild)) {
					// Ban member
					guild.ban(member.getUser(), 0, TimeUnit.SECONDS).reason("Left while jailed")
							.addCheck(() -> guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)).queue(v ->
					// Log to moderation log
					modlog(guild,
							() -> Response.error(
									"Banned " + member.getAsMention() + " for leaving before punishment was over"))
							.ifPresent(RestAction::queue));
				}
			}

		});
	}

	@Override
	protected void postInit() {}

	@Override
	protected void onReady() {
		try {
			jail.start();
		} catch (Exception e) {
			throw new SeverePluginException("Failed to start jailer", e, true);
		}
		if (this.updateRolesToDatabase) {
			logger.warn("Updating roles to database...");
			long start = System.nanoTime();
			WatameBot.getJDA().getGuildCache().forEach(jail::refreshAllMembers);
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
								.addOption(OptionType.BOOLEAN, "ban_on_leave",
										"Should members be banned if they leave the server while jailed")
								.addOptions(
										new OptionData(OptionType.CHANNEL, "jail_channel", "Jail channel")
												.setChannelTypes(ChannelType.TEXT, ChannelType.GUILD_PRIVATE_THREAD,
														ChannelType.GUILD_PUBLIC_THREAD),
										new OptionData(OptionType.CHANNEL, "log_channel", "Channel to log jailings")
												.setChannelTypes(ChannelType.TEXT, ChannelType.GUILD_PRIVATE_THREAD,
														ChannelType.GUILD_PUBLIC_THREAD),
										new OptionData(OptionType.INTEGER, "warning_time",
												"Amount of time (in months) warnings should last")
												.setRequiredRange(1, 12),
										new OptionData(OptionType.STRING, "warning_role_prefix",
												"Prefix for warning roles"),
										new OptionData(OptionType.INTEGER, "max_warnings",
												"Max amount of warnings someone can have").setRequiredRange(0, 99))),
				// ====== Jail Commands ======

				// Jail
				Commands.user("Jail User").setGuildOnly(true).setDefaultPermissions(perm),
				Commands.slash("jail", "Jail a user").setGuildOnly(true).setDefaultPermissions(perm)
						.addOption(OptionType.USER, "user", "User to jail", true)
						.addOptions(new OptionData(OptionType.STRING, "duration", "Amount of time to jail user", true)
								.addChoices(Arrays.stream(jailingTimes).map(arr -> new Command.Choice(arr[0], arr[1]))
										.toList()))
						.addOptions(
								new OptionData(OptionType.STRING, "reason", "Reason for the jailing").setMaxLength(500))
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
				Commands.user("Warnings").setGuildOnly(true).setDefaultPermissions(perm), Commands
						.slash("warnings", "Commands that involve warnings").setGuildOnly(true).setDefaultPermissions(
								perm)
						.addSubcommands(
								// Decrease warning level
								new SubcommandData("decrease", "Decrease a users warning level")
										.addOption(OptionType.USER, "user", "User to decrease warning level for", true)
										.addOption(OptionType.STRING, "reason", "Reason for decreasing warning level"),

								// List warnings
								new SubcommandData("list", "List a user's warnings").addOption(OptionType.USER, "user",
										"User to get warnings for", true),

								// Add warning
								new SubcommandData("add", "Add a warning")
										.addOption(OptionType.USER, "user", "User to add warning to", true)
										.addOptions(
												new OptionData(OptionType.STRING, "reason", "Reason for the warning")
														.setMaxLength(500))
										.addOption(OptionType.BOOLEAN, "active",
												"Should this warning count to the member's warning level"),

								// Remove warning
								new SubcommandData("remove", "Remove a warning")
										.addOptions(new OptionData(OptionType.INTEGER, "case-id", "Warning id", true)
												.setMinValue(0).setMaxValue(Integer.MAX_VALUE))
										.addOption(OptionType.STRING, "reason", "Reason for warning removal"),

								// Clear member warnings
								new SubcommandData("clear", "Clear all warnings for a member")
										.addOption(OptionType.USER, "user", "User to add warning to", true)
										.addOption(OptionType.STRING, "reason", "Reason for the warning"),

								// Update warning
								new SubcommandData("update", "Update a warning")
										.addOption(OptionType.INTEGER, "case-id", "Warning id", true)
										.addOptions(new OptionData(OptionType.STRING, "new-reason",
												"New warning reason", true).setMaxLength(500))
										.addOption(OptionType.STRING, "reason", "Reason for warning update"),

								// Refresh
								new SubcommandData("reload", "Attempt to fix a member's warning level and timers")
										.addOption(OptionType.USER, "user", "User to refresh", true)));
	}

	public IJailSystem getJailSystem() {
		return jail;
	}

	// =================================================================================================================

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
	private RestAction<?> notifyMember(@NotNull Member member, @NotNull String title, int color,
			@NotNull Map<String, String> fields) {
		Objects.requireNonNull(member);
		Objects.requireNonNull(title);
		if (title.isBlank())
			throw new IllegalArgumentException("Title must not be empty!");

		// Send DM message to member
		return member.getUser().openPrivateChannel().flatMap(channel -> {
			Guild guild = member.getGuild();
			String field = "**%s: ** %s\n";

			// Build description
			StringBuilder b = new StringBuilder();
			b.append(field.formatted("In",
					MarkdownUtil.maskedLink(guild.getName(), DiscordUtils.getGuildProtocolLink(guild))));
			fields.forEach((key, value) -> b.append(field.formatted(key, value)));

			// Create embed
			EmbedBuilder builder = new EmbedBuilder();
			builder.setColor(color);
			builder.setTitle(title);
			builder.setThumbnail(guild.getIconUrl());
			builder.setDescription(b.toString().trim());
			builder.setTimestamp(Instant.now());
			builder.setFooter(jail.getEmbedFooter(), EMBED_FOOTER_ICON);

			// Send message
			return channel.sendMessageEmbeds(builder.build()).addCheck(channel::canTalk)
					.addCheck(() -> notifyMember.get(guild, () -> true, PropertyMapping::getAsBoolean));
		});
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
	private RestAction<?> notifyMember(@NotNull Member member, @NotNull String title, int color,
			@NotNull String... fields) {
		Objects.requireNonNull(fields);
		if (fields.length == 0)
			throw new IllegalArgumentException("Fields must not be empty!");
		if ((fields.length & 1) == 1)
			throw new IllegalArgumentException("Fields must be a multiple of two!");
		Map<String, String> map = new HashMap<>();
		for (int i = 0; i < fields.length; i += 2)
			map.put(fields[i], fields[i + 1]);
		return notifyMember(member, title, color, map);
	}

	/**
	 * Write a notification to the moderation log and combine it with another
	 * {@link RestAction}.
	 * <p>
	 * This is effectively equivalent to:
	 * </p>
	 * 
	 * <pre>
	 * Optional<RestAction<?>> action2 = modlog(guild, embed);
	 * return action2.isPresent() ? action2.map(action::and).get() : action;
	 * </pre>
	 * 
	 * @param action - action to combine
	 * @param guild  - {@link Guild} to send to
	 * @param embed  - embed containing the notification
	 * 
	 * @return If the result of {@link #modlog(Guild, Supplier)} is not empty,
	 *         returns the provided {@link RestAction} combined with another that
	 *         will write to the moderation log. Otherwise returns the provided
	 *         {@code action}
	 */
	@NotNull
	public static RestAction<?> modlog(@NotNull RestAction<?> action, @NotNull Guild guild,
			@NotNull Supplier<MessageEmbed> embed) {
		Optional<RestAction<?>> action2 = modlog(guild, embed);
		return action2.isPresent() ? action2.map(action::and).get() : action;
	}

	/**
	 * Write a notification to the moderation log.
	 * 
	 * @param guild - {@link Guild} to send to
	 * @param embed - embed containing the notification
	 * 
	 * @return Returns an {@link Optional} that may contain a {@link RestAction}
	 *         that will write the notification to the moderation log
	 */
	@NotNull
	public static Optional<RestAction<?>> modlog(@NotNull Guild guild, @NotNull Supplier<MessageEmbed> embed) {
		return CustomJailPlugin.logChannel.getOr(guild, WatameBot.getLoggingChannel())
				// As message channel
				.map(PluginPropertyMapping::getAsMessageChannel)
				// Where we have permission to talk
				.filter(GuildMessageChannel::canTalk)
				// Where we have permission to send embeds
				.filter(c -> guild.getSelfMember().hasPermission(c, Permission.MESSAGE_EMBED_LINKS))
				// Send embed
				.map(c -> c.sendMessageEmbeds(embed.get()));
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
	 * 
	 * @throws NullPointerException Thrown if {@code hook} or {@code channel} is
	 *                              {@code null}
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
		return timeoutRole.get(guild, PluginPropertyMapping::getAsRole);
	}

	@Nullable
	public static GuildMessageChannel getJailChannel(@NotNull Guild guild) {
		return timeoutChannel.get(guild, PluginPropertyMapping::getAsMessageChannel);
	}

	public static int getMaxWarnings(@NotNull Guild guild) {
		return maxWarnings.get(guild, () -> 3, PropertyMapping::getAsInt);
	}

	@NotNull
	public static String getWarningPrefix(@NotNull Guild guild) {
		return warningPrefix.get(guild, () -> "Warning", PropertyMapping::getAsString);
	}

	@NotNull
	public static CustomTime getWarningTime(@NotNull Guild guild) {
		return new CustomTime(warningTime.get(guild, () -> 1, PropertyMapping::getAsInt) + "M");
	}

	public static boolean shouldBanOnLeave(@NotNull Guild guild) {
		return banOnLeave.get(guild, () -> false, PropertyMapping::getAsBoolean);
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

package net.foxgenesis.customjail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.quartz.SchedulerException;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.RestAction;
import net.foxgenesis.customjail.database.WarningDatabase;
import net.foxgenesis.customjail.time.CustomTime;
import net.foxgenesis.customjail.util.Response;
import net.foxgenesis.database.IDatabaseManager;
import net.foxgenesis.property.IProperty;
import net.foxgenesis.util.StringUtils;
import net.foxgenesis.util.resource.ModuleResource;
import net.foxgenesis.watame.WatameBot;
import net.foxgenesis.watame.plugin.IEventStore;
import net.foxgenesis.watame.plugin.Plugin;
import net.foxgenesis.watame.plugin.SeverePluginException;
import net.foxgenesis.watame.property.IGuildPropertyMapping;
import net.foxgenesis.watame.property.IGuildPropertyProvider;

public class CustomJailPlugin extends Plugin {
	/**
	 * Date format with medium date and short time
	 */
	public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
			.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault());

	// ========================================================================================

	/**
	 * List of times
	 */
	public static final List<String[]> timeList = new ArrayList<>();

	/**
	 * Muted role
	 */
	static final IProperty<String, Guild, IGuildPropertyMapping> timeoutRole;

	/**
	 * Jail channel
	 */
	static final IProperty<String, Guild, IGuildPropertyMapping> timeoutChannel;

	/**
	 * Logging channel for jails
	 */
	static final IProperty<String, Guild, IGuildPropertyMapping> logChannel;

	/**
	 * Warning duration
	 */
	static final IProperty<String, Guild, IGuildPropertyMapping> warningTime;

	/**
	 * Max amount of warnings in a guild
	 */
	static final IProperty<String, Guild, IGuildPropertyMapping> maxWarnings;

	static {
		IGuildPropertyProvider provider = WatameBot.INSTANCE.getPropertyProvider();
		timeoutRole = provider.getProperty("jail_role");
		timeoutChannel = provider.getProperty("jail_channel");
		logChannel = provider.getProperty("jail_log_channel");
		warningTime = provider.getProperty("jail_warning_time");
		maxWarnings = provider.getProperty("jail_max_warnings");

		//timeList.add(new String[] { "1 Min", "1m" });
		timeList.add(new String[] { "30 Min", "30m" });
		timeList.add(new String[] { "1 Hour", "1h" });
		timeList.add(new String[] { "5 Hours", "5h" });
		timeList.add(new String[] { "12 Hours", "12h" });
		timeList.add(new String[] { "1 Day", "1D" });
		timeList.add(new String[] { "2 Days", "2D" });
		timeList.add(new String[] { "3 Days", "3D" });
		timeList.add(new String[] { "1 Week", "1W" });
		timeList.add(new String[] { "2 Weeks", "2W" });
		timeList.add(new String[] { "1 Month", "1M" });
	}

	@Nullable
	public static Role getTimeoutRole(Guild guild) {
		return timeoutRole.get(guild, () -> null, IGuildPropertyMapping::getAsRole);
	}

	@Nullable
	public static TextChannel getLoggingChannel(Guild guild) {
		return logChannel.get(guild, () -> null, IGuildPropertyMapping::getAsTextChannel);
	}

	public static int getMaxWarnings(Guild guild) {
		return maxWarnings.get(guild, 99, IGuildPropertyMapping::getAsInt);
	}

	@Nullable
	public static Role getRoleForWarningLevel(Guild guild, String prefix, int level) {
		List<Role> roles = guild.getRolesByName(prefix + " " + level, true);
		return roles.isEmpty() ? null : roles.get(0);
	}

	@Nonnull
	public static CustomTime getWarningTime(Guild guild) {
		return new CustomTime(warningTime.get(guild, 2, IGuildPropertyMapping::getAsInt) + "M");
	}

	@Nonnull
	public static TemporalAmount getTemporalOfString(@Nonnull String temporalString) throws DateTimeParseException {
		Objects.requireNonNull(temporalString);

		if (Character.isUpperCase(temporalString.charAt(temporalString.length() - 1)))
			return Period.parse("P" + temporalString);
		return Duration.parse("PT" + temporalString);
	}

	// =================================================================================================================

	private final WarningDatabase database = new WarningDatabase();
	private Jailer jail;

	@Override
	protected void onPropertiesLoaded(Properties properties) {}

	@Override
	protected void onConfigurationLoaded(String identifier, Configuration properties) {}

	@Override
	protected void preInit() {
		try {
			IDatabaseManager manager = WatameBot.INSTANCE.getDatabaseManager();
			manager.register(this, database);
		} catch (IOException e) {
			throw new SeverePluginException("Failed to register warning database", e, true);
		}

		try {
			logger.info("Creating jail scheduler");
			Properties prop = new ModuleResource("watamebot.customjail", "/META-INF/quartz.properties").asProperties();
			Properties prop2 = new Properties();
			try (InputStream in = Files.newInputStream(Path.of("config", "database.properties"),
					StandardOpenOption.READ)) {
				prop2.load(in);
			}
			prop.putAll(prop2);
			jail = new Jailer(prop, database);
		} catch (SchedulerException | IOException e) {
			throw new SeverePluginException("Failed to create scheduler", e, true);
		}
	}

	@Override
	protected void init(IEventStore builder) {
		builder.registerListeners(this, new JailUserHandle(jail), new UserWarningHandler(database));
		builder.registerListeners(this, new ListenerAdapter() {
			@Override
			public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
				switch (event.getCommandPath()) {
					case "jail" -> {
						event.deferReply(true).queue();
						jail.jail(event.getHook(), event.getOption("user", OptionMapping::getAsMember),
								event.getMember(),
								new CustomTime(event.getOption("duration", OptionMapping::getAsString)),
								event.getOption("reason", "No Reason Provided", OptionMapping::getAsString),
								event.getOption("add-warning", true, OptionMapping::getAsBoolean));
					}
					case "unjail" -> {
						event.deferReply(true).queue();
						jail.unjail(event.getHook(), event.getOption("user", OptionMapping::getAsMember),
								event.getMember(),
								event.getOption("reason", "No Reason Provided", OptionMapping::getAsString));
					}
					case "forcestart" -> {
						event.deferReply(true).queue();
						jail.startJailTime(event.getHook(), event.getOption("user", OptionMapping::getAsMember),
								event.getMember());
					}
					case "customjail/configure" -> {
						event.deferReply(true).queue();
						InteractionHook hook = event.getHook();

						// Check for empty
						if (event.getOptions().isEmpty()) {
							hook.editOriginal("Please specify at least one option.").queue();
							return;
						}

						// Get options
						Role role = event.getOption("jail_role", OptionMapping::getAsRole);
						GuildChannelUnion channel = event.getOption("jail_channel", OptionMapping::getAsChannel);
						GuildChannelUnion logChannel = event.getOption("log_channel", OptionMapping::getAsChannel);
						Integer months = event.getOption("warning_time", OptionMapping::getAsInt);

						// Get guild and self
						Guild guild = event.getGuild();
						Member self = guild.getSelfMember();

						// Validate options
						if (!(role == null || self.canInteract(role))) {
							hook.editOriginalEmbeds(Response.error("Unable to assign " + role.getAsMention() + "!"))
									.queue();
							return;
						}

						if (!(channel == null || self.hasAccess(channel) || self.hasPermission(channel,
								Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS))) {
							hook.editOriginalEmbeds(Response
									.error("Missing permissions [SEND MESSAGES, SEND EMBEDS and VIEW CHANNEL] in "
											+ channel.getAsMention() + "!"))
									.queue();
							return;
						}

						if (!(logChannel == null || self.hasAccess(logChannel) || self.hasPermission(logChannel,
								Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS))) {
							hook.editOriginalEmbeds(Response
									.error("Missing permissions [SEND MESSAGES, SEND EMBEDS and VIEW CHANNEL] in "
											+ logChannel.getAsMention() + "!"))
									.queue();
							return;
						}

						// Set and build output
						StringBuilder builder = new StringBuilder();
						if (role != null && timeoutRole.set(guild, role.getIdLong(), true))
							builder.append("Timeout Role = " + role.getAsMention() + "\n");

						if (channel != null && timeoutChannel.set(guild, channel.getIdLong(), true))
							builder.append("Jail Channel = " + channel.getAsMention() + "\n");

						if (logChannel != null && CustomJailPlugin.logChannel.set(guild, logChannel.getIdLong(), true))
							builder.append("Log Channel = " + logChannel.getAsMention() + "\n");

						if (months != null && warningTime.set(guild, months, true))
							builder.append("Warning Time = `" + months + " Month(s)` \n");

						hook.editOriginalEmbeds(Response.success(builder.toString())).queue();
					}
				}
			}

			@Override
			public void onButtonInteraction(ButtonInteractionEvent event) {
				switch (event.getComponentId()) {
					case "start-jail" -> {
						Member member = event.getMember();
						if (jail.isJailed(member)) {
							event.deferReply(true).queue();
							jail.startJailTime(event.getHook(), member);
						} else
							event.replyEmbeds(Response.error("You are not jailed!")).setEphemeral(true).queue();
					}
				}
			}
		});
	}

	@Override
	protected void postInit(WatameBot bot) {}

	@Override
	protected void onReady(WatameBot bot) {
		try {
			jail.start();
		} catch (Exception e) {
			throw new SeverePluginException("Failed to start jailer", e, true);
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
				Commands.slash("customjail", "Commands relating to the CustomJail plugin").setGuildOnly(true)
						.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
						.addSubcommands(new SubcommandData("configure", "Configure CustomJail")
								.addOption(OptionType.ROLE, "jail_role", "Role to give when a user is jailed")
								.addOptions(
										new OptionData(OptionType.CHANNEL, "jail_channel", "Jail channel")
												.setChannelTypes(ChannelType.TEXT),
										new OptionData(OptionType.CHANNEL, "log_channel", "Channel to log jailings")
												.setChannelTypes(ChannelType.TEXT),
										new OptionData(OptionType.INTEGER, "warning_time",
												"Amount of time (in months) warnings should last").setMinValue(1)
												.setMaxValue(12))),
				// ====== Jail Commands ======

				// Jail
				Commands.user("Jail User").setGuildOnly(true).setDefaultPermissions(perm),
				Commands.slash("jail", "Jail a user").setGuildOnly(true).setDefaultPermissions(perm)
						.addOption(OptionType.USER, "user", "User to jail", true)
						.addOptions(new OptionData(OptionType.STRING, "duration", "Amount of time to jail user", true)
								.addChoices(timeList.stream().map(arr -> new Command.Choice(arr[0], arr[1])).toList()))
						.addOption(OptionType.STRING, "reason", "Reason for jail")
						.addOption(OptionType.BOOLEAN, "add-warning", "Should this jail result in a warning"),

				// Un-jail
				Commands.slash("unjail", "Unjail a user").setGuildOnly(true).setDefaultPermissions(perm)
						.addOption(OptionType.USER, "user", "User to unjail", true)
						.addOption(OptionType.STRING, "reason", "Reason for unjail"),

				// Force jail timer
				Commands.slash("forcestart", "Force start someone's jail timer").setGuildOnly(true)
						.setDefaultPermissions(perm)
						.addOption(OptionType.USER, "user", "User to start timer for", true),

				// ====== Warning Commands ======
				Commands.user("Warnings").setGuildOnly(true).setDefaultPermissions(perm),
				Commands.slash("warnings", "Commands that involve warnings").setGuildOnly(true)
						.setDefaultPermissions(perm).addSubcommands(
								// Add warning
								//new SubcommandData("add", "Add a warning")
										//.addOption(OptionType.USER, "user", "User to add warning to", true)
										//.addOption(OptionType.STRING, "reason", "Reason for the warning"),

								// Get warnings
								//new SubcommandData("get", "Get warnings for user").addOption(OptionType.USER, "user",
										//"User to get warning from", true),

								// Remove warning
								new SubcommandData("remove", "Remove a warning")
										.addOption(OptionType.INTEGER, "case-id", "Warning id", true)
										.addOption(OptionType.STRING, "reason", "Reason for warning removal"),

								// Update warning
								new SubcommandData("update", "Update a warning")
										.addOption(OptionType.INTEGER, "case-id", "Warning id", true)
										.addOption(OptionType.STRING, "new-reason", "New warning reason", true)
										.addOption(OptionType.STRING, "reason", "Reason for warning update")));
	}

	// =================================================================================================================

	public static RestAction<?> modlog(RestAction<?> a, Guild guild, Supplier<MessageEmbed> embed) {
		TextChannel channel = CustomJailPlugin.logChannel.get(guild, () -> WatameBot.INSTANCE.getGuildLoggingChannel()
				.get(guild, () -> null, IGuildPropertyMapping::getAsTextChannel),
				IGuildPropertyMapping::getAsTextChannel);

		return channel == null ? a
				: a.and(channel.sendMessageEmbeds(embed.get()).addCheck(channel::canTalk)
						.addCheck(() -> guild.getSelfMember().hasPermission(Permission.MESSAGE_EMBED_LINKS)));
	}

	public static RestAction<?> modlog(Guild guild, Supplier<MessageEmbed> embed) {
		TextChannel channel = CustomJailPlugin.logChannel.get(guild, () -> WatameBot.INSTANCE.getGuildLoggingChannel()
				.get(guild, () -> null, IGuildPropertyMapping::getAsTextChannel),
				IGuildPropertyMapping::getAsTextChannel);

		return channel == null ? null
				: channel.sendMessageEmbeds(embed.get()).addCheck(channel::canTalk)
						.addCheck(() -> guild.getSelfMember().hasPermission(Permission.MESSAGE_EMBED_LINKS));
	}

	public static RestAction<?> displayError(InteractionHook hook, Throwable err) {
		return hook.editOriginalEmbeds(Response.error("An error has occured! Please report this to the developers.",
				"```" + StringUtils.limit(ExceptionUtils.getStackTrace(err), 1970) + "```")).setReplace(true);
	}

	public static void notReady(IReplyCallback event) {
		event.reply("Not yet implemented").setEphemeral(true).queue();
	}
}

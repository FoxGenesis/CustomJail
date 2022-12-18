package net.foxgenesis.customjail;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Channel;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.foxgenesis.config.fields.ChannelField;
import net.foxgenesis.config.fields.RoleField;
import net.foxgenesis.watame.WatameBot;
import net.foxgenesis.watame.WatameBot.ProtectedJDABuilder;
import net.foxgenesis.watame.plugin.IPlugin;
import net.foxgenesis.watame.plugin.PluginProperties;
import net.foxgenesis.watame.util.DiscordUtils;

@PluginProperties(name = "Custom Jailing", description = "A plugin to manage jailings", version = "0.0.1")
public class CustomJailPlugin implements IPlugin {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger("Custom Jail");

	private static final Button submitButton = Button.danger("jail-user", "Jail User")
			.withEmoji(Emoji.fromFormatted("U+1F6A8")).asDisabled(),
			reasonButton = Button.primary("add-reason", "Add Reason");

	private static final SelectMenu timeMenu = SelectMenu.create("time-selection").addOptions(getTimeOptions())
			.setPlaceholder("Set Time").build();

	private static final RoleField timeoutRole = new RoleField("jail.role",
			guild -> guild.getRolesByName("muted", true).stream().findAny().orElse(null), true);

	private static final ChannelField logChannel = new ChannelField("jail.log_channel",
			guild -> guild.getTextChannelsByName("modlog", true).stream().findAny().orElse(null), true);

	@Override
	public void preInit() {

	}

	@Override
	public void init(ProtectedJDABuilder builder) {

	}

	@Override
	public void postInit(WatameBot bot) {
		JDA jda = bot.getJDA();
		jda.upsertCommand(Commands.message("Jail user with message").setGuildOnly(true)
				.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)))
				.and(jda.upsertCommand(Commands.user("Jail user").setGuildOnly(true)
						.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))))
				.queue();

		jda.addEventListener(new ListenerAdapter() {
			@Override
			public void onUserContextInteraction(UserContextInteractionEvent event) {
				if (event.getName().equals("Jail user")) {
					event.replyEmbeds(createMessageEmbed(event.getGuild(), event.getTarget(), null))
							.addActionRow(timeMenu).addActionRow(reasonButton, submitButton.asDisabled())
							.setEphemeral(true).queue();
				}
			}

			@Override
			public void onMessageContextInteraction(MessageContextInteractionEvent event) {
				Message target = event.getTarget();

				if (event.getName().equals("Jail user with message")) {
					event.replyEmbeds(createMessageEmbed(event.getGuild(), target.getAuthor(), target))
							.addActionRow(timeMenu).addActionRow(reasonButton, submitButton.asDisabled())
							.setEphemeral(true).queue();
				}
			}

			@Override
			public void onSelectMenuInteraction(SelectMenuInteractionEvent event) {
				switch (event.getComponentId()) {
				case "time-selection" -> handleTimeSelection(event);
				}
			}

			@Override
			public void onButtonInteraction(ButtonInteractionEvent event) {
				switch (event.getComponentId()) {
				case "add-reason" -> showReasonModal(event);
				case "jail-user" -> handleSubmit(event);
				}
			}
		});
	}

	@Override
	public void onReady(WatameBot bot) {

	}

	@Override
	public void close() throws Exception {

	}

	private static void handleTimeSelection(SelectMenuInteractionEvent event) {
		event.deferEdit().queue();

		InteractionHook hook = event.getHook();
		Message message = event.getMessage();
		String values = event.getValues().stream().reduce((a, b) -> a + " " + b).orElse(null);

		if (values != null) {
			LocalDateTime futureDate = getFutureDateFromNow(values);
			MessageEmbed newEmbed = addTimeAndReason(message.getEmbeds().get(0), futureDate, null);

			hook.editOriginalComponents(ActionRow.of(timeMenu), ActionRow.of(reasonButton, submitButton.asEnabled()))
					.and(hook.editOriginalEmbeds(newEmbed)).queue();
		}
	}

	private static void showReasonModal(ButtonInteractionEvent event) { event.deferEdit().queue(); }

	private static void handleSubmit(ButtonInteractionEvent event) {
		event.deferEdit().queue();

		InteractionHook hook = event.getHook();
		Guild guild = event.getGuild();
		Role role = timeoutRole.optFrom(guild);
		Member botMember = DiscordUtils.getBotMember(guild);

		// Check if role is not null and we can interact with role
		if (role == null) {
			hook.editOriginal("Unable to find timeout role! Set `jail.role`.").setReplace(true).queue();
			return; // Fail early
		} else if (!botMember.canInteract(role)) {
			hook.editOriginal("Insufficient permissions to interact with " + role + "!").setReplace(true).queue();
			return; // Fail early
		}

		// Construct data
		MessageEmbed embed = event.getMessage().getEmbeds().get(0);
		Map<String, String> fields = embed.getFields().stream()
				.collect(Collectors.toMap(field -> field.getName(), field -> field.getValue()));

		Member member = guild.getMemberById(fields.get("User ID"));
		LocalDateTime time = LocalDateTime.parse(fields.get("Until"));
		String reason = fields.getOrDefault("Reason", "No Reason Provided");

		// Check if we can interact with member
		if (botMember.canInteract(member)) {
			jailUser(hook, guild, member, role, time, reason, event.getMessageChannel(), embed.getFooter().getText());
		} else {
			hook.editOriginal("Insufficient permissions to interact with user!").setReplace(true).queue();
		}
	}

	private static void jailUser(@Nonnull InteractionHook hook, @Nonnull Guild guild, @Nonnull Member member,
			@Nonnull Role role, @Nonnull LocalDateTime time, @Nullable String reason,
			@Nullable MessageChannel textChannel, @Nullable String messageId) {
		EmbedBuilder builder = new EmbedBuilder();

		// TODO check if user is already jailed
		guild.addRoleToMember(member, role).reason(reason)
				.and(hook.editOriginal("Jailed " + member.getAsMention() + " until " + time).setReplace(true)).queue();

		if (textChannel != null && messageId != null) {
			textChannel.deleteMessageById(messageId).reason(reason).queue();
			// Message message = textChannel.retrieveMessageById(messageId).;
		}

		Channel channel = logChannel.optFrom(guild);

		if (channel != null) {
			// TODO log jail to modlog
		}
		// TODO auto un-jail after duration
	}

	private static MessageEmbed createMessageEmbed(@Nonnull Guild guild, @Nonnull User user,
			@Nullable Message message) {
		Objects.requireNonNull(guild);
		Objects.requireNonNull(user);

		EmbedBuilder embedBuilder = new EmbedBuilder().setTitle("Timeout User");

		CompletableFuture<EmbedBuilder> addUserFuture = guild.retrieveMemberById(user.getId())
				.map(member -> embedBuilder
						.setAuthor(member.getEffectiveName(), message != null ? message.getJumpUrl() : null,
								user.getAvatarUrl())
						.setColor(member.getColor()).addField("User ID", member.getId(), true))
				.submit();

		if (message != null) {
			String content = message.getContentDisplay();
			embedBuilder.setDescription(content.substring(0, content.length() > 100 ? 100 : content.length()))
					.addField("Attachments", message.getAttachments().toString(), false).setFooter(message.getId());
		}
		return addUserFuture.join().build();
	}

	private static MessageEmbed addTimeAndReason(@Nonnull MessageEmbed original, @Nullable LocalDateTime time,
			@Nullable String reason) {
		Objects.requireNonNull(original);

		// Extract old fields except 'until' and 'reason'
		List<Field> fields = new ArrayList<>(original.getFields().stream().filter(
				field -> !(field.getName().equalsIgnoreCase("until") && field.getName().equalsIgnoreCase("reason")))
				.limit(3).toList());

		if (time != null)
			fields.add(new Field("Until", time.toString(), true));

		if (reason != null)
			fields.add(new Field("Reason", reason, true));

		EmbedBuilder builder = new EmbedBuilder(original).clearFields();
		for (Field field : fields)
			builder.addField(field);
		return builder.build();
	}

	private static LocalDateTime getFutureDateFromNow(@Nonnull String temporalString) {
		Objects.requireNonNull(temporalString);

		logger.trace("Getting temporal time of {}", temporalString);
		if (Character.isUpperCase(temporalString.charAt(temporalString.length() - 1)))
			return LocalDateTime.now().plus(Period.parse("P" + temporalString));
		return LocalDateTime.now().plus(Duration.parse("PT" + temporalString));

	}

	@Nonnull
	private static SelectOption[] getTimeOptions() {
		return new SelectOption[] { SelectOption.of("30 Min", "30m"), SelectOption.of("1 Hour", "1h"),
				SelectOption.of("5 Hours", "5h"), SelectOption.of("12 Hours", "12h"), SelectOption.of("1 Day", "1D"),
				SelectOption.of("2 Day", "2D"), SelectOption.of("3 Days", "3D"), SelectOption.of("1 Week", "1W"),
				SelectOption.of("2 Weeks", "2W"), SelectOption.of("1 Month", "1M") };
	}
}

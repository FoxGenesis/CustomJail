package net.foxgenesis.customjail;

import java.util.Arrays;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.foxgenesis.customjail.database.warning.Warning;
import net.foxgenesis.customjail.jail.JailDetails;
import net.foxgenesis.customjail.jail.JailSystem;
import net.foxgenesis.customjail.jail.exception.LocalizedException;
import net.foxgenesis.customjail.util.CustomTime;
import net.foxgenesis.customjail.util.Utilities;
import net.foxgenesis.watame.util.discord.Colors;
import net.foxgenesis.watame.util.discord.DiscordUtils;
import net.foxgenesis.watame.util.discord.InteractionListener;
import net.foxgenesis.watame.util.discord.Response;
import net.foxgenesis.watame.util.lang.DiscordLocaleMessageSource;
import net.foxgenesis.watame.util.lang.LocalizedEmbedBuilder;

public class JailFrontend extends ListenerAdapter {

	@Autowired
	private DiscordLocaleMessageSource messages;

	private final JailSystem jail;

	public JailFrontend(JailSystem system) {
		this.jail = Objects.requireNonNull(system);
	}

	@Override
	public void onUserContextInteraction(UserContextInteractionEvent event) {
		if (event.isFromGuild()) {
			switch (event.getFullCommandName()) {
			case "Jail User" -> {
				if (isValidUser(event, event.getTargetMember()))
					if (jail.isJailed(event.getTargetMember()))
						error(event, "customjail.alreadyJailed").queue();
					else
						new JailUserListener(event);

			}
			case "Jail Details" -> {
				Locale locale = event.getUserLocale().toLocale();
				Member member = event.getTargetMember();

				if (!jail.isJailed(member))
					error(event, "customjail.notJailed").queue();
				else if (isNonBotUser(event, member)) {
					Optional<String> jailEndTimestamp = jail.getJailEndTimestamp(member);
					boolean isTimerRunning = jailEndTimestamp.isPresent();

					// Create embed
					JailDetails details = jail.getJailDetails(member);

					LocalizedEmbedBuilder builder = new LocalizedEmbedBuilder(messages, locale);
					details.applyToEmbedBuilder(builder, messages, locale, member, jail.getJailEndTimestamp(member));

					MessageEmbed embed = builder.build();

					// Create actions
					ActionRow interactions = ActionRow.of(
							Button.danger(Utilities.Interactions.wrapInteraction("forcestart", member),
									messages.getMessage("customjail.embed.forcestart", locale))
									.withDisabled(isTimerRunning),
							Button.danger(Utilities.Interactions.wrapInteraction("unjail", member),
									messages.getMessage("customjail.embed.unjail", locale)));

					// Reply with embed and actions
					event.replyEmbeds(embed).setComponents(interactions).setEphemeral(true).queue();
				}
			}
			case "View Warnings" -> {
				if (isValidUser(event, event.getTargetMember())) {
					new WarningPage(event, jail, event.getTargetMember(), messages);
				}
			}
			}
		}
	}

	@Override
	public void onButtonInteraction(ButtonInteractionEvent event) {
		if (!event.isFromGuild())
			return;
		if (Utilities.Interactions.unwrapInteraction(event, (id, unwrappedMember, variant) -> {
			unwrappedMember.ifPresentOrElse(member -> {
				switch (id) {
				case "forcestart" -> {
					if (jail.isJailTimerRunning(member)) {
						error(event, "customjail.timer-already-started").queue();
						return;
					}
					// Display reason modal
					displayReasonModal(event, () -> member, "forcestart");
				}
				case "unjail" -> {
					if (!jail.isJailed(member)) {
						error(event, "customjail.notJailed").queue();
						return;
					}
					// Display reason modal
					displayReasonModal(event, () -> member, "unjail");
				}
				}
			},
					// Unable to find member button was wrapped to
					() -> {
						MessageEmbed errorMsg = Response.error(
								messages.getMessage("customjail.embed.no-target", event.getUserLocale().toLocale()));
						RestAction<?> edit = (event.isAcknowledged()
								? event.getHook().editOriginalEmbeds(errorMsg).setReplace(true)
								: event.replyEmbeds(errorMsg).setEphemeral(true));
						event.editButton(event.getButton().asDisabled()).flatMap(o -> edit).queue();
					});
		}))
			return;
	}

	@Override
	public void onModalInteraction(ModalInteractionEvent event) {
		// Only work with guild buttons
		if (!event.isFromGuild())
			return;

		if (Utilities.Interactions.unwrapInteraction(event, (id, unwrappedMember, variant) -> {
			unwrappedMember.ifPresentOrElse(member -> {
				switch (id) {
				case "addreason" -> {
					// Ensure the callback is valid
					if (variant.isEmpty()) {
						error(event, "watame.invalid-interaction").queue();
						return;
					}

					// Pass reason back
					String reason = event.getValue("reason").getAsString();

					// Switch based on callback
					switch (variant.get()) {
					case "forcestart" -> {
						if (isValidUser(event, member))
							if (!jail.isJailed(member)) {
								error(event, "customjail.notJailed").queue();
								return;
							}

						attemptAction(event, (hook, locale) -> {
							jail.startJailTimer(member, event.getMember(), reason);
							return Response.success(messages.getMessage("customjail.embed.timer-started",
									new Object[] { member.getAsMention(), jail.getJailEndTimestamp(member).get() },
									locale));
						}).queue();
					}
					case "unjail" -> {
						if (isValidUser(event, member))
							if (!jail.isJailed(member)) {
								error(event, "customjail.notJailed").queue();
								return;
							}

						attemptAction(event, (hook, locale) -> {
							jail.unjail(member, event.getMember(), reason);
							return Response.success(messages.getMessage("customjail.embed.unjailed",
									new Object[] { member.getAsMention() }, locale));
						}).queue();
					}
					}
				}
				}
			}, () -> error(event, "customjail.embed.no-target").queue());
		}))
			return;
	}

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.isFromGuild())
			return;
		try {
			switch (event.getName()) {
			case "warnings" -> handleWarningCommands(event);
			case "jail" -> {
				Member member = event.getOption("user", OptionMapping::getAsMember);
				CustomTime duration = event.getOption("duration", o -> new CustomTime(o.getAsString()));
				String reason = event.getOption("reason", null, OptionMapping::getAsString);
				boolean active = event.getOption("add-warning", true, OptionMapping::getAsBoolean);
				boolean anon = event.getOption("anonymous", true, OptionMapping::getAsBoolean);

				attemptAction(event, (hook, locale) -> {
					jail.jail(member, event.getMember(), duration, reason, active, anon);
					return Response.success(messages.getMessage("customjail.embed.jailed-user", new Object[] {
							member.getAsMention(), duration.getLocalizedDisplayString(messages, locale) }, locale));
				}).queue();
			}
			case "unjail" -> {
				Member member = event.getOption("user", OptionMapping::getAsMember);
				String reason = event.getOption("reason", null, OptionMapping::getAsString);

				if (isValidUser(event, member))
					if (!jail.isJailed(member))
						error(event, "customjail.notJailed").queue();

				attemptAction(event, (hook, locale) -> {
					jail.unjail(member, event.getMember(), reason);
					return Response.success(messages.getMessage("customjail.embed.unjailed",
							new Object[] { member.getAsMention() }, locale));
				}).queue();
			}
			case "forcestart" -> {
				Member member = event.getOption("user", OptionMapping::getAsMember);
				String reason = event.getOption("reason", null, OptionMapping::getAsString);

				if (isValidUser(event, member))
					if (!jail.isJailed(member))
						error(event, "customjail.notJailed").queue();

				attemptAction(event, (hook, locale) -> {
					jail.startJailTimer(member, event.getMember(), reason);
					return Response.success(messages.getMessage("customjail.embed.timer-started",
							new Object[] { member.getAsMention(), jail.getJailEndTimestamp(member).get() }, locale));
				}).queue();
			}
			}
		} catch (LocalizedException e) {
			MessageEmbed embed = e.getErrorEmbed(messages, event.getUserLocale().toLocale());

			if (event.isAcknowledged())
				event.getHook().editOriginalEmbeds(embed).setReplace(true).queue();
			else
				event.replyEmbeds(embed).setEphemeral(true).queue();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void handleWarningCommands(SlashCommandInteractionEvent event) {
		switch (event.getSubcommandName()) {
		// List warnings
		case "list" -> {
			Member member = event.getOption("user", OptionMapping::getAsMember);
			if (isNonBotUser(event, member))
				new WarningPage(event, jail, member, messages);
		}
		// Add warning
		case "add" -> {
			Member member = event.getOption("user", OptionMapping::getAsMember);
			String reason = event.getOption("reason", OptionMapping::getAsString);
			boolean active = event.getOption("active", true, OptionMapping::getAsBoolean);

			if (isValidUser(event, member)) {
				Warning w = jail.addWarning(member, event.getMember(), reason, active);

				success(event, "customjail.warning-added", member.getAsMention(), w.getId()).queue();
			}
		}
		// Remove warning
		case "remove" -> {
			long caseid = event.getOption("case-id", OptionMapping::getAsLong);
			String reason = event.getOption("reason", OptionMapping::getAsString);

			jail.deleteWarningById(caseid, event.getMember(), reason);
			success(event, "customjail.warning-removed", caseid).queue();
		}
		// Decrease warning level
		case "decrease" -> {
			Member member = event.getOption("user", OptionMapping::getAsMember);
			String reason = event.getOption("reason", OptionMapping::getAsString);

			int currentLevel = jail.getWarningLevel(member);
			if (currentLevel <= 0) {
				error(event, "customjail.no-active-warnings").queue();
				return;
			}

			jail.decreaseWarningLevel(member, member, reason);
			success(event, "customjail.warning-decreased", member.getAsMention(), currentLevel, currentLevel - 1)
					.queue();
		}
		// Update warning reason
		case "update" -> {
			long caseid = event.getOption("case-id", OptionMapping::getAsLong);
			String newReason = event.getOption("new-reason", OptionMapping::getAsString);
			String reason = event.getOption("reason", OptionMapping::getAsString);

			try {
				jail.updateWarningReason(caseid, event.getMember(), newReason, reason);
			} catch (NoSuchElementException e) {
				error(event, "customjail.no-warning", caseid).queue();
				return;
			}
			
			success(event, "customjail.embed.warning-updated").queue();
		}
		// Clear warnings
		case "clear" -> {
			Member member = event.getOption("user", OptionMapping::getAsMember);
			String reason = event.getOption("reason", OptionMapping::getAsString);

			if (isValidUser(event, member)) {
				jail.clearWarnings(member, event.getMember(), reason);
				success(event, "customjail.warnings-cleared", member.getAsMention()).queue();
			}
		}
		case "fix" -> {
			Member member = event.getOption("user", OptionMapping::getAsMember);
			if (isValidUser(event, member)) {
				jail.fixMember(member);
				success(event, "customjail.fixed", member.getAsMention()).queue();
			}
		}
		}
	}

	private boolean isNonBotUser(IReplyCallback event, Member target) {
		if (target == null) {
			error(event, "customjail.no-target").queue();
			return false;
		}

		User user = target.getUser();
		if (user.isBot() || user.isSystem()) {
			error(event, "customjail.bot-user").queue();
			return false;
		}

		return true;
	}

	private boolean isValidUser(IReplyCallback event, Member target) {
		if (!isNonBotUser(event, target))
			return false;

		Member member = event.getMember();
		if (!member.isOwner()) {
			if (target.getIdLong() == member.getIdLong()) {
				error(event, "customjail.self").queue();
				return false;
			}

			if (!member.canInteract(target)) {
				error(event, "customjail.no-interact").queue();
				return false;
			}
		}

		return true;
	}

	private ReplyCallbackAction success(IReplyCallback event, String code, Object... args) {
		return event.replyEmbeds(Response.success(messages.getMessage(code, args, code, event.getUserLocale())))
				.setEphemeral(true);
	}

	private RestAction<?> error(IReplyCallback event, String code, Object... args) {
		MessageEmbed embed = Response.error(messages.getMessage(code, args, code, event.getUserLocale()));
		return event.isAcknowledged() ? event.getHook().editOriginalEmbeds(embed).setReplace(true)
				: event.replyEmbeds(embed).setEphemeral(true);
	}

	private RestAction<?> attemptAction(IReplyCallback event,
			BiFunction<InteractionHook, Locale, MessageEmbed> attempt) {
		return event.deferReply(true).flatMap(hook -> {
			Locale locale = event.getUserLocale().toLocale();
			MessageEmbed embed = null;

			try {
				embed = attempt.apply(hook, locale);
			} catch (LocalizedException e) {
				embed = e.getErrorEmbed(messages, locale);
			} catch (Exception e) {
				e.printStackTrace();
				embed = Response.error(DiscordUtils.toString(e));
			}

			return hook.editOriginalEmbeds(
					embed != null ? embed : Response.error("Uknown Error. Please contact the developer"));
		});
	}

	private void displayReasonModal(GenericComponentInteractionCreateEvent event, Supplier<Member> wrappedMember,
			String callback) {
		Locale locale = event.getUserLocale().toLocale();

		Modal.Builder builder = Modal.create(
				Utilities.Interactions.wrapInteraction("addreason",
						wrappedMember != null ? wrappedMember.get() : event.getMember(), callback),
				messages.getMessage("customjail.modal.title", locale));

		TextInput body = TextInput
				.create("reason", messages.getMessage("customjail.embed.reason", locale), TextInputStyle.PARAGRAPH)
				.setPlaceholder(messages.getMessage("customjail.modal.placeholder", locale)).setMinLength(3)
				.setMaxLength(500).setRequired(false).build();

		builder.addActionRow(body);
		event.replyModal(builder.build()).queue();
	}

	private class JailUserListener extends InteractionListener {
		private CompletableFuture<Void> expirationFuture;

		private final Member member;
		private final int warningLevel;
		private final int warnings;

		private CustomTime time;

		public JailUserListener(UserContextInteractionEvent event) {
			super(event);
			this.member = event.getTargetMember();
			this.warningLevel = jail.getWarningLevel(member);
			this.warnings = jail.getTotalWarnings(member);

			ActionRow interactions = ActionRow.of(getAddWarningButton(true), addAnonButton(true),
					Button.danger("jailuser", messages.getMessage("customjail.embed.jail-user", locale)).asDisabled());

			InteractionHook hook = event.getHook();
			event.replyEmbeds(createJailEmbed())
					// Add time menu
					.addActionRow(getTimeMenu())
					// Add buttons
					.addComponents(interactions)
					// Set user only
					.setEphemeral(true)
					// Send
					.queue(v -> {
						hook.getJDA().addEventListener(this);
						expirationFuture = CompletableFuture.runAsync(() -> {
							hook.getJDA().removeEventListener(this);
							hook.editOriginalEmbeds(
									Response.error(messages.getMessage("watame.interaction.expired", null, locale)))
									.setReplace(true).queue();
						}, CompletableFuture.delayedExecutor(894, TimeUnit.SECONDS));
					});
		}

		@Override
		public void onButtonInteraction(ButtonInteractionEvent event) {
			if (!shouldRespond(event))
				return;
			switch (event.getButton().getId()) {
			case "with-warning" -> event.editButton(getAddWarningButton(false)).queue();
			case "without-warning" -> event.editButton(getAddWarningButton(true)).queue();
			case "anon" -> event.editButton(addAnonButton(false)).queue();
			case "non-anon" -> event.editButton(addAnonButton(true)).queue();
			case "jailuser" -> event.replyModal(createJailModal(member)).queue();
			}
		}

		@Override
		public void onStringSelectInteraction(StringSelectInteractionEvent event) {
			if (!shouldRespond(event))
				return;
			switch (event.getComponentId()) {
			case "time-selection" -> {
				time = event.getValues().stream().reduce((a, b) -> a + b).map(CustomTime::new).orElseThrow();

				Message message = event.getMessage();
				Button withWarning = message.getButtonById("with-warning"),
						withoutWarning = message.getButtonById("without-warning"), anon = message.getButtonById("anon"),
						nonAnon = message.getButtonById("non-anon"), jailButton = message.getButtonById("jailuser");

				event.editComponents(
						ActionRow.of(event.getSelectMenu().createCopy().setDefaultValues(event.getValues()).build()),
						ActionRow.of(withWarning != null ? withWarning : withoutWarning, anon != null ? anon : nonAnon,
								jailButton.asEnabled()))
						.queue();
			}
			}
		}

		@Override
		public void onModalInteraction(ModalInteractionEvent event) {
			Message.Interaction interactionContext = event.getMessage().getInteraction();
			if (interactionContext != null && interactionContext.getIdLong() == id) {
				Message message = event.getMessage();

				boolean withWarning = message.getButtonById("with-warning") != null;
				boolean anon = message.getButtonById("anon") != null;
				String reason = event.getValue("reason").getAsString();

				expirationFuture.cancel(true);
				event.getJDA().removeEventListener(this);

				event.deferEdit().flatMap(hook -> {
					Locale locale = event.getUserLocale().toLocale();

					MessageEmbed embed = null;
					try {
						jail.jail(member, event.getMember(), time, reason, withWarning, anon);

						embed = Response.success(messages.getMessage("customjail.embed.jailed-user", new Object[] {
								member.getAsMention(), time.getLocalizedDisplayString(messages, locale) }, locale));
					} catch (LocalizedException e) {
						embed = Response.error(messages.getMessage(e.getErrorMessage(), locale));
					} catch (Exception e) {
						embed = Response.error(DiscordUtils.toString(e));
					}

					return hook.editOriginalEmbeds(embed != null ? embed : Response.error("Uknown error while jailing"))
							.setReplace(true);
				}).queue();
			}
		}

		private MessageEmbed createJailEmbed() {
			LocalizedEmbedBuilder builder = new LocalizedEmbedBuilder(messages, locale);
			builder.setColor(Colors.INFO);
			builder.setLocalizedTitle("customjail.embed.jail-user");
			builder.setThumbnail(member.getEffectiveAvatarUrl());

			builder.addLocalizedField("customjail.embed.member", member.getAsMention(), true);
			builder.addLocalizedField("customjail.embed.warning-level", "" + warningLevel, true);
			builder.addLocalizedField("customjail.embed.total-warnings", "" + warnings, true);
			return builder.build();
		}

		private Button addAnonButton(boolean anon) {
			return anon
					? Button.primary("anon", messages.getMessage("customjail.embed.anon", locale))
							.withEmoji(Emoji.fromFormatted("U+1F92B"))
					: Button.secondary("non-anon", messages.getMessage("customjail.embed.non-anon", locale))
							.withEmoji(Emoji.fromFormatted("U+1F4E3"));
		}

		private Button getAddWarningButton(boolean addWarning) {
			return addWarning
					? Button.primary("with-warning", messages.getMessage("customjail.embed.with-warning", locale))
							.withEmoji(Emoji.fromFormatted("U+2705"))
					: Button.secondary("without-warning",
							messages.getMessage("customjail.embed.without-warning", locale))
							.withEmoji(Emoji.fromFormatted("U+274C"));
		}

		private SelectMenu getTimeMenu() {
			SelectOption[] options = Arrays
					// Stream times
					.stream(jail.getJailTimings())
					// Create select option
					.map(time -> SelectOption.of(new CustomTime(time).getLocalizedDisplayString(messages, locale),
							time))
					// To array
					.toArray(SelectOption[]::new);

			return StringSelectMenu
					// Set ID
					.create("time-selection")
					// Set placeholder
					.setPlaceholder(messages.getMessage("customjail.embed.set-time", locale))
					// Add time options
					.addOptions(options)
					// Build
					.build();
		}

		private Modal createJailModal(Member member) {
			Modal.Builder builder = Modal.create(Utilities.Interactions.wrapInteraction("jailuser", member),
					messages.getMessage("customjail.modal.title", locale));

			TextInput body = TextInput
					.create("reason", messages.getMessage("customjail.embed.reason", locale), TextInputStyle.PARAGRAPH)
					.setPlaceholder(messages.getMessage("customjail.modal.placeholder", locale)).setMinLength(3)
					.setMaxLength(500).setRequired(false).build();

			builder.addActionRow(body);
			return builder.build();
		}
	}
}

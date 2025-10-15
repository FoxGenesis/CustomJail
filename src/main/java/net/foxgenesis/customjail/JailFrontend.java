package net.foxgenesis.customjail;

import java.util.Arrays;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSourceResolvable;

import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.SelectMenu;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import net.foxgenesis.customjail.database.warning.Warning;
import net.foxgenesis.customjail.jail.JailDetails;
import net.foxgenesis.customjail.jail.JailSystem;
import net.foxgenesis.customjail.jail.exception.LocalizedException;
import net.foxgenesis.customjail.util.CustomTime;
import net.foxgenesis.customjail.util.Utilities;
import net.foxgenesis.watame.util.discord.DiscordUtils;
import net.foxgenesis.watame.util.discord.components.Response;
import net.foxgenesis.watame.util.lang.DiscordLocaleMessageSource;
import net.foxgenesis.watame.util.lang.LocalizedContainerBuilder;
import net.foxgenesis.watame.util.lang.LocalizedModalBuilder;

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
//						new JailUserListener(event);
						displayJailModal(event);

			}
			case "Jail Details" -> {
				Locale locale = event.getUserLocale().toLocale();
				Member member = event.getTargetMember();

				if (isValidUser(event, member))
					if (!jail.isJailed(member))
						error(event, "customjail.notJailed").queue();
					else if (isNonBotUser(event, member)) {
						// Create embed
						JailDetails details = jail.getJailDetails(member);

						LocalizedContainerBuilder cb = new LocalizedContainerBuilder(messages, locale);
						details.applyToContainerBuilder(cb, member, jail.getJailEndTimestamp(member));

						// Reply with embed and actions
						event.replyComponents(cb.build())
								// This is required any time you are using Components V2
								.useComponentsV2().setEphemeral(true).queue();
					}
			}
			case "View Warnings" -> {
				if (isValidUser(event, event.getTargetMember())) {
					// new WarningPage(event, jail, event.getTargetMember(), messages);
					new WarningPageContainer(event, jail, event.getTargetMember(), messages);
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
				// Callback from jail user modal
				case "jailuser" -> {
					if (isValidUser(event, member))
						if (jail.isJailed(member)) {
							error(event, "customjail.alreadyJailed").queue();
							return;
						}

					CustomTime duration = event.getValue("time-selection").getAsStringList().stream()
							.reduce((a, b) -> a + b).map(CustomTime::new).orElseThrow();
					boolean active = Boolean.valueOf(event.getValue("add-warning").getAsStringList().get(0));
					boolean anon = Boolean.valueOf(event.getValue("anon").getAsStringList().get(0));
					String reason = event.getValue("reason").getAsString();

					attemptAction(event, (hook, locale) -> {
						jail.jail(member, event.getMember(), duration, reason, active, anon);

						String response = messages.getMessage("customjail.embed.jailed-user", new Object[] {
								member.getAsMention(), duration.getLocalizedDisplayString(messages, locale) }, locale);
						return Response.success(response);
					}).queue();
				}
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

		TextInput body = TextInput.create("reason", TextInputStyle.PARAGRAPH)
				.setPlaceholder(messages.getMessage("customjail.modal.placeholder", locale)).setMinLength(3)
				.setMaxLength(500).setRequired(false).build();

		builder.addComponents(Label.of(messages.getMessage(CommonMessages.REASON, locale), body));
		event.replyModal(builder.build()).queue();
	}

	private void displayJailModal(UserContextInteractionEvent event) {
		Locale locale = event.getUserLocale().toLocale();
		Member target = event.getTargetMember();

		LocalizedModalBuilder builder = new LocalizedModalBuilder(messages, locale,
				Utilities.Interactions.wrapInteraction("jailuser", target, "jailuser"), "customjail.embed.jail-user");

		TextDisplay details = getJailModalDetails(target, locale);

		TextInput body = TextInput.create("reason", TextInputStyle.PARAGRAPH)
				.setPlaceholder(messages.getMessage("customjail.modal.placeholder", locale)).setMinLength(3)
				.setMaxLength(500).setRequired(false).build();

		SelectOption[] yesNo = new SelectOption[] {
				SelectOption.of(messages.getMessage(CommonMessages.YES, locale), Boolean.TRUE.toString()),
				SelectOption.of(messages.getMessage(CommonMessages.NO, locale), Boolean.FALSE.toString()) };
		SelectMenu addWarning = StringSelectMenu.create("add-warning").addOptions(yesNo).setDefaultOptions(yesNo[0])
				.setRequired(true).build();
		SelectMenu anon = StringSelectMenu.create("anon").addOptions(yesNo).setDefaultOptions(yesNo[0])
				.setRequired(true).build();

		SelectMenu timeMenu = getTimeMenu(locale);

		builder.addComponents(details);
		builder.addLocalizedLabel(CommonMessages.DURATION, timeMenu);
		builder.addLocalizedLabel(CommonMessages.WITH_WARNING, addWarning);
		builder.addLocalizedLabel(CommonMessages.ANONYMOUS, anon);

		builder.addLocalizedLabel(CommonMessages.REASON, body);

		event.replyModal(builder.build()).queue();
	}

	private TextDisplay getJailModalDetails(Member target, Locale locale) {
		int warningLevel = jail.getWarningLevel(target);
		int totalWarnings = jail.getTotalWarnings(target);

		StringBuilder builder = new StringBuilder();

		appendBoldField(builder, locale, CommonMessages.MEMBER, target.getAsMention());
		appendBoldField(builder, locale, CommonMessages.WARNING_LEVEL, MarkdownUtil.monospace(warningLevel + ""));
		appendBoldField(builder, locale, CommonMessages.TOTAL_WARNINGS, MarkdownUtil.monospace(totalWarnings + ""));

		return TextDisplay.of(builder.toString());
	}

	private void appendBoldField(StringBuilder builder, Locale locale, Object... args) {
		Object[] resolved = Arrays.copyOf(args, args.length);
		for (int i = 0; i < resolved.length; i++) {
			Object arg = resolved[i];
			if (arg instanceof MessageSourceResolvable resolvable)
				resolved[i] = messages.getMessage(resolvable, locale);
		}
		builder.append(String.format("**%s:** %s\n", args));
	}

	private SelectMenu getTimeMenu(Locale locale) {
		SelectOption[] options = Arrays
				// Stream times
				.stream(jail.getJailTimings())
				// Create select option
				.map(time -> SelectOption.of(new CustomTime(time).getLocalizedDisplayString(messages, locale), time))
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
}

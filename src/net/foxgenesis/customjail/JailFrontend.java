package net.foxgenesis.customjail;

import static net.foxgenesis.customjail.util.Utilities.Interactions.displayReasonModal;
import static net.foxgenesis.customjail.util.Utilities.Interactions.unwrapInteraction;
import static net.foxgenesis.customjail.util.Utilities.Interactions.validateMember;
import static net.foxgenesis.customjail.util.Utilities.Interactions.wrapInteraction;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.foxgenesis.customjail.embed.JailEmbed;
import net.foxgenesis.customjail.embed.WarningsEmbed;
import net.foxgenesis.customjail.jail.IJailSystem;
import net.foxgenesis.customjail.jail.InternalException;
import net.foxgenesis.customjail.jail.JailDetails;
import net.foxgenesis.customjail.time.CustomTime;
import net.foxgenesis.customjail.time.UnixTimestamp;
import net.foxgenesis.customjail.util.Response;
import net.foxgenesis.customjail.util.Utilities;
import net.foxgenesis.watame.util.Colors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;

public class JailFrontend extends ListenerAdapter {

	private static final int WARNINGS_PER_PAGE = 5;
	private static final String NA = "N/A";

	// ========================================================================================

	private static final Button addWarningOn = Button.primary("add-warning", "With Warning")
			.withEmoji(Emoji.fromFormatted("U+2705")),
			addWarningOff = Button.secondary("add-warning", "Without Warning").withEmoji(Emoji.fromFormatted("U+274C"));

	private static final SelectMenu timeMenu = StringSelectMenu
			.create("time-selection").addOptions(Arrays.stream(CustomJailPlugin.jailingTimes)
					.map(arr -> SelectOption.of(arr[0], arr[1])).toArray(SelectOption[]::new))
			.setPlaceholder("Set Time").build();

	// ========================================================================================
	private final IJailSystem jail;

	public JailFrontend(IJailSystem jail) {
		this.jail = Objects.requireNonNull(jail);
	}

	@Override
	public void onUserContextInteraction(UserContextInteractionEvent event) {
		// Only work with guild buttons
		if (!event.isFromGuild())
			return;

		switch (event.getName()) {
			case "Warnings" -> {
				validateMember(event, event::getTargetMember, (e, guild, member) -> {
					event.deferReply(true).queue();

					// Create warning embed
					WarningsEmbed embed = createWarningsEmbedForMember(null, member, 1);
					int page = embed.getPage();

					// Build response
					event.getHook().editOriginalEmbeds(embed.build()).setReplace(true)
							.setActionRow(
									Button.primary(wrapInteraction("warnings", member, "" + (page - 1)), "\u25C0")
											.withDisabled(page - 1 <= 0),
									Button.primary(wrapInteraction("warnings", member, "" + (page + 1)), "\u25B6")
											.withDisabled(page + 1 > embed.getMaxPage()))
							.queue();
				});
			}

			// ========================================================================================

			case "Jail User" -> {
				validateMember(event, event::getTargetMember, (e, guild, member) -> {
					// Check if member is already jailed
					if (jail.isJailed(member)) {
						event.replyEmbeds(Response.error("User is already jailed!")).setEphemeral(true).queue();
						return;
					}

					// Do not allow operations on self
					if (event.getMember().equals(member)) {
						event.replyEmbeds(Response.error("Unable to unjail self")).setEphemeral(true).queue();
						return;
					}

					// Check if moderator can interact with user
					if (!event.getMember().canInteract(member)) {
						event.replyEmbeds(Response.error("You are unable to interact with this member"))
								.setEphemeral(true).queue();
						return;
					}

					MessageEmbed embed = new JailEmbed(null).setMember(member).build();
					Button addWarning = jail.getWarningLevelForMember(member) >= CustomJailPlugin.getMaxWarnings(guild)
							? Button.danger("add-warning", "Max Warning Level Reached").asDisabled()
							: addWarningOn;
					Button jailButton = Button.danger(wrapInteraction("jailuser", member), "Jail").asDisabled();

					event.replyEmbeds(embed).addActionRow(timeMenu).addActionRow(addWarning, jailButton)
							.setEphemeral(true).queue();
				});
			}

			// ========================================================================================

			case "Jail Details" -> {
				validateMember(event, event::getTargetMember, (e, guild, user) -> {
					// Check if user is jailed
					if (!jail.isJailed(user)) {
						event.replyEmbeds(Response.error("User is not jailed!")).setEphemeral(true).queue();
						return;
					}

					// Display working
					event.deferReply(true).queue();

					// Attempt to obtain details
					Optional<JailDetails> jailData;
					try {
						jailData = jail.getJailDetails(user);
					} catch (Exception err) {
						CustomJailPlugin.displayError(event.getHook(), err).queue();
						return;
					}

					// Create response
					jailData.map(details -> {
						Member member = details.member();
						boolean isTimerRunning = jail.isJailTimerRunning(member);

						EmbedBuilder builder = new EmbedBuilder();
						// Header
						builder.setColor(Colors.INFO);
						builder.setTitle("Jailing Details");
						builder.setThumbnail(member.getEffectiveAvatarUrl());

						// Row 1
						builder.addField("Member", member.getAsMention(), true);
						builder.addField("Moderator", Optional.ofNullable(details.moderator()).map(Member::getAsMention)
								.orElse("Deleted User"), true);
						builder.addField("Case ID", details.caseid() == -1 ? NA : "" + details.caseid(), true);

						// Row 2
						builder.addField("Accepted", isTimerRunning ? "Yes" : "No", true);
						builder.addField("Duration", details.duration().getDisplayString(), true);
						builder.addField("Time Left",
								isTimerRunning
										? jail.getJailEndTimestamp(member)
												.map(UnixTimestamp::getRelativeTimeStringInSeconds).orElse(NA)
										: "Not Yet Accepted",
								true);

						// Row 3
						builder.addField("Reason", details.reason(), false);

						// Footer
						builder.setFooter(jail.getEmbedFooter());
						builder.setTimestamp(Instant.ofEpochMilli(details.timestamp()));

						// Buttons
						ActionRow interactions = ActionRow
								.of(Button.danger(wrapInteraction("forcestart", member), "Force Start").withDisabled(
										isTimerRunning), Button.danger(wrapInteraction("unjail", member), "Unjail"));

						return event.getHook().editOriginalEmbeds(builder.build()).setComponents(interactions);
					}).orElse(event.getHook()
							.editOriginalEmbeds(Response.error("Failed to find jail details. Please try again later.")))
							.setReplace(true).queue();
				});
			}
		}
	}

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		// Only work with guild buttons
		if (!event.isFromGuild())
			return;

		switch (event.getFullCommandName()) {
			case "warnings decrease" -> {
				event.deferReply(true).queue();

				InteractionHook hook = event.getHook();
				Member member = event.getOption("user", OptionMapping::getAsMember);
				Member moderator = event.getMember();
				Optional<String> reason = Optional.ofNullable(event.getOption("reason", OptionMapping::getAsString));

				// Decrease warning level
				jail.decreaseWarningLevel(member, Optional.of(moderator), reason, (oldLevel, newLevel) -> hook
						.editOriginalEmbeds(Response.success(
								"Decreased " + member.getAsMention() + "'s Level from " + oldLevel + " to " + newLevel))
						.queue(), err -> error(hook, err));
			}
			case "warnings remove" -> {
				event.deferReply(true).queue();

				InteractionHook hook = event.getHook();
				Guild guild = event.getGuild();
				int caseID = event.getOption("case-id", OptionMapping::getAsInt);
				Optional<String> reason = Optional.ofNullable(event.getOption("reason", OptionMapping::getAsString));

				if (caseID <= 0) {
					hook.editOriginalEmbeds(Response.error("Invalid Case-ID")).queue();
					return;
				}

				// Get warning if it exists
				jail.getWarning(guild, caseID).ifPresentOrElse(warning -> {
					// Attempt to delete warning
					if (jail.deleteWarning(guild, caseID, Optional.of(event.getMember()), reason))
						hook.editOriginalEmbeds(Response.success("Warning Removed")).queue();
					else
						hook.editOriginalEmbeds(Response.error("Failed to remove warning")).queue();
				}, () -> hook.editOriginalEmbeds(Response.error("No warning with case ID")).queue());
			}
			case "warnings update" -> {
				event.deferReply(true).queue();

				InteractionHook hook = event.getHook();
				Guild guild = event.getGuild();
				int caseID = event.getOption("case-id", OptionMapping::getAsInt);
				String newReason = event.getOption("new-reason", OptionMapping::getAsString);
				Optional<String> reason = Optional.ofNullable(event.getOption("reason", OptionMapping::getAsString));

				// Check if case id is valid
				if (!jail.isValidCaseID(caseID))
					hook.editOriginalEmbeds(Response.error("Invalid Case-ID")).queue();

				// Check if new reason is valid
				else if (newReason == null || newReason.isBlank())
					hook.editOriginalEmbeds(Response.error("Please specify a new reason")).queue();

				// Attempt to update warning reason
				else if (jail.updateWarningReason(guild, caseID, Optional.of(event.getMember()), newReason, reason))
					hook.editOriginalEmbeds(Response.success("Updated Warning")).queue();

				// Failed to update reason
				else
					hook.editOriginalEmbeds(Response.error("Failed to update warning")).queue();
			}

			// ========================================================================================

			case "jail" -> {
				event.deferReply(true).queue();

				InteractionHook hook = event.getHook();
				Member member = event.getOption("user", OptionMapping::getAsMember);
				Member moderator = event.getMember();
				CustomTime time = new CustomTime(event.getOption("duration", OptionMapping::getAsString));
				String reason = event.getOption("reason", OptionMapping::getAsString);
				boolean addWarning = event.getOption("add-warning", true, OptionMapping::getAsBoolean);

				// Check if member is already jailed
				if (jail.isJailed(member)) {
					event.replyEmbeds(Response.error("User is already jailed!")).setEphemeral(true).queue();
					return;
				}

				// Do not allow operations on self
				if (moderator.equals(member)) {
					event.replyEmbeds(Response.error("Unable to unjail self")).setEphemeral(true).queue();
					return;
				}

				// Check if moderator can interact with user
				if (!moderator.canInteract(member)) {
					event.replyEmbeds(Response.error("You are unable to interact with this member")).setEphemeral(true)
							.queue();
					return;
				}

				jail.jail(member, moderator, time, reason, addWarning,
						details -> hook.editOriginalEmbeds(Response.success(
								"Jailed " + member.getAsMention() + " for " + details.duration().getDisplayString()))
								.setReplace(true).queue(),
						err -> error(hook, err));
			}
			case "unjail" -> {
				event.deferReply(true).queue();

				InteractionHook hook = event.getHook();
				Member member = event.getOption("user", OptionMapping::getAsMember);
				Optional<Member> moderator = Optional.of(event.getMember());
				Optional<String> reason = Optional.ofNullable(event.getOption("reason", OptionMapping::getAsString));

				jail.unjail(member, moderator, reason,
						() -> hook.editOriginalEmbeds(Response.success("Unjailed " + member.getAsMention())),
						err -> error(hook, err));
			}
			case "forcestart" -> {
				event.deferReply(true).queue();

				InteractionHook hook = event.getHook();
				Member member = event.getOption("user", OptionMapping::getAsMember);
				Optional<Member> moderator = Optional.of(event.getMember());
				Optional<String> reason = Optional.ofNullable(event.getOption("reason", OptionMapping::getAsString));

				jail.startJailTimer(member, moderator, reason,
						timeLeft -> hook.editOriginalEmbeds(
								Response.success("**Time Remaining:** " + timeLeft.getRelativeTimeStringInSeconds())),
						err -> error(hook, err));
			}
		}
	}

	@Override
	public void onButtonInteraction(ButtonInteractionEvent event) {
		// Only work with guild buttons
		if (!event.isFromGuild())
			return;

		if (unwrapInteraction(event, (id, unwrappedMember, variant) -> {
			Member pressed = event.getMember();
			InteractionHook hook = event.getHook();

			unwrappedMember.ifPresentOrElse(member -> {
				switch (id) {
					case "forcestart" -> {
						if (jail.isJailTimerRunning(member)) {
							hook.editOriginalEmbeds(Response.error("Timer is already running")).queue();
							return;
						}
						// Display reason modal
						displayReasonModal(event, () -> member, "forcestart", "Reason for force start", 500);
					}
					case "unjail" -> {
						if (!jail.isJailed(member)) {
							hook.editOriginalEmbeds(Response.error("Member is not jailed")).queue();
							return;
						}
						// Display reason modal
						displayReasonModal(event, () -> member, "unjail", "Reason for unjailing", 500);
					}
					// ========================================================================================

					case "startjail" -> {
						// Ensure button belongs to who pressed it
						if (!pressed.equals(member)) {
							event.replyEmbeds(Response.error("This is not your punishment!")).setEphemeral(true)
									.queue();
							return;
						}

						// Check if member is jailed
						if (!jail.isJailed(member)) {
							event.replyEmbeds(Response.error("You are not jailed!")).setEphemeral(true).queue();
							return;
						}

						// Display working
						event.deferReply(true).queue();

						// Start jail time
						jail.startJailTimer(member, Optional.empty(), Optional.empty(), timeLeft -> {
							// Display that the punishment was accepted
							if (event.getButton().getStyle() != ButtonStyle.SUCCESS)
								event.editButton(event.getButton().withStyle(ButtonStyle.SUCCESS).withLabel("Accepted"))
										.queue();

							// Display time left
							hook.editOriginalEmbeds(Response
									.success("**Time Remaining:** " + timeLeft.getRelativeTimeStringInSeconds()))
									.queue();
						}, err -> error(hook, err));
					}

					case "jailuser" -> {
						// Display reason modal
						displayReasonModal(event, () -> member, "jailuser", "Reason for jail", 500);
					}

					// ========================================================================================

					case "warnings" -> {
						event.deferEdit().queue();

						// Ensure page is inside wrapped event
						if (variant.isEmpty()) {
							hook.editOriginalEmbeds(Response.error("Invalid Page")).queue();
							return;
						}

						// Parse embed from message
						WarningsEmbed embed = new WarningsEmbed(event.getMessage().getEmbeds().get(0));

						// Get new page
						int page = variant.map(Integer::parseInt).map(p -> Utilities.clamp(p, 1, embed.getMaxPage()))
								.orElseThrow();

						// Set embed's new details
						embed.setPageNumber(page);
						embed.setWarnings(jail.getWarningsPageForMember(member, WARNINGS_PER_PAGE, page));

						Set<ItemComponent> components = new HashSet<>();

						// Check if we can go back a page
						components.add(Button.primary(wrapInteraction("warnings", member, "" + (page - 1)), "\u25C0")
								.withDisabled(page - 1 <= 0));

						// Check if we can advance a page
						components.add(Button.primary(wrapInteraction("warnings", member, "" + (page + 1)), "\u25B6")
								.withDisabled(page + 1 > embed.getMaxPage()));

						// Build response
						WebhookMessageEditAction<Message> a = event.getHook().editOriginalEmbeds(embed.build())
								.setReplace(true);
						if (!components.isEmpty())
							a.setActionRow(components);
						a.queue();
					}
				}
			},
					// Unable to find member button was wrapped to
					() -> event.editButton(event.getButton().asDisabled())
							.and(event.replyEmbeds(Response.error("User does not exist"))).queue());
		}))
			return;

		switch (event.getComponentId()) {
			case "add-warning" ->
				// Toggle button state
				event.editButton(event.getButton().equals(addWarningOn) ? addWarningOff : addWarningOn).queue();

			// ========================================================================================

			// Old button reply to keep user space
			case "start-jail" -> event
					.replyEmbeds(Response.error(
							"This button is no longer valid. Please ask a moderator to start the punishment for you."))
					.setEphemeral(true).queue();
		}
	}

	@Override
	public void onStringSelectInteraction(StringSelectInteractionEvent event) {
		// Only work with guild buttons
		if (!event.isFromGuild())
			return;

		switch (event.getComponentId()) {
			case "time-selection" -> {
				Message message = event.getMessage();
				CustomTime time = event.getValues().stream().reduce((a, b) -> a + b).map(CustomTime::new).orElseThrow();

				// Update embed if value is valid
				if (time != null) {
					MessageEmbed newEmbed = new JailEmbed(message.getEmbeds().get(0)).setTime(time).build();

					SelectMenu menu = event.getComponent().createCopy().setDefaultValues(event.getValues()).build();

					Button addWarning = message.getButtonById("add-warning");
					Button jailButton = message.getButtons().stream().filter(b -> b.getId().startsWith("jailuser"))
							.findFirst().orElseThrow().asEnabled();

					// Update message
					event.editComponents(ActionRow.of(menu), ActionRow.of(addWarning, jailButton)).setEmbeds(newEmbed)
							.setReplace(true).queue();
				}

			}
		}
	}

	@Override
	public void onModalInteraction(ModalInteractionEvent event) {
		// Only work with guild buttons
		if (!event.isFromGuild())
			return;

		if (unwrapInteraction(event, (id, unwrappedMember, variant) -> {
			Member pressed = event.getMember();
			InteractionHook hook = event.getHook();

			unwrappedMember.ifPresentOrElse(member -> {
				switch (id) {
					case "addreason" -> {
						// Ensure the callback is valid
						if (variant.isEmpty()) {
							event.replyEmbeds(Response.error("Invalid Interaction")).queue();
							return;
						}

						// Pass reason back
						Optional<String> reason = Optional.ofNullable(event.getValue("reason").getAsString())
								.filter(r -> !r.isBlank());

						// Switch based on callback
						switch (variant.get()) {
							case "forcestart" -> {
								event.deferReply(true).queue();
								jail.startJailTimer(member, Optional.of(pressed), reason, timeLeft -> hook
										.editOriginalEmbeds(Response.success(
												"**Time Remaining:** " + timeLeft.getRelativeTimeStringInSeconds()))
										.setReplace(true).queue(), err -> error(hook, err));
							}
							case "unjail" -> {
								event.deferReply(true).queue();
								jail.unjail(member, Optional.of(pressed), reason, () -> hook
										.editOriginalEmbeds(Response.success("Unjailed " + member.getAsMention()))
										.setReplace(true).queue(), err -> error(hook, err));
							}
							case "jailuser" -> {
								event.deferEdit().queue();

								// Get selected time
								Optional<CustomTime> optTime = ((StringSelectMenu) event.getMessage().getActionRows()
										.get(0).getComponents().get(0)).getOptions().stream()
										.filter(SelectOption::isDefault).findAny().map(SelectOption::getValue)
										.map(CustomTime::new);

								// Check toggle button state
								boolean addWarning = event.getMessage().getButtonById("add-warning")
										.equals(addWarningOn);

								// Ensure we have a time selected
								if (optTime.isEmpty()) {
									hook.editOriginalEmbeds(Response.error("No time selected!")).queue();
									return;
								}

								// Jail user
								jail.jail(member, pressed, optTime.get(), reason.orElse(null), addWarning,
										jailDetails -> hook
												.editOriginalEmbeds(Response.success("Jailed " + member.getAsMention()
														+ " for " + jailDetails.duration().getDisplayString()))
												.setReplace(true).queue(),
										err -> error(hook, err));

							}
						}
					}
				}
			}, () -> event.replyEmbeds(Response.error("User does not exist")).queue());
		}))
			return;
	}

	// ========================================================================================

	private WarningsEmbed createWarningsEmbedForMember(@Nullable MessageEmbed embed, @NotNull Member member, int page) {
		return new WarningsEmbed(embed,
				() -> Utilities.calculateMaxPages(WARNINGS_PER_PAGE, jail.getTotalWarnings(member))).setUser(member)
				.setTotalWarning(jail.getTotalWarnings(member)).setWarningLevel(jail.getWarningLevelForMember(member))
				.setPageNumber(page).setWarnings(jail.getWarningsPageForMember(member, WARNINGS_PER_PAGE, page));
	}

	private static void error(InteractionHook hook, InternalException err) {
		hook.editOriginalEmbeds(err.getDisplayableMessage()).setReplace(true).queue();
	}
}

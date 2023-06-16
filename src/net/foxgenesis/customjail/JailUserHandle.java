package net.foxgenesis.customjail;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.foxgenesis.customjail.embed.JailEmbed;
import net.foxgenesis.customjail.time.CustomTime;
import net.foxgenesis.customjail.util.Response;

public class JailUserHandle extends ListenerAdapter {
	private static final Button submitButton = Button.danger("jail-user", "Jail User")
			.withEmoji(Emoji.fromFormatted("U+1F6A8")).asDisabled(),
			reasonButton = Button.primary("add-reason", "Add Reason"),
			addWarningOn = Button.secondary("add-warning", "Adds Warning").withEmoji(Emoji.fromFormatted("U+2714")),
			addWarningOff = Button.secondary("add-warning", "No Warning").withEmoji(Emoji.fromFormatted("U+274C"));

	private static final SelectMenu timeMenu = SelectMenu.create("time-selection").addOptions(getTimeOptions())
			.setPlaceholder("Set Time").build();

	// ========================================================================================

	private final IJailHandler jailer;

	public JailUserHandle(IJailHandler jailer) {
		this.jailer = Objects.requireNonNull(jailer);
	}

	@Override
	public void onUserContextInteraction(UserContextInteractionEvent event) {
		switch (event.getName()) {
			case "Jail User" -> {
				Member member = event.getTargetMember();
				if (jailer.isJailed(member))
					event.replyEmbeds(Response.error("User is already jailed!")).setEphemeral(true).queue();
				else
					event.replyEmbeds(new JailEmbed(null).setMember(member).build()).addActionRow(timeMenu)
							.addActionRow(reasonButton, addWarningOn, submitButton.asDisabled()).setEphemeral(true)
							.queue();
			}
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
			case "add-warning" ->
				event.editButton(event.getButton().equals(addWarningOn) ? addWarningOff : addWarningOn).queue();
		}
	}

	@Override
	public void onModalInteraction(ModalInteractionEvent event) {
		switch (event.getModalId()) {
			case "add-reason" -> event.editMessageEmbeds(new JailEmbed(event.getMessage().getEmbeds().get(0))
					.setReason(event.getValue("reason").getAsString()).build()).queue();
		}
	}

	// ========================================================================================

	private void handleSubmit(ButtonInteractionEvent event) {
		event.deferEdit().queue();

		// Construct data
		MessageEmbed embed = event.getMessage().getEmbeds().get(0);
		Map<String, String> fields = embed.getFields().stream()
				.collect(Collectors.toMap(field -> field.getName(), field -> field.getValue()));

		Member member = event.getGuild().getMemberById(fields.get("User ID"));
		String reason = fields.getOrDefault("Reason", "No Reason Provided");

		Optional<SelectOption> selected = ((SelectMenu) event.getMessage().getActionRows().get(0).getComponents()
				.get(0)).getOptions().stream().filter(SelectOption::isDefault).findAny();

		// Jail user
		jailer.jail(event.getHook(), member, event.getMember(),
				selected.map(SelectOption::getValue).map(CustomTime::new).get(), reason,
				event.getMessage().getButtonById("add-warning").equals(addWarningOn));
	}

	// ========================================================================================

	private static void handleTimeSelection(SelectMenuInteractionEvent event) {
		event.deferEdit().queue();

		Message message = event.getMessage();
		CustomTime time = event.getValues().stream().reduce((a, b) -> a + b).map(CustomTime::new).orElseThrow();

		// Update embed if value is valid
		if (time != null) {
			MessageEmbed newEmbed = new JailEmbed(message.getEmbeds().get(0)).setTime(time).build();
			event.getHook()
					.editOriginalComponents(
							ActionRow.of(event.getComponent().createCopy().setDefaultValues(event.getValues()).build()),
							ActionRow.of(reasonButton, message.getButtonById("add-warning"), submitButton.asEnabled()))
					.setEmbeds(newEmbed).queue();
		}
	}

	private static void showReasonModal(ButtonInteractionEvent event) {
		event.replyModal(Modal.create("add-reason", "Add Reason")
				.addActionRow(TextInput.create("reason", "Reason", TextInputStyle.PARAGRAPH).setRequired(true)
						.setPlaceholder("Reason for jail").setMaxLength(500).build())
				.build()).queue();
	}

	@Nonnull
	private static SelectOption[] getTimeOptions() {
		return CustomJailPlugin.timeList.stream().map(arr -> SelectOption.of(arr[0], arr[1]))
				.toArray(SelectOption[]::new);
	}
}

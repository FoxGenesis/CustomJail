package net.foxgenesis.customjail;

import static net.foxgenesis.customjail.CustomJailPlugin.modlog;
import static net.foxgenesis.customjail.CustomJailPlugin.notReady;

import java.time.Instant;
import java.util.Objects;

import javax.annotation.Nullable;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.foxgenesis.customjail.database.IWarningDatabase;
import net.foxgenesis.customjail.embed.WarningsEmbed;
import net.foxgenesis.watame.Constants;

public class UserWarningHandler extends ListenerAdapter {
	private static final int WARNINGS_PER_PAGE = 5;

	private static final Button backButton = Button.primary("prev-page", "Previous Page"),
			nextButton = Button.primary("next-page", "Next Page");

	// ========================================================================================

	private final IWarningDatabase database;

	public UserWarningHandler(@SuppressWarnings("exports") IWarningDatabase database) {
		this.database = Objects.requireNonNull(database);
	}

	@Override
	public void onUserContextInteraction(UserContextInteractionEvent event) {
		switch (event.getName()) {
			case "Warnings" -> {
				event.deferReply(true).queue();
				sendWarningEmbed(event.getHook(), createWarningsEmbedForMember(event.getTargetMember()));
			}
		}
	}

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		switch (event.getCommandPath()) {
			case "warnings/add" -> notReady(event);
			case "warnings/get" -> {
				event.deferReply(true).queue();
				Member member = event.getOption("user", OptionMapping::getAsMember);

				if (member != null)
					sendWarningEmbed(event.getHook(), createWarningsEmbedForMember(member));
				else
					event.getHook().editOriginal("Invalid Member").queue();
			}
			case "warnings/remove" -> {
				event.deferReply(true).queue();
				InteractionHook hook = event.getHook();

				Guild guild = event.getGuild();
				int caseID = event.getOption("case-id", OptionMapping::getAsInt);
				String reason = event.getOption("reason", "No Reason Provided", OptionMapping::getAsString);

				if (caseID <= 0)
					hook.editOriginal("Invalid Case-ID").queue();
				else if (database.deleteWarning(guild, caseID))
					modlog(hook.editOriginal("Removed Warning"), guild,
							() -> new EmbedBuilder().setColor(Constants.Colors.WARNING_DARK).setTitle("Warning Removed")
									.addField("case-id", caseID + "", true)
									.addField("Moderator", hook.getInteraction().getMember().getAsMention(), true)
									.addField("Reason", reason, false).setTimestamp(Instant.now()).build())
							.queue();
				else
					hook.editOriginal("Failed to remove warning").queue();

			}
			case "warnings/update" -> {
				event.deferReply(true).queue();
				InteractionHook hook = event.getHook();

				Guild guild = event.getGuild();
				int caseID = event.getOption("case-id", OptionMapping::getAsInt);
				String newReason = event.getOption("new-reason", OptionMapping::getAsString);
				String reason = event.getOption("reason", "No Reason Provided", OptionMapping::getAsString);

				if (caseID <= 0)
					hook.editOriginal("Invalid Case-ID").queue();
				else if (newReason == null || newReason.isBlank())
					hook.editOriginal("Please specify a new reason").queue();
				else if (database.updateWarningReason(guild, caseID, newReason))
					modlog(hook.editOriginal("Updated Warning"), guild,
							() -> new EmbedBuilder().setColor(Constants.Colors.WARNING_DARK).setTitle("Warning Updated")
									.addField("case-id", caseID + "", true)
									.addField("Moderator", hook.getInteraction().getMember().getAsMention(), true)
									.addField("Reason", reason, false).setTimestamp(Instant.now()).build())
							.queue();
				else
					hook.editOriginal("Failed to update warning").queue();
			}
		}
	}

	@Override
	public void onButtonInteraction(ButtonInteractionEvent event) {
		switch (event.getComponentId()) {
			case "next-page" -> {
				event.deferEdit().queue();
				WarningsEmbed embed = new WarningsEmbed(event.getMessage().getEmbeds().get(0));
				embed.setPageNumber(embed.getPage() + 1);

				embed.getMember(event.getGuild()).ifPresent(member -> embed
						.setWarnings(database.getWarningsPageForMember(member, WARNINGS_PER_PAGE, embed.getPage())));
				sendWarningEmbed(event.getHook(), embed);

			}
			case "prev-page" -> {
				event.deferEdit().queue();
				WarningsEmbed embed = new WarningsEmbed(event.getMessage().getEmbeds().get(0));
				embed.setPageNumber(embed.getPage() - 1);

				embed.getMember(event.getGuild()).ifPresent(member -> embed
						.setWarnings(database.getWarningsPageForMember(member, WARNINGS_PER_PAGE, embed.getPage())));
				sendWarningEmbed(event.getHook(), embed);
			}
		}
	}

	private WarningsEmbed createWarningsEmbedForMember(Member member) {
		return createWarningsEmbedForMember(null, member, 1);
	}

	private WarningsEmbed createWarningsEmbedForMember(@Nullable MessageEmbed embed, Member member, int page) {
		return new WarningsEmbed(embed, () -> calculateMaxPages(database.getTotalWarnings(member))).setUser(member)
				.setWarningLevel(database.getWarningLevelForMember(member)).setPageNumber(page)
				.setWarnings(database.getWarningsPageForMember(member, WARNINGS_PER_PAGE, page));
	}

	private static void sendWarningEmbed(InteractionHook hook, WarningsEmbed embed) {
		int page = embed.getPage();
		hook.editOriginalEmbeds(embed.build()).setActionRow(page - 1 > 0 ? backButton : backButton.asDisabled(),
				page < embed.getMaxPage() ? nextButton : nextButton.asDisabled()).setReplace(true).queue();
	}

	private static int calculateMaxPages(int totalWarnings) {
		int div = totalWarnings / WARNINGS_PER_PAGE;
		if (totalWarnings == 0 || totalWarnings % WARNINGS_PER_PAGE > 0)
			div++;
		return div;
	}
}

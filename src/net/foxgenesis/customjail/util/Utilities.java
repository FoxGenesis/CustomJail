package net.foxgenesis.customjail.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.InteractionType;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.RestAction;
import net.foxgenesis.customjail.CustomJailPlugin;
import net.foxgenesis.util.function.TriConsumer;

public final class Utilities {
	private static final Logger logger = LoggerFactory.getLogger(Utilities.class);

	public static final class Warnings {
		public static Set<Role> modifyWarningRoles(@NotNull Member member, int level) {
			Objects.requireNonNull(member);

			Guild guild = member.getGuild();
			List<Role> warningRoles = CustomJailPlugin.getWarningRoles(guild);

			int maxLevel = Math.min(CustomJailPlugin.getMaxWarnings(guild), warningRoles.size());
			int newLevel = Utilities.clamp(level, 0, maxLevel);

			logger.info("Updating Warning Level for {} to {}/{}", member, newLevel, maxLevel);

			List<List<Role>> split = Utilities.split(warningRoles, newLevel);

			Set<Role> roles = new HashSet<>(member.getRoles());
			roles.addAll(split.get(0));
			roles.removeAll(split.get(1));
			return roles;
		}

		public static RestAction<Void> updateWarningLevel(@NotNull Member member, int level, Optional<String> reason) {
			// Modify user roles
			return Objects.requireNonNull(member).getGuild()
					.modifyMemberRoles(member, modifyWarningRoles(member, level))
					.reason(reason.orElse("Warning level update"));
		}
	}

	public static final class Interactions {
		private static final Pattern UNWRAP_SPLIT = Pattern.compile(":");

		public static String wrapInteraction(@NotNull String interactionID, @NotNull Member member) {
			return wrapInteraction(interactionID, member, null);
		}

		public static String wrapInteraction(@NotNull String interactionID, @NotNull Member member,
				@Nullable String variant) {
			Objects.requireNonNull(interactionID);
			Objects.requireNonNull(member);

			if (interactionID.length() > 50)
				throw new IllegalArgumentException("Max buttonID is 50 characters");
			if (variant != null && variant.length() > 30)
				throw new IllegalArgumentException("Max variant length is 30 characters");

			logger.debug("Wrapping Interaction {}:{} for {}", interactionID, variant, member);

			return interactionID + ':' + member.getId() + (variant != null ? ':' + variant : "");
		}

		public static <T extends GenericInteractionCreateEvent> boolean unwrapInteraction(@NotNull T event,
				@NotNull TriConsumer<String, Optional<Member>, Optional<String>> unwrapped) {
			// Extract interaction id based on interaction type
			String id = event.getType() == InteractionType.COMPONENT
					? ((GenericComponentInteractionCreateEvent) event).getComponentId()
					: event.getType() == InteractionType.MODAL_SUBMIT ? ((ModalInteractionEvent) event).getModalId()
							: null;
			if (id == null)
				throw new IllegalArgumentException("Wrapped events must be of type COMPONENT or MODAL_SUBMIT!");

			String[] split = UNWRAP_SPLIT.split(id, 3);
			boolean hasVar = split.length == 3;

			// Only work with valid unwraps
			if (!(split.length == 2 || hasVar))
				return false;

			// Validate unwrap
			if ((split[0] == null || split[0].isBlank()) || (hasVar && (split[2] == null || split[2].isBlank())))
				return false;

			Optional<Member> unwrappedMember = Optional.ofNullable(event.getGuild().getMemberById(split[1]));
			Optional<String> variant = hasVar ? Optional.of(split[2]) : Optional.empty();

			logger.debug("Unwrapped Interaction {}:{} for {}", id, variant, unwrappedMember);

			// Callback
			unwrapped.accept(split[0], unwrappedMember, variant);
			return true;
		}

		// ========================================================================================

		public static <T extends IReplyCallback> void validateMember(@NotNull T callback,
				@Nullable Supplier<Member> targetMember, @NotNull TriConsumer<T, Guild, Member> consumer) {
			Member member = targetMember != null ? targetMember.get() : callback.getMember();

			if (member == null)
				callback.replyEmbeds(Response.error("User does not exist!")).setEphemeral(true).queue();
			else if (member.getUser().isBot() || member.getUser().isSystem())
				callback.replyEmbeds(Response.error("User is a bot")).queue();
			else
				consumer.accept(callback, callback.getGuild(), member);
		}

		public static <T extends IReplyCallback> void validateMember(@NotNull T callback,
				@NotNull TriConsumer<T, Guild, Member> consumer) {
			validateMember(callback, () -> callback.getMember(), consumer);
		}

		public static void displayReasonModal(@NotNull GenericComponentInteractionCreateEvent event,
				@Nullable Supplier<Member> wrappedMember, @NotNull String callback, @NotNull String placeholder,
				int maxLength) {
			event.replyModal(Modal
					.create(Utilities.Interactions.wrapInteraction("addreason",
							wrappedMember != null ? wrappedMember.get() : event.getMember(), callback), "Add Reason")
					.addActionRow(TextInput.create("reason", "Reason", TextInputStyle.PARAGRAPH)
							.setPlaceholder(placeholder).setRequired(false).setMaxLength(maxLength).build())
					.build()).queue();
		}
	}

	// ========================================================================================

	public static String prettyPrintDuration(@NotNull Optional<Duration> duration) {
		return duration.map(Duration::toMillis).map(n -> DurationFormatUtils.formatDurationWords(n, true, true))
				.orElse("null");
	}

	public static String prettyPrintDuration(@Nullable Duration duration) {
		return prettyPrintDuration(Optional.ofNullable(duration));
	}

	public static int clamp(int in, int min, int max) {
		return Math.max(Math.min(in, max), min);
	}

	public static <T> List<List<T>> split(@NotNull List<T> one, int index) {
		return split(one, index, false);
	}

	/**
	 * Split a list into two halves based on an index. If {@code inclusive} is set
	 * to {@code true}, the {@code index} will be included in the first list,
	 * otherwise in the second.
	 * 
	 * @param <T>       List type
	 * @param list      - The {@code List} to split
	 * @param index     - Index to split at
	 * @param inclusive - Should the index be included in the first list
	 * 
	 * @return Returns a {@link List} containing the two halves of the split list.
	 * 
	 * @throws NullPointerException Thrown if the specified list is {@code null}
	 */
	public static <T> List<List<T>> split(@NotNull List<T> list, int index, boolean inclusive) {
		Objects.requireNonNull(list);
		List<T> one = new ArrayList<>(list); // Non-backed copy

		// Neat trick found from https://stackoverflow.com/a/379824 :D
		List<T> sub = one.subList(0, Math.max(0, Math.min(one.size(), inclusive ? index + 1 : index)));
		List<T> two = new ArrayList<>(sub); // Non-backed copy
		sub.clear(); // since sub is backed by one, this removes all sub-list items from one
		return List.of(two, one);
	}

	public static int calculateMaxPages(int warningsPerPage, int totalWarnings) {
		int div = totalWarnings / warningsPerPage;
		if (totalWarnings == 0 || totalWarnings % warningsPerPage > 0)
			div++;
		return div;
	}
}
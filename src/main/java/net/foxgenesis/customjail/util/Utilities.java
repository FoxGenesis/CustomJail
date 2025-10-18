package net.foxgenesis.customjail.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.lang3.function.TriConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.InteractionType;

public final class Utilities {
	private static final Logger logger = LoggerFactory.getLogger(Utilities.class);

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

			Optional<String> variant = hasVar ? Optional.of(split[2]) : Optional.empty();


			event.getGuild().retrieveMemberById(split[1]).onErrorMap(e -> null).queue(unwrappedMember -> {
				logger.debug("Unwrapped Interaction {}:{} for {}", id, variant, unwrappedMember);
				unwrapped.accept(split[0], Optional.ofNullable(unwrappedMember), variant);
			});
			
			return true;
		}
	}

	// ========================================================================================


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
	
	public static String nullIfBlank(String in) {
		return in == null || in.isBlank() ? null : in;
	}
}
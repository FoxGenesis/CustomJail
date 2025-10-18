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
import net.foxgenesis.watame.util.StringUtils;

public final class Utilities {
	private static final Logger logger = LoggerFactory.getLogger(Utilities.class);

	public static final class Interactions {
		/**
		 * Max length for primary IDs when wrapping interactions
		 */
		public static final int MAX_PRIMARY_ID_LENGTH = 49;

		/**
		 * Max length for secondary IDs when wrapping interactions
		 */
		public static final int MAX_SECONDARY_ID_LENGTH = 29;

		private static final Pattern UNWRAP_SPLIT = Pattern.compile(":");

		/**
		 * Class containing commonly used IDs for
		 * {@link Utilities.Interactions#wrapInteraction(WrappedInteractions, Member, String)}
		 * 
		 * @author Ashley
		 */
		public static enum WrappedInteractions {
			ADD_REASON, JAIL_USER, UNJAIL, START_JAIL, FORCE_START;

			private final String id;

			WrappedInteractions() {
				this.id = this.name().replaceAll("_", "");
				if (this.id.length() > MAX_PRIMARY_ID_LENGTH)
					throw new IllegalArgumentException("WrappedInteractions ID [" + this.name() + "] is longer than "
							+ MAX_PRIMARY_ID_LENGTH + " characters even when shortened!");
			}

			public final String getShortId() {
				return id;
			}

			/**
			 * Create a callback for a specific member by filling a components "Custom ID"
			 * with data. This "Custom ID" is created with an ID and Member ID.
			 * <p>
			 * This is a utility method and is effectively equivalent to:
			 * 
			 * <pre>
			 * wrapInteraction(member, null)
			 * </pre>
			 * 
			 * See {@link Utilities.Interactions#wrapInteraction(String, Member, String)}
			 * for full documentation.
			 * 
			 * 
			 * 
			 * @param interactionID - primary ID of this interaction
			 * @param member        - {@link Member} to wrap this interaction for
			 * @return Returns the wrapped "Custom ID" that a component should use to
			 *         successfully unwrap it via
			 *         {@link #unwrapInteraction(GenericInteractionCreateEvent, TriConsumer)}
			 * @throws NullPointerException Thrown if {@code interactionID} or
			 *                              {@code member} is {@code null}
			 * @see Utilities.Interactions#wrapInteraction(String, Member, String)
			 * @see Utilities.Interactions#unwrapInteraction(GenericInteractionCreateEvent,
			 *      TriConsumer)
			 */
			public String wrapInteraction(@NotNull Member member) {
				return wrapInteraction(member, null);
			}

			/**
			 * Create a callback for a specific member by filling a components "Custom ID"
			 * with data. This "Custom ID" is created with an ID and Member ID.
			 * <p>
			 * This is a utility method and is effectively equivalent to:
			 * 
			 * <pre>
			 * Utilities.Interactions.wrapInteraction(
			 * 	name().length() > {@link MAX_PRIMARY_ID_LENGTH} ? getShortId() : name(),
			 *	member, null)
			 * </pre>
			 * 
			 * See {@link Utilities.Interactions#wrapInteraction(String, Member, String)}
			 * for full documentation.
			 * 
			 * 
			 * 
			 * @param interactionID - primary ID of this interaction
			 * @param member        - {@link Member} to wrap this interaction for
			 * @return Returns the wrapped "Custom ID" that a component should use to
			 *         successfully unwrap it via
			 *         {@link #unwrapInteraction(GenericInteractionCreateEvent, TriConsumer)}
			 * @throws NullPointerException Thrown if {@code interactionID} or
			 *                              {@code member} is {@code null}
			 * @see Utilities.Interactions#wrapInteraction(String, Member, String)
			 * @see Utilities.Interactions#unwrapInteraction(GenericInteractionCreateEvent,
			 *      TriConsumer)
			 */
			public String wrapInteraction(@NotNull Member member, @Nullable String variant) {
				return Utilities.Interactions
						.wrapInteraction(name().length() > MAX_PRIMARY_ID_LENGTH ? getShortId() : name(), member, null);
			}

			public static WrappedInteractions parse(String id) {
				Objects.requireNonNull(StringUtils.nullIfBlank(id));

				// Attempt to parse based on enum name
				WrappedInteractions parsed = WrappedInteractions.valueOf(id);
				if (parsed != null)
					return parsed;

				// Attempt to parse based on short name
				for (WrappedInteractions wrapped : WrappedInteractions.values())
					if (wrapped.id.equalsIgnoreCase(id))
						return wrapped;

				// Not found
				return null;
			}
		}

		/**
		 * Create a callback for a specific member by filling a components "Custom ID"
		 * with data. This "Custom ID" is created with an ID and Member ID.
		 * <p>
		 * This is a utility method and is effectively equivalent to:
		 * 
		 * <pre>
		 * Utilities.Interactions.wrapInteraction(interactionID, member, null)
		 * </pre>
		 * 
		 * See {@link #wrapInteraction(String, Member, String)} for full documentation.
		 * 
		 * 
		 * 
		 * @param interactionID - primary ID of this interaction
		 * @param member        - {@link Member} to wrap this interaction for
		 * @return Returns the wrapped "Custom ID" that a component should use to
		 *         successfully unwrap it via
		 *         {@link #unwrapInteraction(GenericInteractionCreateEvent, TriConsumer)}
		 * @throws IllegalArgumentException Thrown if {@code interactionID} is longer
		 *                                  than {@link #MAX_PRIMARY_ID_LENGTH} or
		 *                                  contains ' : '
		 * @throws NullPointerException     Thrown upon the following conditions:
		 *                                  <ul>
		 *                                  <li>{@code interactionID} is {@code null} or
		 *                                  blank</li>
		 *                                  <li>{@code member} is {@code null}
		 *                                  </ul>
		 * @see #wrapInteraction(String, Member, String)
		 * @see #unwrapInteraction(GenericInteractionCreateEvent, TriConsumer)
		 */
		public static String wrapInteraction(@NotNull String interactionID, @NotNull Member member) {
			return wrapInteraction(interactionID, member, null);
		}

		/**
		 * Create a callback for a specific member by filling a components "Custom ID"
		 * with data. This "Custom ID" is created with an ID, Member ID and optional
		 * variant.
		 * <p>
		 * The "Custom ID" is assembled as followed: {@code Primary : Member : Variant}
		 * </p>
		 * <p>
		 * The max length of a Custom ID is 100. As such, this function splits the
		 * length as follows:
		 * </p>
		 * <ul>
		 * <li><b>{@value #MAX_PRIMARY_ID_LENGTH}</b> - Interaction ID</li>
		 * <li><b>1</b> - ' : ' to separate data</li>
		 * <li><b>20</b> - Member ID (unsigned long contains max 20 digits)</li>
		 * <li><b>1</b> - ' : ' to separate data</li>
		 * <li><b>{@value #MAX_SECONDARY_ID_LENGTH}</b> - Variant</li>
		 * </ul>
		 * 
		 * 
		 * @param interactionID - primary ID of this interaction
		 * @param member        - {@link Member} to wrap this interaction for
		 * @param variant       - (optional) sub ID of this interaction
		 * @return Returns the wrapped "Custom ID" that a component should use to
		 *         successfully unwrap it via
		 *         {@link #unwrapInteraction(GenericInteractionCreateEvent, TriConsumer)}
		 * @throws IllegalArgumentException Thrown upon the following conditions:
		 *                                  <ul>
		 *                                  <li>{@code interactionID} is longer than
		 *                                  {@link #MAX_PRIMARY_ID_LENGTH} or contains '
		 *                                  : '</li>
		 *                                  <li>{@code variant} is longer than
		 *                                  {@link #MAX_SECONDARY_ID_LENGTH}
		 *                                  </ul>
		 * @throws NullPointerException     Thrown upon the following conditions:
		 *                                  <ul>
		 *                                  <li>{@code interactionID} is {@code null} or
		 *                                  blank</li>
		 *                                  <li>{@code member} is {@code null}
		 *                                  </ul>
		 * @see #unwrapInteraction(GenericInteractionCreateEvent, TriConsumer)
		 */
		public static String wrapInteraction(@NotNull String interactionID, @NotNull Member member,
				@Nullable String variant) {
			Objects.requireNonNull(StringUtils.nullIfBlank(interactionID));
			Objects.requireNonNull(member);

			// Validate id length is capped at 49
			if (interactionID.length() > MAX_PRIMARY_ID_LENGTH)
				throw new IllegalArgumentException("Max primary ID length is " + MAX_PRIMARY_ID_LENGTH + " characters");
			// Ensure id does not contain colon
			else if (interactionID.contains(":"))
				throw new IllegalArgumentException(
						"Interaction ID must not contain ':' as it's used to seperate data!");

			String out = interactionID + ':' + member.getId();

			// Ensure variant length is capped at 29
			if (StringUtils.nullIfBlank(variant) != null && variant.length() > MAX_SECONDARY_ID_LENGTH)
				throw new IllegalArgumentException(
						"Max secondary ID length is " + MAX_SECONDARY_ID_LENGTH + " characters");
			else
				out += ':' + variant;

			logger.debug("Wrapping Interaction {}:{} for {}", interactionID, variant, member);

			return out;
		}

		/**
		 * Unwrap a callback for a specific {@link Member} from a "Custom ID". This
		 * method will retrieve data from
		 * {@link #wrapInteraction(String, Member, String)}.
		 * 
		 * @param <T>       instance of {@link GenericInteractionCreateEvent}
		 * @param event     - event to unwrap
		 * @param unwrapped - consumer that accepts the unwrapped data
		 * @return Returns {@code true} if unwrapping was successful, {@code false}
		 *         otherwise
		 * @throws IllegalArgumentException If the provided {@code event}'s
		 *                                  {@link InteractionType} is not
		 *                                  {@link InteractionType#COMPONENT} or
		 *                                  {@link InteractionType#MODAL_SUBMIT}
		 * @see #wrapInteraction(String, Member, String)
		 */
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

			// Only work when split data contains at least 2 values
			if (split.length < 2)
				return false;

			String primaryID = split[0];
			String memberID = split[1];
			boolean hasVar = split.length == 3;

			// Validate that primary ID not null or blank
			if (primaryID == null || primaryID.isBlank())
				return false;

			// Validate that member ID is between 17 and 20 digits
			if (memberID.length() < 17 || memberID.length() > 20)
				return false;

			// Validate that IF variant is present, it is not null or blank
			if (hasVar && (split[2] == null || split[2].isBlank()))
				return false;

			Optional<String> variant = hasVar ? Optional.of(split[2]) : Optional.empty();

			event.getGuild()
					// Find guild member by ID
					.retrieveMemberById(memberID)
					// If not found, map to null
					.onErrorMap(e -> null)
					// Get member or null
					.queue(unwrappedMember -> {
						logger.debug("Unwrapped Interaction {}:{} for {}", id, variant.orElse("null"), unwrappedMember);
						unwrapped.accept(primaryID, Optional.ofNullable(unwrappedMember), variant);
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
}
package net.foxgenesis.customjail.jail;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.foxgenesis.customjail.database.Warning;
import net.foxgenesis.customjail.jail.event.IJailEventBus;
import net.foxgenesis.customjail.time.CustomTime;
import net.foxgenesis.customjail.time.UnixTimestamp;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

public interface IJailSystem {

	/**
	 * Jail a {@link Member} for a specific amount of time.
	 * 
	 * @param member      - member to jail
	 * @param moderator   - moderator that is jailing the member
	 * @param time        - duration of the jail
	 * @param reason      - (optional) reason for the jail
	 * @param addWarning  - should this jail give the member an active warning
	 * @param jailDetails - callback with the4 details of the jailing
	 * @param e           - error handler
	 */
	void jail(@NotNull Member member, @NotNull Member moderator, @NotNull CustomTime time, @Nullable String reason,
			boolean addWarning, @NotNull Consumer<JailDetails> jailDetails, @NotNull ErrorHandler<InternalException> e);

	/**
	 * Unjail a {@link Member}.
	 * 
	 * @param member    - member to unjail
	 * @param moderator - moderator that is unjailing the member
	 * @param reason    - optional reason
	 * @param success   - success handler
	 * @param err       - error handler
	 */
	void unjail(@NotNull Member member, @NotNull Optional<Member> moderator, @NotNull Optional<String> reason,
			@NotNull Runnable success, @NotNull ErrorHandler<InternalException> err);

	/**
	 * Start the jail timer for a {@link Member}.
	 * 
	 * @param member    - member to start jail timer for
	 * @param moderator - (optional) moderator who started it
	 * @param reason    - (optional) reason for starting timer
	 * @param timeLeft  - consumer for a string displaying how much time is left
	 * @param err       - error handler if something went wrong
	 */
	void startJailTimer(@NotNull Member member, @NotNull Optional<Member> moderator, @NotNull Optional<String> reason,
			@NotNull Consumer<UnixTimestamp> timeLeft, @NotNull ErrorHandler<InternalException> err);

	/**
	 * Fire a {@link Member}'s warning timer.
	 * <p>
	 * This method will decrease the member's warning level by one and reschedule
	 * the timer if the new level is greater than zero.
	 * </p>
	 * 
	 * @param member     - member to fire timer for
	 * @param moderator  - (optional) moderator who started it
	 * @param reason     - (optional) reason for firing timer
	 * @param oldTrigger - (optional) old warning timer
	 * @param success    - success handler
	 * @param err        - error handler
	 */
	void decreaseWarningLevel(@NotNull Member member, @NotNull Optional<Member> moderator,
			@NotNull Optional<String> reason, @NotNull BiConsumer<Integer, Integer> newLevel,
			@NotNull ErrorHandler<InternalException> err);

	/**
	 * Check if a {@link Member} is jailed.
	 * 
	 * @param member - member to check
	 * 
	 * @return Returns {@code true} if the specified member is jailed
	 */
	boolean isJailed(@NotNull Member member);

	/**
	 * Check if a {@link Member} has accepted their jail.
	 * 
	 * @param member - member to check
	 * 
	 * @return Returns {@code true} if the jail timer is running
	 */
	boolean isJailTimerRunning(@NotNull Member member);

	/**
	 * Check how much time is left on a {@link Member}'s punishment.
	 * 
	 * @param member - member to check
	 * 
	 * @return Returns an {@link Optional} containing a {@link Duration}
	 *         representing the remaining jail time. Otherwise an empty result.
	 */
	@NotNull
	Optional<Duration> getRemainingJailTime(@NotNull Member member);

	/**
	 * Check how much time is left on a {@link Member}'s warning timer.
	 * 
	 * @param member - member to check
	 * 
	 * @return Returns an {@link Optional} of {@link UnixTimestamp} for when the
	 *         warning timer will fire.
	 */
	@NotNull
	Optional<UnixTimestamp> getWarningEndTimestamp(@NotNull Member member);

	/**
	 * Check how much time is left on a {@link Member}'s punishment.
	 * 
	 * @param member - member to check
	 * 
	 * @return Returns an {@link Optional} of {@link UnixTimestamp} for when the
	 *         jail timer will fire.
	 */
	@NotNull
	Optional<UnixTimestamp> getJailEndTimestamp(@NotNull Member member);

	/**
	 * Get the details of a {@link Member}'s jailing.
	 * 
	 * @param member - member to check
	 * 
	 * @return Returns an {@link Optional} containing the member's
	 *         {@link JailDetails} otherwise an empty {@link Optional}
	 * 
	 * @throws InternalException Thrown if the internal scheduler throws an
	 *                           exception while retrieving data
	 */
	@NotNull
	Optional<JailDetails> getJailDetails(@NotNull Member member) throws InternalException;

	/**
	 * Check if a {@link Member} has a warning timer running.
	 * 
	 * @param member - member to check
	 * 
	 * @return Returns {@code true} if there is a warning timer running
	 */
	boolean isWarningTimerRunning(@NotNull Member member);

	/**
	 * Get the warning level of a {@link Member}.
	 * 
	 * @param member - member to get warning level for
	 * 
	 * @return Returns the member's warning level. Otherwise {@code -1}
	 */
	int getWarningLevelForMember(@NotNull Member member);

	/**
	 * Get the total amount of warnings a {@link Member} has.
	 * 
	 * @param member - member to get warnings for
	 * 
	 * @return Returns the total amount of warnings
	 */
	int getTotalWarnings(@NotNull Member member);

	/**
	 * NEED_JAVADOC
	 * 
	 * @param member
	 * @param moderator
	 * @param reason
	 * @param active
	 * 
	 * @return
	 */
	int addWarningForMember(@NotNull Member member, @NotNull Member moderator, @NotNull Optional<String> reason,
			boolean active);

	/**
	 * NEED_JAVADOC
	 * 
	 * @param member
	 * @param itemsPerPage
	 * @param page
	 * 
	 * @return
	 */
	@NotNull
	Warning[] getWarningsPageForMember(@NotNull Member member, int itemsPerPage, int page);

	/**
	 * NEED_JAVADOC
	 * 
	 * @param guild
	 * @param case_id
	 * 
	 * @return
	 */
	@NotNull
	Optional<Warning> getWarning(@NotNull Guild guild, int case_id);

	/**
	 * Delete a warning from the database based on the specified {@code case id}.
	 * 
	 * @param guild     - {@link Guild} this request came from
	 * @param case_id   - case id of the warning
	 * @param moderator - (optional) moderator that performed this action
	 * @param reason    - (optional) reason for deletion
	 * 
	 * @return Returns {@code true} if the specified warning was removed and
	 *         {@code false} otherwise
	 */
	boolean deleteWarning(@NotNull Guild guild, int case_id, @NotNull Optional<Member> moderator,
			@NotNull Optional<String> reason);

	/**
	 * Delete all warnings for a {@link Member}.
	 * 
	 * @param member    - member to delete warnings for
	 * @param moderator - (optional) moderator that performed this action
	 * @param reason    - (optional) reason for deletion
	 * 
	 * @return Returns {@code true} if all warnings were deleted. {@code false}
	 *         otherwise.
	 */
	boolean deleteWarnings(@NotNull Member member, @NotNull Optional<Member> moderator,
			@NotNull Optional<String> reason);

	/**
	 * NEED_JAVADOC
	 * 
	 * @param guild
	 * @param case_id
	 * @param moderator
	 * @param newReason
	 * @param reason
	 * 
	 * @return
	 */
	boolean updateWarningReason(@NotNull Guild guild, int case_id, @NotNull Optional<Member> moderator,
			@NotNull String newReason, @NotNull Optional<String> reason);

	/**
	 * Refresh a {@link Member} in the system.
	 * 
	 * @param member - member to refresh
	 */
	public void refreshMember(@NotNull Member member);

	/**
	 * NEED_JAVADOC
	 * 
	 * @param guild
	 * 
	 * @return
	 * 
	 * @throws InternalException
	 */
	@NotNull
	CompletableFuture<Void> refreshAllMembers(@NotNull Guild guild) throws InternalException;

	/**
	 * Check if the specified {@code case ID} is valid.
	 * 
	 * @param case_id - id to check
	 * 
	 * @return Returns {@code true} if the case id is valid
	 */
	default boolean isValidCaseID(int case_id) {
		return case_id >= 0;
	}

	/**
	 * Get the default reason for jail system operations.
	 * 
	 * @return Returns the reason to be used when no reason is provided during jail
	 *         operations
	 */
	@NotNull
	default String getDefaultReason() {
		return "No Reason Provided";
	}

	/**
	 * Get the required footer for all embeds related to this system.
	 * 
	 * @return Returns a string containing the text to set on all embeds
	 */
	@NotNull
	default String getEmbedFooter() {
		return "via Custom Jail";
	}

	/**
	 * Get the event manager used fire system events.
	 * 
	 * @return Returns the {@link IJailEventBus} used to handle system events
	 */
	@NotNull
	IJailEventBus getEventManager();

	@FunctionalInterface
	public static interface ErrorHandler<E extends Exception> {
		public static <E extends Exception> ErrorHandler<E> identity() {
			return err -> { throw err; };
		}

		void accept(E error) throws E;
	}
}

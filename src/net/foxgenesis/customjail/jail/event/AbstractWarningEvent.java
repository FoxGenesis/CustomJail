package net.foxgenesis.customjail.jail.event;

import java.util.Objects;
import java.util.Optional;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.jail.WarningDetails;

public abstract class AbstractWarningEvent implements IJailEvent {

	private final WarningDetails details;
	private final Optional<Member> mod;
	private final Optional<String> reason;

	/**
	 * Construct a new event with the specified {@link WarningDetails} with a
	 * possible moderator and reason.
	 * 
	 * @param details   - the warning this event was fired for
	 * @param moderator - (optional) moderator that initiated this event
	 * @param reason    - (optional) reason for initiating this event
	 * 
	 * @implSpec The passed {@link WarningDetails} <b>should be a fresh copy from
	 *           the database</b> containing all the new information
	 */
	public AbstractWarningEvent(WarningDetails details, Optional<Member> moderator, Optional<String> reason) {
		this.details = Objects.requireNonNull(details);
		this.mod = Objects.requireNonNull(moderator);
		this.reason = Objects.requireNonNull(reason);
	}

	/**
	 * Get the {@link Guild} this event was fired from.
	 * 
	 * @return returns the {@link Guild} linked to the event
	 */
	@Override
	public Guild getGuild() {
		return details.member().getGuild();
	}

	/**
	 * Get the {@link WarningDetails Warning} this event was fired for.
	 * 
	 * @return Returns the {@link WarningDetails} containing the <b>new</b> warning
	 *         data
	 */
	public WarningDetails getDetails() {
		return details;
	}

	/**
	 * Get the {@link Member} that initiated this event if present.
	 * 
	 * @return Returns a possible {@link Member} that was the cause of this event
	 */
	public Optional<Member> getModerator() {
		return mod;
	}

	/**
	 * Get the reason specified by either the moderator or system for firing this
	 * event.
	 * 
	 * @return Returns a possible string that contains the reason why this warning
	 *         was added/removed/updated/etc
	 */
	public Optional<String> getReason() {
		return reason;
	}
}

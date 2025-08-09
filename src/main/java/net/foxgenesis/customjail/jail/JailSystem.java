package net.foxgenesis.customjail.jail;

import java.util.Date;
import java.util.Optional;

import org.springframework.lang.Nullable;

import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.jail.exception.AlreadyJailedException;
import net.foxgenesis.customjail.jail.exception.NotJailedException;
import net.foxgenesis.customjail.util.CustomTime;

/**
 * Interface defining common functions of a jail system
 * 
 * @author Ashley
 */
public interface JailSystem extends WarningSystem {

	/**
	 * Check if a {@code member} is jailed in a {@code guild}.
	 * 
	 * @param guild  - guild to check in
	 * @param member - member to check
	 * @return Returns {@code true} if the specified user is jailed in the specified
	 *         guild
	 * @see #isJailed(Member)
	 */
	boolean isJailed(long guild, long member);

	/**
	 * Check if a {@link Member}'s jail timer is running
	 * 
	 * @param member - member to check
	 * @return Returns {@code true} if the specified member's jail timer is running
	 */
	boolean isJailTimerRunning(Member member);

	/**
	 * Check if a {@link Member} is jailed.
	 * 
	 * @param member - member to check
	 * @return Returns {@code true} if the member is jailed.
	 * @see #isJailed(long, long)
	 */
	default boolean isJailed(Member member) {
		return isJailed(member.getGuild().getIdLong(), member.getIdLong());
	}

	/**
	 * Jail a {@link Member} for a specified {@code time}.
	 * 
	 * @param member     - member to jail
	 * @param moderator  - moderator that is jailing the member
	 * @param time       - duration of the jail
	 * @param reason     - (optional) reason for being jailed
	 * @param addWarning - should a warning be added to the member for this jailing
	 * @param anonymous  - should the jailing be listed as anonymous
	 * @throws AlreadyJailedException Thrown if the specified member is already
	 *                                jailed
	 * @see #unjail(Member, Member, String)
	 * @see #unjail(long, long, Member, String)
	 * @see #startJailTimer(Member, Member, String)
	 */
	void jail(Member member, Member moderator, CustomTime time, @Nullable String reason, boolean addWarning,
			boolean anonymous) throws AlreadyJailedException;

	/**
	 * Unjail a member in a guild
	 * 
	 * @param guild     - guild to unjail the member in
	 * @param member    - member to unjail
	 * @param moderator - (optional) moderator that unjailing the member
	 * @param reason    - (optional) reason for the member to be unjailed
	 * @throws NotJailedException Thrown if the specified member is not jailed
	 * 
	 * @see #jail(Member, Member, CustomTime, String, boolean)
	 * @see #unjail(Member, Member, String)
	 * @see #startJailTimer(Member, Member, String)
	 */
	void unjail(long guild, long member, @Nullable Member moderator, @Nullable String reason) throws NotJailedException;

	/**
	 * Unjail a {@link Member}.
	 * 
	 * @param member    - member to unjail
	 * @param moderator - (optional) moderator that unjailing the member
	 * @param reason    - (optional) reason for the member to be unjailed
	 * @throws NotJailedException Thrown if the specified member is not jailed
	 * 
	 * @see #jail(Member, Member, CustomTime, String, boolean)
	 * @see #unjail(long, long, Member, String)
	 * @see #startJailTimer(Member, Member, String)
	 */
	default void unjail(Member member, @Nullable Member moderator, @Nullable String reason) throws NotJailedException {
		unjail(member.getGuild().getIdLong(), member.getIdLong(), moderator, reason);
	}

	/**
	 * Start the jail timer for a {@link Member}.
	 * 
	 * @param member    - member to start the jail timer for
	 * @param moderator - (optional) moderator starting the timer
	 * @param reason    - (optional) reason for the timer to be started
	 * @throws NotJailedException Thrown if the specified member is not jailed
	 * 
	 * @see #jail(Member, Member, CustomTime, String, boolean)
	 * @see #unjail(Member, Member, String)
	 * @see #unjail(long, long, Member, String)
	 */
	Date startJailTimer(Member member, @Nullable Member moderator, @Nullable String reason) throws NotJailedException;

	/**
	 * Get the details for a {@link Member}s jail.
	 * 
	 * @param member - member to get details for
	 * @return {@link JailDetails} containing the details of the jail
	 * @throws NotJailedException Thrown if the specified member is not jailed
	 */
	JailDetails getJailDetails(Member member) throws NotJailedException;

	/**
	 * Attempt to fix a {@link Member}'s jail/warning timers and warning roles.
	 * 
	 * @param member - member to fix
	 */
	void fixMember(Member member);

	Optional<String> getJailEndTimestamp(Member member);

	String[] getJailTimings();
}

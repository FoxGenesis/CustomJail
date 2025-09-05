package net.foxgenesis.customjail.jail;

import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.database.warning.Warning;

/**
 * Interface defining functions of a warning system
 * 
 * @author Ashley
 */
public interface WarningSystem {
	/**
	 * Get the total amount of warnings for the specified {@link Member}.
	 * 
	 * @param member - member to get warnings for
	 * @return Returns the total amount of warnings the specified member has
	 */
	int getTotalWarnings(Member member);

	/**
	 * Get the warning level for a member in a guild
	 * 
	 * @param guild  - guild to check in
	 * @param member - member to check for
	 * @return Returns the warning level for the member in the specified guild
	 * 
	 * @see #getWarningLevel(Member)
	 */
	int getWarningLevel(long guild, long member);

	/**
	 * Get the warning level for a member
	 * 
	 * @param member - member to check for
	 * @return Returns the warning level for the specified member
	 * @see #getWarningLevel(long, long)
	 */
	default int getWarningLevel(Member member) {
		return getWarningLevel(member.getGuild().getIdLong(), member.getIdLong());
	}

	void deleteWarning(Warning warning, @Nullable Member moderator, @Nullable String reason);

	default void deleteWarningById(long id, @Nullable Member moderator, @Nullable String reason) {
		deleteWarning(findWarning(id).orElseThrow(), moderator, reason);
	}

	Warning addWarning(Member member, Member moderator, @Nullable String reason, boolean active);

	Optional<Warning> findWarning(long id);

	Page<Warning> getWarningPage(Member member, Pageable pageable);

	void decreaseWarningLevel(Member member, Member moderator, @Nullable String reason);

	void decreaseWarningLevel(long guild, long member, Member moderator, @Nullable String reason);

	default Warning updateWarningReason(long id, Member moderator, String newReason, @Nullable String reason)
			throws NoSuchElementException {
		return updateWarningReason(findWarning(id).orElseThrow(), moderator, newReason, reason);
	}

	Warning updateWarningReason(Warning warning, Member moderator, String newReason, @Nullable String reason);

	void clearWarnings(Member member, Member moderator, String reason);

	void clearWarnings(Guild guild);
}

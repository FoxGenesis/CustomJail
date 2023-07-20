package net.foxgenesis.customjail.database;

import java.util.Optional;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

public interface IWarningDatabase {

	/**
	 * Get the warning level of a {@link Member}.
	 * 
	 * @param member - member to get warning level for
	 * 
	 * @return Returns the member's warning level. Otherwise {@code -1}
	 */
	int getWarningLevelForMember(Member member);

	Optional<Warning> getWarning(Guild guild, int case_id);

	int getTotalWarnings(Member member);

	Warning[] getWarningsPageForMember(Member member, int itemsPerPage, int page);

	Warning[] getWarningsPageForMember(long guildID, long memberID, int itemsPerPage, int page);

	int addWarningForMember(Member member, Member moderator, String reason, boolean active);

	int decreaseAndGetWarningLevel(Member member);

	/**
	 * Delete a warning from the database based on the specified {@code case id}.
	 * 
	 * @param guild   - {@link Guild} this request came from
	 * @param case_id - case id of the warning
	 * 
	 * @return Returns {@code true} if the specified warning was removed and
	 *         {@code false} otherwise
	 */
	boolean deleteWarning(Guild guild, int case_id);

	boolean deleteWarnings(Member member);

	boolean updateWarningReason(Guild guild, int case_id, String reason);

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
}

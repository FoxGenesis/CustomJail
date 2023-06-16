package net.foxgenesis.customjail.database;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

public interface IWarningDatabase {

	int getWarningLevelForMember(Member member);
	
	int getTotalWarnings(Member member);
	
	Warning[] getWarningsPageForMember(Member member, int itemsPerPage, int page);
	
	Warning[] getWarningsPageForMember(long guildID, long memberID, int itemsPerPage, int page);
	
	int addWarningForMember(Member member, Member moderator, String reason, boolean active);
	
	int decreaseAndGetWarningLevel(Member member);
	
	boolean deleteWarning(Guild guild, int case_id);
	
	boolean deleteWarnings(Member member);

	boolean updateWarningReason(Guild guild, int case_id, String reason);
}

package net.foxgenesis.customjail.jail;

import java.util.Optional;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.database.Warning;

public record WarningDetails(Member member, Optional<Member> mod, Optional<String> reason, long timestamp, int caseID,
		boolean active) {

	public static WarningDetails fromData(JDA jda, long guildID, long memberID, String reason, String moderator,
			long time, int caseID, boolean active) {
		return Optional.ofNullable(jda.getGuildById(guildID)).map(guild -> {
			Member member = guild.getMemberById(memberID);
			Member mod = guild.getMemberById(moderator);

			if (member == null)
				return null;

			return new WarningDetails(member, Optional.ofNullable(mod), Optional.ofNullable(reason), time, caseID,
					active);
		}).orElseThrow(() -> new NullPointerException("Unable to find member or guild"));
	}

	public static WarningDetails fromData(JDA jda, Warning warning) {
		return fromData(jda, warning.guildID(), warning.memberID(), warning.reason(), warning.moderator(),
				warning.time(), warning.caseId(), warning.active());
	}
}

package net.foxgenesis.customjail.database.warning;

import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

@Repository
public interface WarningDatabase extends JpaRepository<Warning, Long> {
	
	void deleteByGuild(long guild);
	
	void deleteByGuildAndMember(long guild, long member);
	
	Page<Warning> findAllByGuildAndMember(long guild, long member, Pageable pageable);

	int countByGuildAndMember(long guild, long member);

	int countByGuildAndMemberAndActiveIsTrue(long guild, long member);

	Set<Warning> findByGuildAndMember(long guild, long member);

	Set<Warning> findByGuildAndMemberAndActiveIsTrue(long guild, long member);
	
	@Modifying
	@Transactional
	@NativeQuery("update warning w set w.active = false where w.guild = :guild and w.member = :member and w.active = true order by w.time ASC limit 1")
	void decreaseWarningLevel(@Param(value = "guild") long guild, @Param(value = "member") long member);
	
	default int getWarningLevel(Member member) {
		return getWarningLevel(member.getGuild().getIdLong(), member.getIdLong());
	}

	default int getWarningLevel(long guild, long member) {
		return countByGuildAndMemberAndActiveIsTrue(guild, member);
	}

	default  int getWarningCount(Member member) {
		return getWarningCount(member.getGuild().getIdLong(), member.getIdLong());
	}

	default int getWarningCount(long guild, long member) {
		return countByGuildAndMember(guild, member);
	}

	default Set<Warning> getActiveWarnings(Member member) {
		return getActiveWarnings(member.getGuild().getIdLong(), member.getIdLong());
	}

	default Set<Warning> getActiveWarnings(long guild, long member) {
		return findByGuildAndMemberAndActiveIsTrue(guild, member);
	}

	default Warning addWarning(long guild, long member, long moderator, String reason, boolean active) {
		return save(new Warning(guild, member, moderator, reason, active));
	}

	default Warning addWarning(Member member, Member moderator, String reason, boolean active) {
		return save(new Warning(member, moderator, reason, active));
	}
	
	default Page<Warning> findAllByMember(Member member, Pageable pageable) {
		return findAllByGuildAndMember(member.getGuild().getIdLong(), member.getIdLong(), pageable);
	}
	
	default void deleteByGuild(Guild guild) {
		deleteByGuild(guild.getIdLong());
	}
	
	default void deleteByMember(Member member) {
		deleteByGuildAndMember(member.getGuild().getIdLong(), member.getIdLong());
	}
}

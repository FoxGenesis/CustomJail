package net.foxgenesis.customjail.database.warning;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.springJDA.annotation.Snowflake;

@Data
@NoArgsConstructor

@Entity
@Table(indexes = @Index(columnList = "guild, member"))
public class Warning {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Snowflake
	@Column(updatable = false, nullable = false)
	private long guild;

	@Snowflake
	@Column(updatable = false, nullable = false)
	private long member;

	@Snowflake
	@Column(updatable = false, nullable = false)
	private long moderator;

	@Column(length = 500)
	private String reason;

	@CreationTimestamp
	@Column(updatable = false, nullable = false)
	private Instant time;

	@Column(nullable = false)
	private boolean active;

	public Warning(long guild, long member, long moderator, String reason, boolean active) {
		this();
		setGuild(guild);
		setMember(member);
		setModerator(moderator);
		setReason(reason);
		setActive(active);
	}

	public Warning(Member member, Member moderator, String reason, boolean active) {
		this(member.getGuild().getIdLong(), member.getIdLong(), moderator.getIdLong(), reason, active);
	}
	
	public Warning copy() {
		return new Warning(guild, member, moderator, reason, active);
	}
}

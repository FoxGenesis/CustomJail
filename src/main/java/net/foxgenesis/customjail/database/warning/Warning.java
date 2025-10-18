package net.foxgenesis.customjail.database.warning;

import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.util.Utilities;
import net.foxgenesis.springJDA.annotation.Snowflake;

@Entity
@Table(indexes = @Index(columnList = "guild, member"))
public class Warning {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Snowflake
	@Column(nullable = false)
	private long guild;

	@Snowflake
	@Column(nullable = false)
	private long member;

	@Snowflake
	@Column(nullable = false)
	private long moderator;

	@Column(length = 500)
	private String reason;

	@CreationTimestamp
	@Column(nullable = false)
	private Instant time;

	@Column(nullable = false)
	private boolean active;

	public Warning() {
	}

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
	
	public Warning(Warning other) {
		this(other.guild, other.member ,other.moderator, other.reason, other.active);
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public long getGuild() {
		return guild;
	}

	public void setGuild(long guild) {
		this.guild = guild;
	}

	public void setGuild(Guild guild) {
		setGuild(guild.getIdLong());
	}

	public long getMember() {
		return member;
	}

	public void setMember(long member) {
		this.member = member;
	}

	public void setMember(Member member) {
		setMember(member.getIdLong());
	}

	public long getModerator() {
		return moderator;
	}

	public void setModerator(long moderator) {
		this.moderator = moderator;
	}

	public void setModerator(Member moderator) {
		setModerator(moderator.getIdLong());
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = Utilities.nullIfBlank(reason);
	}

	public Instant getTime() {
		return time;
	}

	public void setTime(Instant time) {
		this.time = time;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}
	
	public Warning copy() {
		return new Warning(this);
	}

	@Override
	public int hashCode() {
		return Objects.hash(active, guild, id, member, moderator, reason, time);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Warning other = (Warning) obj;
		return active == other.active && guild == other.guild && id == other.id && member == other.member
				&& moderator == other.moderator && Objects.equals(reason, other.reason)
				&& Objects.equals(time, other.time);
	}

	@Override
	public String toString() {
		return "Warning [id=" + id + ", guild=" + guild + ", member=" + member + ", moderator=" + moderator
				+ ", reason=" + reason + ", time=" + time + ", active=" + active + "]";
	}
}

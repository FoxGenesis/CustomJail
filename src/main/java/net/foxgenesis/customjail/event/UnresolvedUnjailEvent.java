package net.foxgenesis.customjail.event;

import java.util.Optional;

import org.springframework.context.ApplicationEvent;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

public class UnresolvedUnjailEvent extends ApplicationEvent {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1010081228889360734L;
	private final long member;
	private final Member moderator;
	private final String reason;

	public UnresolvedUnjailEvent(Guild guild, long member, Member moderator, String reason) {
		super(guild);
		this.member = member;
		this.moderator = moderator;
		this.reason = reason;
	}

	public Guild getGuild() {
		return (Guild) super.getSource();
	}

	public long getMember() {
		return member;
	}

	public Optional<Member> getModerator() {
		return Optional.ofNullable(moderator);
	}

	public Optional<String> getReason() {
		return Optional.ofNullable(reason);
	}
}

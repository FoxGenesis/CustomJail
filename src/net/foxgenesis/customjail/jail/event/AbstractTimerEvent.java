package net.foxgenesis.customjail.jail.event;

import java.util.Objects;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

public abstract class AbstractTimerEvent implements IJailEvent{

	private final Member member;
	
	public AbstractTimerEvent(Member member) {
		this.member = Objects.requireNonNull(member);
	}
	
	@Override
	public Guild getGuild() {
		return member.getGuild();
	}
	
	public Member getMember() {
		return member;
	}
}

package net.foxgenesis.customjail.jail.event;

import java.util.Objects;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.api.entities.Member;

public abstract class AbstractMemberJailEvent extends AbstractJailEvent {

	private final Member member;

	public AbstractMemberJailEvent(Member member, Optional<Member> mod, Optional<String> reason) {
		super(Objects.requireNonNull(member).getGuild(), mod, reason);
		this.member = member;
	}

	@NotNull
	public Member getMember() {
		return member;
	}
}

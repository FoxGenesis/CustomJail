package net.foxgenesis.customjail.jail.event.impl;

import java.util.Optional;

import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.jail.event.AbstractMemberJailEvent;

public class MemberUnjailEvent extends AbstractMemberJailEvent {

	public MemberUnjailEvent(Member member, Optional<Member> mod, Optional<String> reason) {
		super(member, mod, reason);
	}
}

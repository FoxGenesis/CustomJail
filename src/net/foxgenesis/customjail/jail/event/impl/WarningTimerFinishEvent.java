package net.foxgenesis.customjail.jail.event.impl;

import java.util.Objects;
import java.util.Optional;

import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.jail.event.AbstractTimerEvent;

public class WarningTimerFinishEvent extends AbstractTimerEvent {
	private final int oldLevel;
	private final int newLevel;
	private final Optional<Member> mod;

	public WarningTimerFinishEvent(Member member, Optional<Member> mod, int oldLevel, int newLevel) {
		super(member);
		this.oldLevel = oldLevel;
		this.newLevel = newLevel;
		this.mod = Objects.requireNonNull(mod);
	}

	public int getOldWarningLevel() {
		return oldLevel;
	}

	public int getNewWarningLevel() {
		return newLevel;
	}

	public boolean isForced() {
		return mod.isPresent();
	}

	public Optional<Member> getModerator() {
		return mod;
	}
}

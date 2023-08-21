package net.foxgenesis.customjail.jail.event.impl;

import java.time.Instant;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;

import net.foxgenesis.customjail.jail.JailDetails;
import net.foxgenesis.customjail.jail.event.AbstractMemberJailEvent;
import net.foxgenesis.customjail.time.CustomTime;

public class MemberJailEvent extends AbstractMemberJailEvent {

	private final JailDetails details;

	public MemberJailEvent(JailDetails details) {
		super(details.member(), Optional.ofNullable(details.moderator()), Optional.ofNullable(details.reason()));
		this.details = details;
	}

	public boolean withActiveWarning() {
		return details.caseid() != -1;
	}

	public Optional<Integer> getCaseID() {
		return Optional.of(details.caseid()).filter(id -> id != -1);
	}

	public CustomTime getDuration() {
		return details.duration();
	}

	@NotNull
	public Instant getTimeStamp() {
		return Instant.ofEpochMilli(details.timestamp());
	}
}

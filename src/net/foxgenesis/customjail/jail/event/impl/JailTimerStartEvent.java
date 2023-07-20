package net.foxgenesis.customjail.jail.event.impl;

import java.util.Date;
import java.util.Objects;
import java.util.Optional;

import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.jail.JailDetails;
import net.foxgenesis.customjail.jail.event.AbstractTimerEvent;

public class JailTimerStartEvent extends AbstractTimerEvent {
	private final JailDetails details;
	private final Date endDate;
	private final Optional<Member> moderator;
	private final Optional<String> reason;
	
	public JailTimerStartEvent(JailDetails details, Optional<Member> moderator, Optional<String> reason, Date endDate) {
		super(Objects.requireNonNull(details).member());
		this.details = details;
		this.endDate = Objects.requireNonNull(endDate);
		this.moderator = Objects.requireNonNull(moderator);
		this.reason = Objects.requireNonNull(reason);
	}

	public JailDetails getDetails() {
		return details;
	}

	public Date getEndDate() {
		return endDate;
	}

	public Optional<Member> getModerator() {
		return moderator;
	}
	
	public Optional<String> getStartReason() {
		return reason;
	}
}

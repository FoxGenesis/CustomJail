package net.foxgenesis.customjail.jail.event.impl;

import java.util.Date;
import java.util.Objects;

import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.jail.event.AbstractTimerEvent;

public class WarningTimerStartEvent extends AbstractTimerEvent {
	private final Date endDate;
	
	public WarningTimerStartEvent(Member member, Date endDate) {
		super(member);
		this.endDate = Objects.requireNonNull(endDate);
	}

	public Date getEndDate() {
		return endDate;
	}
}

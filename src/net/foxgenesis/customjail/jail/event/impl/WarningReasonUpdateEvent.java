package net.foxgenesis.customjail.jail.event.impl;

import java.util.Objects;
import java.util.Optional;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.database.Warning;
import net.foxgenesis.customjail.jail.WarningDetails;
import net.foxgenesis.customjail.jail.event.AbstractWarningEvent;

public class WarningReasonUpdateEvent extends AbstractWarningEvent {

	private final Optional<String> oldReason;
	private final Optional<String> newReason;

	public WarningReasonUpdateEvent(JDA jda, Warning warning, Optional<Member> mod, Optional<String> reason,
			Optional<String> oldReason) {
		this(WarningDetails.fromData(jda, warning), mod, reason, oldReason);
	}

	public WarningReasonUpdateEvent(WarningDetails details, Optional<Member> mod, Optional<String> reason,
			Optional<String> oldReason) {
		super(details, mod, reason);
		this.oldReason = Objects.requireNonNull(oldReason);
		this.newReason = getDetails().reason();
	}

	public Optional<String> getOldReason() {
		return oldReason;
	}

	public Optional<String> getNewReason() {
		return newReason;
	}
}

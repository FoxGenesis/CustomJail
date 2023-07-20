package net.foxgenesis.customjail.jail.event.impl;

import java.util.Optional;

import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.jail.WarningDetails;
import net.foxgenesis.customjail.jail.event.AbstractWarningEvent;

public class WarningRemovedEvent extends AbstractWarningEvent {

	public WarningRemovedEvent(WarningDetails details, Optional<Member> moderator, Optional<String> reason) {
		super(details, moderator, reason);
	}
}

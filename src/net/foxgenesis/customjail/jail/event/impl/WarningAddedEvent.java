package net.foxgenesis.customjail.jail.event.impl;

import net.foxgenesis.customjail.jail.WarningDetails;
import net.foxgenesis.customjail.jail.event.AbstractWarningEvent;

public class WarningAddedEvent extends AbstractWarningEvent {

	public WarningAddedEvent(WarningDetails details) {
		super(details, details.mod(), details.reason());
	}
}

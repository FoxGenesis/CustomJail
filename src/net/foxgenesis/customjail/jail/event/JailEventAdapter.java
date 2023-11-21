package net.foxgenesis.customjail.jail.event;

import net.foxgenesis.customjail.jail.event.impl.JailTimerStartEvent;
import net.foxgenesis.customjail.jail.event.impl.MemberJailEvent;
import net.foxgenesis.customjail.jail.event.impl.MemberLeaveWhileJailedEvent;
import net.foxgenesis.customjail.jail.event.impl.MemberUnjailEvent;
import net.foxgenesis.customjail.jail.event.impl.WarningAddedEvent;
import net.foxgenesis.customjail.jail.event.impl.WarningReasonUpdateEvent;
import net.foxgenesis.customjail.jail.event.impl.WarningRemovedEvent;
import net.foxgenesis.customjail.jail.event.impl.WarningTimerFinishEvent;
import net.foxgenesis.customjail.jail.event.impl.WarningTimerStartEvent;

public class JailEventAdapter implements JailEventListener {

	@Override
	public void onMemberJail(MemberJailEvent event) {}

	@Override
	public void onMemberUnjail(MemberUnjailEvent event) {}

	@Override
	public void onJailTimerStart(JailTimerStartEvent event) {}

	@Override
	public void onWarningTimerStart(WarningTimerStartEvent event) {}

	@Override
	public void onWarningTimerFinish(WarningTimerFinishEvent event) {}

	@Override
	public void onWarningAdded(WarningAddedEvent event) {}

	@Override
	public void onWarningRemove(WarningRemovedEvent event) {}

	@Override
	public void onWarningReasonUpdated(WarningReasonUpdateEvent event) {}

	@Override
	public void onMemberLeaveWhileJailed(MemberLeaveWhileJailedEvent event) {}
}

package net.foxgenesis.customjail.jail.event;

import net.foxgenesis.customjail.jail.event.impl.JailTimerStartEvent;
import net.foxgenesis.customjail.jail.event.impl.MemberJailEvent;
import net.foxgenesis.customjail.jail.event.impl.MemberUnjailEvent;
import net.foxgenesis.customjail.jail.event.impl.WarningAddedEvent;
import net.foxgenesis.customjail.jail.event.impl.WarningReasonUpdateEvent;
import net.foxgenesis.customjail.jail.event.impl.WarningRemovedEvent;
import net.foxgenesis.customjail.jail.event.impl.WarningTimerFinishEvent;
import net.foxgenesis.customjail.jail.event.impl.WarningTimerStartEvent;

public interface JailEventListener {

	public void onMemberJail(MemberJailEvent event);
	
	public void onMemberUnjail(MemberUnjailEvent event);
	
	public void onJailTimerStart(JailTimerStartEvent event);
	
	public void onWarningTimerStart(WarningTimerStartEvent event);
	
	public void onWarningTimerFinish(WarningTimerFinishEvent event);
	
	public void onWarningAdded(WarningAddedEvent event);
	
	public void onWarningRemove(WarningRemovedEvent event);
	
	public void onWarningReasonUpdated(WarningReasonUpdateEvent event);
}

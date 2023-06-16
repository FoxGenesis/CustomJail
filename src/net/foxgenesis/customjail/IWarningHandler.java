package net.foxgenesis.customjail;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.InteractionHook;

public interface IWarningHandler {
	public void deleteWarning(InteractionHook hook, Member moderator, int case_id);
	
	public void updateWarningReason(InteractionHook hook, Member moderator, String reason);
}

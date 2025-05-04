package net.foxgenesis.customjail.event;

import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.watame.util.discord.ModeratorActionEvent;

public abstract class JailEvent extends ModeratorActionEvent  {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3879353074417230492L;

	public JailEvent(Member member, Member moderator, String reason, int color, String title) {
		super(member, moderator, reason, color, title);
	}
}

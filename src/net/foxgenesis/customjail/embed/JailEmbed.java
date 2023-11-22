package net.foxgenesis.customjail.embed;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.foxgenesis.customjail.CustomJailPlugin;
import net.foxgenesis.customjail.time.CustomTime;

public class JailEmbed extends ACustomEmbed {

	public JailEmbed(MessageEmbed messageEmbed) {
		super(messageEmbed);
		builder.setTitle("Jail User");
		builder.setFooter("via Custom Jail", CustomJailPlugin.EMBED_FOOTER_ICON);
	}

	public JailEmbed setMember(Member member) {
		builder.setAuthor(member.getEffectiveName(), null, member.getEffectiveAvatarUrl()).setColor(member.getColor());
		replaceField(new Field("User", member.getAsMention(), true));
		return this;
	}
	
	public JailEmbed setCurrentLevel(int currentLevel) {
		replaceField(new Field("Warning Level", "" + currentLevel, true));
		return this;
	}

	public JailEmbed setTime(CustomTime time) {
		replaceField(new Field("Duration", time.getDisplayString(), true, true));
		return this;
	}

	@Override
	protected void handleField(Field field) {}
}

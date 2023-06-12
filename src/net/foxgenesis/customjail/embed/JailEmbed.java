package net.foxgenesis.customjail.embed;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.foxgenesis.customjail.time.CustomTime;

public class JailEmbed extends ACustomEmbed {

	public JailEmbed(MessageEmbed messageEmbed) {
		super(messageEmbed);
		builder.setTitle("Jail User");
		builder.setThumbnail("https://media.tenor.com/JwnY0jHr7_MAAAAi/bonk-cat-ouch.gif");
	}

	public JailEmbed setMember(Member member) {
		builder.setAuthor(member.getEffectiveName(), null, member.getEffectiveAvatarUrl()).setColor(member.getColor());
		replaceField(new Field("User", member.getAsMention(), true));
		replaceField(new Field("User ID", member.getId(), true));
		addBlank();
		return this;
	}

	public JailEmbed setReason(String reason) {
		replaceField(new Field("Reason", reason, true));
		return this;
	}

	public JailEmbed setTime(CustomTime time) {
		replaceField(new Field("Duration", time.getDisplayString(), true, true));
		return this;
	}

	@Override
	protected void handleField(Field field) {}
}

package net.foxgenesis.customjail.event;

import java.util.Locale;
import java.util.Optional;

import org.springframework.context.MessageSource;

import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.util.CustomTime;
import net.foxgenesis.watame.util.discord.Colors;
import net.foxgenesis.watame.util.lang.LocalizedEmbedBuilder;

public class JailedEvent extends JailEvent {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4758643324688452997L;

	private final CustomTime duration;
	private final Optional<Long> caseId;

	public JailedEvent(Member member, Member moderator, CustomTime duration, String reason, Optional<Long> caseId) {
		super(member, moderator, reason, Colors.ERROR, "customjail.embed.jailed");
		this.duration = duration;
		this.caseId = caseId;
	}

	public CustomTime getDuration() {
		return duration;
	}

	public Optional<Long> getCaseId() {
		return caseId;
	}

	public String getCaseId(MessageSource source, Locale locale) {
		return getCaseId().map(i -> "" + i).orElseGet(getNA(source, locale));
	}

	@Override
	public void fillEmbed(LocalizedEmbedBuilder builder) {
		MessageSource source = builder.getMessageSource();
		Locale locale = builder.getLocale();
		
		// Row 1
		builder.addLocalizedField("customjail.embed.member", getMember().getAsMention(), true);
		builder.addLocalizedField("customjail.embed.moderator", getModerator(source, locale), true);
		builder.addLocalizedField("customjail.embed.caseid", getCaseId(source, locale), true);

		// Row 2
		builder.addLocalizedField("customjail.embed.duration", getDuration().getLocalizedDisplayString(source, locale),
				true);

		// Row 3
		builder.addLocalizedField("customjail.embed.reason", getReason(source, locale), false);
	}
}

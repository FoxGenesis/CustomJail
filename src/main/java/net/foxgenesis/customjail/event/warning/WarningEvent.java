package net.foxgenesis.customjail.event.warning;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.MessageSource;

import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.customjail.database.warning.Warning;
import net.foxgenesis.watame.util.discord.ModeratorActionEvent;

public abstract class WarningEvent extends ModeratorActionEvent {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5335964934204229688L;

	private final Warning warning;

	public WarningEvent(Member member, Member moderator, String reason, Warning warning, int color, String title) {
		super(member, moderator, reason, color, title);
		this.warning = Objects.requireNonNull(warning);
	}

	public Warning getWarning() {
		return warning;
	}

	public String getCaseId(MessageSource source, Locale locale) {
		return Optional.of(warning.getId()).filter(id -> id != -1).map(id -> "" + id).orElseGet(getNA(source, locale));
	}

	public String getWarningReason(MessageSource source, Locale locale) {
		return Optional.ofNullable(warning.getReason()).orElseGet(getDefaultReason(source, locale));
	}
}

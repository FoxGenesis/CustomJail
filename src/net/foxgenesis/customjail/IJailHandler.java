package net.foxgenesis.customjail;

import java.time.Duration;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAmount;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.foxgenesis.customjail.time.CustomTime;

public interface IJailHandler {

	public boolean isJailed(Member member);

	public void jail(InteractionHook hook, Member member, Member moderator, CustomTime customTime, String reason,
			boolean addWarning);

	public void extendJailTime(InteractionHook hook, Member member, Member moderator, TemporalAmount time);

	public void startJailTime(InteractionHook hook, Member member);

	public void startJailTime(InteractionHook hook, Member member, Member moderator);

	public void unjail(InteractionHook hook, Member member, Member moderator, String reason);

	public static TemporalAmount getTemporalOfString(@Nonnull String temporalString) throws DateTimeParseException {
		Objects.requireNonNull(temporalString);

		if (Character.isUpperCase(temporalString.charAt(temporalString.length() - 1)))
			return Period.parse("P" + temporalString);
		return Duration.parse("PT" + temporalString);
	}
}

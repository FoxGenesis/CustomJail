package net.foxgenesis.customjail;

import java.util.Objects;

import org.springframework.context.MessageSourceResolvable;

public enum CommonMessages implements MessageSourceResolvable {
	MEMBER("customjail.embed.member"),
	MODERATOR("customjail.embed.moderator"),
	ANONYMOUS("customjail.anonymous"),
	ACCEPT("customjail.embed.accept"),
	ACCEPTED("customjail.embed.accepted"),
	YES("customjail.embed.yes"),
	NO("customjail.embed.no"),
	REASON("customjail.embed.reason"),
	DEFAULT_REASON("customjail.embed.defaultReason"),
	WARNING_LEVEL("customjail.embed.warning-level"),
	TOTAL_WARNINGS("customjail.embed.total-warnings"),
	WARNING_EXPIRES("customjail.embed.warning-expires"),
	WITH_WARNING("customjail.embed.with-warning"),
	NA("customjail.embed.na"),
	CASE_ID("customjail.embed.caseid"),
	DURATION("customjail.embed.duration"),
	TIME_LEFT("customjail.embed.time-left"),
	JAIL_DETAILS("customjail.container.jaildetails"),
	UNJAIL("customjail.embed.unjail"),
	FORCESTART("customjail.embed.forcestart"),
	MEMBER_JAILED("customjail.embed.jailed");

	private final String[] codes;

	CommonMessages(String code) {
		this.codes = new String[] { Objects.requireNonNull(code) };
	}

	@Override
	public String[] getCodes() {
		return codes;
	}

}

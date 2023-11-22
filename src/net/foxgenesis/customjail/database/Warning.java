package net.foxgenesis.customjail.database;

import java.sql.Timestamp;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

public record Warning(long guildID, long memberID, long moderator, String reason, long time, int caseId,
		boolean active) {

	@SuppressWarnings("exports")
	public Warning(long guildID, long memberID, long moderator, String reason, Timestamp timestamp, int caseID,
			boolean active) {
		this(guildID, memberID, moderator, reason, timestamp.getTime(), caseID, active);
	}

	private static final Pattern externalPattern = Pattern.compile("%([a-zA-Z0-9]+)%");

	public String toExternalFormat(String format) {
		return externalPattern.matcher(format).replaceAll(result -> replace(result));
	}

	public String toFormattedString(String format) {
		return format.formatted(caseId, "<t:" + (time / 1000) + ">", moderator, reason, active);
	}

	private String replace(MatchResult result) {
		return switch (result.group(1)) {
			case "caseid" -> "" + caseId;
			case "date" -> "<t:" + (time / 1000) + ":R>";
			case "moderator" -> "<@" + moderator + ">";
			case "reason" -> reason;
			case "active" -> Boolean.toString(active);
			default -> "null";
		};
	}
}
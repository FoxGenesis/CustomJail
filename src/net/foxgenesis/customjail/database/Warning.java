package net.foxgenesis.customjail.database;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

public record Warning(long guildID, long memberID, String reason, String moderator, TemporalAccessor time, int caseId,
		boolean active) {

	private static final Pattern externalPattern = Pattern.compile("%([a-zA-Z0-9]+)%");

	public Warning(long guildID, long memberID, String reason, String moderator, Timestamp time, int caseId,
			boolean active) {
		this(guildID, memberID, reason, moderator, time.toInstant(), caseId, active);
	}

	public Warning(long guildID, long memberID, String reason, String moderator, LocalDateTime time, int caseId,
			boolean active) {
		this(guildID, memberID, reason, moderator, Timestamp.valueOf(time), caseId, active);
	}

	public String toExternalFormat(String format, DateTimeFormatter formatter) {
		return externalPattern.matcher(format).replaceAll(result -> replace(result, formatter));
	}

	public String toFormattedString(String format, DateTimeFormatter formatter) {
		return format.formatted(caseId, formatter.format(time), moderator, reason, active);
	}

	private String replace(MatchResult result, DateTimeFormatter formatter) {
		return switch (result.group(1)) {
			case "caseid" -> "" + caseId;
			case "date" -> formatter.format(time);
			case "moderator" -> "<@" + moderator + ">";
			case "reason" -> reason;
			case "active" -> Boolean.toString(active);
			default -> "null";
		};
	}
}
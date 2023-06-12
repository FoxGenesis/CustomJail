package net.foxgenesis.customjail.database;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

public record Warning(String reason, String moderator, TemporalAccessor time, int caseId, boolean active) {
	public Warning(String reason, String moderator, Timestamp time, int caseId, boolean active) {
		this(reason, moderator, time.toInstant(), caseId, active);
	}

	public Warning(String reason, String moderator, LocalDateTime time, int caseId, boolean active) {
		this(reason, moderator, Timestamp.valueOf(time), caseId, active);
	}

	public String toFormattedString(String format, DateTimeFormatter formatter) {
		return format.formatted(reason, moderator, formatter.format(time), caseId, active);
	}
}
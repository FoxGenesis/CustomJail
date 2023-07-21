package net.foxgenesis.customjail.time;

import java.time.Instant;
import java.util.Date;
import java.util.Objects;

public class UnixTimestamp implements Comparable<Long> {
	private static final String TIME_FORMAT = "<t:%d:%C>";
	private final long milli;

	public UnixTimestamp(long milli) {
		this.milli = milli;
	}

	public long getEpochMilli() {
		return milli;
	}

	public long getEpochSeconds() {
		return milli / 1_000;
	}

	public String getRelativeTimeStringInSeconds() {
		return formatTime(getEpochSeconds(), true);
	}

	public String getRelativeTimeStringInMilli() {
		return formatTime(getEpochMilli(), true);
	}

	public String getFullTimeStringInSeconds() {
		return formatTime(getEpochSeconds(), false);
	}

	public String getFullTimeStringInMilli() {
		return formatTime(getEpochMilli(), false);
	}

	public Instant toInstant() {
		return Instant.ofEpochMilli(milli);
	}

	public Date toDate() {
		return Date.from(toInstant());
	}

	@Override
	public int compareTo(Long o) {
		return Long.compare(milli, o);
	}

	@Override
	public int hashCode() {
		return Objects.hash(milli);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		UnixTimestamp other = (UnixTimestamp) obj;
		return milli == other.milli;
	}

	@Override
	public String toString() {
		return "UnixTimestamp [milli=" + milli + "]";
	}

	// ========================================================================================================

	public static UnixTimestamp fromEpochMilli(long milli) {
		return new UnixTimestamp(milli);
	}

	public static UnixTimestamp fromEpochSeconds(long seconds) {
		return fromEpochMilli(seconds * 1_000);
	}

	public static UnixTimestamp now() {
		return fromEpochMilli(System.currentTimeMillis());
	}

	public static UnixTimestamp fromInstant(Instant instant) {
		return fromEpochMilli(instant.toEpochMilli());
	}

	public static UnixTimestamp fromDate(Date date) {
		return fromEpochMilli(date.getTime());
	}

	private static String formatTime(long time, boolean relative) {
		return TIME_FORMAT.formatted(time, relative ? 'r' : 'f');
	}
}

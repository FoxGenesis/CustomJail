package net.foxgenesis.customjail.util;

import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.time.DateUtils;
import org.springframework.context.MessageSource;

public class CustomTime {

	private static final Pattern pattern = Pattern.compile("(\\d+)([YyMWwDdHhmsS])");
	private static final char[] index = { 'S', 's', 'm', 'h', 'D', 'W', 'M', 'Y' };
	private static final int[] cap = { 1000, 60, 60, 24, 7, 4, 12, -1 };

	private final Map<Character, Long> parts;

	public CustomTime(String str) {
		this.parts = getParts(str);
	}

	public CustomTime normalize() {
		// Normalize values
		long tmp, tmp2;
		int max;
		char key;
		for (int i = 0; i < index.length; i++) {
			key = index[i];
			max = cap[i];
			if (!parts.containsKey(key) || max < 0)
				continue;

			tmp2 = parts.get(key);
			tmp = tmp2 / max;
			if (tmp > 0) {
				parts.merge(index[i + 1], tmp, (a, b) -> a + b);
				tmp = tmp2 % max;
				if (tmp == 0)
					parts.remove(key);
				else
					parts.put(key, tmp2 % max);
			}
		}

		return this;
	}

	public Date addTo(Date date) {
		Date tmp = date;
		int[] tmp2 = { 0, 0 };

		for (char key : parts.keySet()) {
			long value = parts.get(key);
			if (value == 0)
				continue;

			// Long to Integer
			if (value > Integer.MAX_VALUE) {
				tmp2[0] = Integer.MAX_VALUE;
				tmp2[1] = (int) (value % Integer.MAX_VALUE);
			} else {
				tmp2[0] = (int) value;
				tmp2[1] = 0;
			}

			for (int a : tmp2) {
				if (a == 0)
					continue;
				tmp = switch (key) {
				case 'Y', 'y' -> DateUtils.addYears(tmp, a);
				case 'M' -> DateUtils.addMonths(tmp, a);
				case 'W', 'w' -> DateUtils.addWeeks(tmp, a);
				case 'D', 'd' -> DateUtils.addDays(tmp, a);
				case 'H', 'h' -> DateUtils.addHours(tmp, a);
				case 'm' -> DateUtils.addMinutes(tmp, a);
				case 's' -> DateUtils.addSeconds(tmp, a);
				case 'S' -> DateUtils.addMilliseconds(tmp, a);
				default -> tmp;
				};
			}
		}
		return tmp;
	}

	public String getDisplayString() {
		StringBuilder b = new StringBuilder();
		long value;
		char key;
		String type;

		for (int i = index.length - 1; i > 0; i--) {
			key = index[i];
			if (parts.containsKey(key)) {
				type = switch (key) {
				case 'Y', 'y' -> "Year";
				case 'M' -> "Month";
				case 'W', 'w' -> "Week";
				case 'D', 'd' -> "Day";
				case 'H', 'h' -> "Hour";
				case 'm' -> "Minute";
				case 's' -> "Second";
				case 'S' -> "Millisecond";
				default -> null;
				};
				if (type == null)
					continue;

				value = parts.get(key);
				if (value == 0)
					continue;
				if (value < -1 || value > 1)
					type += "s";

				b.append(value + " " + type);
				if (i > 0)
					b.append(" ");
			}
		}
		return b.toString();
	}

	public String getLocalizedDisplayString(MessageSource source, Locale locale) {
		StringBuilder b = new StringBuilder();
		long value;
		char key;
		String type;

		for (int i = index.length - 1; i > 0; i--) {
			key = index[i];
			if (parts.containsKey(key)) {
				type = switch (key) {
				case 'Y', 'y' -> "years";
				case 'M' -> "months";
				case 'W', 'w' -> "weeks";
				case 'D', 'd' -> "days";
				case 'H', 'h' -> "hours";
				case 'm' -> "minutes";
				case 's' -> "seconds";
				case 'S' -> "milliseconds";
				default -> null;
				};
				if (type == null)
					continue;

				value = parts.get(key);
				if (value == 0)
					continue;

				b.append(source.getMessage("customtime." + type, new Object[] { value }, locale));

				if (i > 0)
					b.append(" ");
			}
		}
		return b.toString();
	}

	@Override
	public int hashCode() {
		return Objects.hash(parts);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CustomTime other = (CustomTime) obj;
		return Objects.equals(parts, other.parts);
	}

	@Override
	public String toString() {
		// Rebuild time string
		StringBuilder b = new StringBuilder();
		for (int i = index.length - 1; i > 0; i--) {
			char key = index[i];
			if (parts.containsKey(key))
				b.append(parts.get(key) + "" + key);
		}
		return b.toString();
	}

	private static Map<Character, Long> getParts(String raw) {
		if (raw == null || raw.isBlank())
			return new HashMap<>();

		// Map type to values
		Map<Character, Long> map = pattern.matcher(raw)
				// Get results
				.results()
				// As map
				.collect(Collectors.toMap(
						// Key: time unit
						m -> m.group(2).charAt(0),
						// Value: time value
						m -> Long.parseLong(m.group(1)),
						// Add duplicated values
						(a, b) -> a + b));

		// Fix key capitalization
		map.keySet()
				// Stream keys
				.stream()
				// Check for lower-case years, weeks and days
				.filter(s -> s.equals('y') || s.equals('w') || s.equals('d'))
				// Merge lower-case keys to upper-case
				.forEach(s -> map.merge(Character.toUpperCase(s), map.get(s), (a, b) -> a + b));

		// Merge upper-case hours to lower-case
		if (map.containsKey('H'))
			map.merge('h', map.get('H'), (a, b) -> a + b);

		return map;
	}
}

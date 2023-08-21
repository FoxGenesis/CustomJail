package net.foxgenesis.customjail.time;

import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.time.DateUtils;
import org.jetbrains.annotations.NotNull;

public class CustomTime {

	private static final Pattern pattern = Pattern.compile("(\\d+)([YyMWwDdHhmsS])");
	private static final String[] index = { "S", "s", "m", "h", "D", "W", "M", "Y" };
	private static final int[] cap = { 1000, 60, 60, 24, 7, 4, 12, -1 };

	@NotNull
	private final Map<String, Long> parts;

	public CustomTime(@NotNull String str) {
		Objects.requireNonNull(str);
		if (str.isBlank())
			throw new NumberFormatException("Unable to parse blank string");
		this.parts = getParts(str);
	}

	@NotNull
	public CustomTime normalize() {
		// Normalize values
		long tmp, tmp2;
		int max;
		String key;
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

	@NotNull
	public Date addTo(Date date) {
		Date tmp = date;
		int[] tmp2 = { 0, 0 };

		for (String key : parts.keySet()) {
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
					case "Y" -> DateUtils.addYears(tmp, a);
					case "M" -> DateUtils.addMonths(tmp, a);
					case "W", "w" -> DateUtils.addWeeks(tmp, a);
					case "D", "d" -> DateUtils.addDays(tmp, a);
					case "H", "h" -> DateUtils.addHours(tmp, a);
					case "m" -> DateUtils.addMinutes(tmp, a);
					case "s" -> DateUtils.addSeconds(tmp, a);
					case "S" -> DateUtils.addMilliseconds(tmp, a);
					default -> tmp;
				};
			}
		}
		return tmp;
	}

	@NotNull
	public String getDisplayString() {
		StringBuilder b = new StringBuilder();
		long value;
		String key;
		String type;

		for (int i = index.length - 1; i > 0; i--) {
			key = index[i];
			if (parts.containsKey(key)) {
				type = switch (key) {
					case "Y", "y" -> "Year";
					case "M" -> "Month";
					case "W", "w" -> "Week";
					case "D", "d" -> "Day";
					case "H", "h" -> "Hour";
					case "m" -> "Minute";
					case "s" -> "Second";
					case "S" -> "Millisecond";
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
			String key = index[i];
			if (parts.containsKey(key))
				b.append(parts.get(key) + key);
		}
		return b.toString();
	}

	@NotNull
	private static Map<String, Long> getParts(String raw) {
		// Map type to values
		Map<String, Long> map = pattern.matcher(raw).results()
				.collect(Collectors.toMap(m -> m.group(2), m -> Long.parseLong(m.group(1)), (a, b) -> a + b));

		// Fix key capitalization
		map.keySet().stream().filter(s -> s.equals("y") || s.equals("w") || s.equals("d"))
				.forEach(s -> map.merge(s.toUpperCase(), map.get(s), (a, b) -> a + b));
		if (map.containsKey("H"))
			map.merge("h", map.get("H"), (a, b) -> a + b);

		return map;
	}
}

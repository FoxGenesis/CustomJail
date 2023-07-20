package net.foxgenesis.customjail.embed;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.function.Supplier;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.foxgenesis.customjail.database.Warning;
import net.foxgenesis.util.StringUtils;

public class WarningsEmbed extends ACustomEmbed {

	private static final DateTimeFormatter formatter = DateTimeFormatter
			.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT).withZone(ZoneId.systemDefault());
	// private static final String WARNING_FORMAT = "* %s - %s %s\n[Case ID:
	// %d]\n\n";

	// caseId, formatter.format(time), moderator, reason, active
	// private static final String WARNING_FORMAT = "* [%d] *%s* %s - %s \n\n";

	private static final String WARNING_FORMAT = "* %caseid% %s| %moderator% at `%date%`\n> *%reason%*\n";

	private final int maxPages;
	private int page = 1;

	public WarningsEmbed(MessageEmbed embed) {
		this(embed, () -> 1);
	}

	public WarningsEmbed(MessageEmbed embed, Supplier<Integer> maxPages) {
		super(embed);
		if (embed != null) {
			String[] split = embed.getFooter().getText().split("/");
			if (split.length != 2)
				throw new RuntimeException("Failed to parse page number!");

			this.page = Integer.parseInt(split[0]);
			this.maxPages = Integer.parseInt(split[1]);
		} else {
			this.maxPages = Math.max(1, maxPages.get());
		}
	}

	@Override
	protected void handleField(Field field) {}

	public int getPage() {
		return page;
	}

	public int getMaxPage() {
		return maxPages;
	}

	public WarningsEmbed setPageNumber(int page) {
		this.page = page;
		return this;
	}

	public WarningsEmbed setUser(Member member) {
		builder.setAuthor(member.getEffectiveName(), null, member.getEffectiveAvatarUrl()).setColor(member.getColor());
		replaceField(new Field("User", member.getAsMention(), true));
		// addBlank();
		return this;
	}

	public WarningsEmbed setWarningLevel(int count) {
		replaceField(new Field("Current Warning Level", "" + count, true, true));
		return this;
	}

	public WarningsEmbed setWarnings(Warning... warnings) {
		StringBuilder builder = new StringBuilder();
		if (warnings.length > 0) {
			for (int i = 0; i < warnings.length; i++)
				builder.append(warnings[i].toExternalFormat(WARNING_FORMAT, formatter)
						.formatted(warnings[i].active() ? "**[Active]** " : "") + "\n");
		} else {
			builder.append("No Warnings");
		}
		replaceField(new Field("Warnings", StringUtils.limit(builder.toString(), MessageEmbed.VALUE_MAX_LENGTH), false,
				true));
		return this;
	}

	@Override
	public MessageEmbed build() {
		builder.setFooter(page + "/" + maxPages);
		return super.build();
	}
}

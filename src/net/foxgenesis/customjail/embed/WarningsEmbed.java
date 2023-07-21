package net.foxgenesis.customjail.embed;

import java.time.Instant;
import java.util.function.Supplier;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.foxgenesis.customjail.CustomJailPlugin;
import net.foxgenesis.customjail.database.Warning;
import net.foxgenesis.util.StringUtils;

public class WarningsEmbed extends ACustomEmbed {
	private static final String WARNING_FORMAT = "%1$s#%caseid%:%<s %date% - By **%moderator%**\n**Reason: **%reason%\n";

	private final int maxPages;
	private int page = 1;

	public WarningsEmbed(MessageEmbed embed) {
		this(embed, () -> 1);
	}

	public WarningsEmbed(MessageEmbed embed, Supplier<Integer> maxPages) {
		super(embed);
		if (embed != null) {
			String[] split = embed.getFooter().getText().substring("Page ".length()).split("/");
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
		builder.setThumbnail(member.getEffectiveAvatarUrl());
		builder.setColor(member.getColor());
		builder.appendDescription("### Warnings - " + member.getAsMention() + "\n");
		return this;
	}

	public WarningsEmbed setTotalWarning(int count) {
		replaceField(new Field("Total Warnings", "" + count, true));

		return this;
	}

	public WarningsEmbed setWarningLevel(int count) {
		replaceField(new Field("Warning Level", "" + count, true));
		return this;
	}

	public WarningsEmbed setWarnings(Warning... warnings) {
		StringBuilder builder = new StringBuilder(">>> ");
		if (warnings.length > 0) {
			for (int i = 0; i < warnings.length; i++) {
				Warning w = warnings[i];
				String a = w.active() ? "**" : "";
				builder.append(warnings[i].toExternalFormat(WARNING_FORMAT).formatted(a) + "\n");
			}
		} else {
			builder.append("No Warnings");
		}
		this.builder.appendDescription(StringUtils.limit(builder.toString(), MessageEmbed.VALUE_MAX_LENGTH));
		return this;
	}

	@Override
	public MessageEmbed build() {
		builder.setFooter("Page " + page + "/" + maxPages, CustomJailPlugin.EMBED_FOOTER_ICON);
		builder.setTimestamp(Instant.now());
		return super.build();
	}
}

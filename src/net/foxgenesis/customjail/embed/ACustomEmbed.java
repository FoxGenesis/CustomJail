package net.foxgenesis.customjail.embed;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;

public abstract class ACustomEmbed {
	protected final EmbedBuilder builder = new EmbedBuilder();
	private final List<Field> fields = new ArrayList<>();

	public ACustomEmbed(MessageEmbed embed) {
		if (embed != null) {
			fields.addAll(embed.getFields());
			builder.copyFrom(embed);
			builder.clearFields();
		}
		fields.forEach(this::handleField);
	}

	protected abstract void handleField(Field field);

	protected void replaceField(String name, String value, boolean inline) {
		replaceField(new Field(name, value, inline));
	}

	protected void replaceField(Field field) {
		int index = getFieldIndex(field.getName());
		if (index == -1)
			fields.add(field);
		else
			fields.set(index, field);
	}

	protected void addBlank() {
		addBlank(fields.size());
	}

	protected void addBlank(int index) {
		fields.add(index, new Field("", "", true));
	}

	protected boolean hasField(Field field) {
		return fields.contains(field);
	}

	protected boolean hasField(String key) {
		return getFieldIndex(key) > -1;
	}

	protected Optional<Field> getField(String key) {
		int index = getFieldIndex(key);
		return Optional.ofNullable(index != -1 ? fields.get(index) : null);
	}

	public MessageEmbed build() {
		for (int i = 0; i < fields.size(); i++)
			builder.addField(fields.get(i));
		return builder.build();
	}

	private final int getFieldIndex(String key) {
		for (int i = 0; i < fields.size(); i++)
			if (fields.get(i).getName().equalsIgnoreCase(key))
				return i;
		return -1;
	}
}

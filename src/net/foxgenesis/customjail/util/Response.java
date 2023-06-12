package net.foxgenesis.customjail.util;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import static net.foxgenesis.watame.Constants.Colors.*;

public final class Response {

	// =================================================================================================================

	public static MessageEmbed info(String description) {
		return builder(INFO, description).build();
	}

	public static MessageEmbed info(String title, String description) {
		return builder(INFO, title, description).build();
	}

	public static MessageEmbed info(String title, String url, String description) {
		return builder(INFO, title, url, description).build();
	}

	// =================================================================================================================

	public static MessageEmbed success(String description) {
		return builder(SUCCESS, description).build();
	}

	public static MessageEmbed success(String title, String description) {
		return builder(SUCCESS, title, description).build();
	}

	public static MessageEmbed success(String title, String url, String description) {
		return builder(SUCCESS, title, url, description).build();
	}

	// =================================================================================================================

	public static MessageEmbed notice(String description) {
		return builder(WARNING_DARK, description).build();
	}

	public static MessageEmbed notice(String title, String description) {
		return builder(WARNING_DARK, title, description).build();
	}

	public static MessageEmbed notice(String title, String url, String description) {
		return builder(WARNING_DARK, title, url, description).build();
	}

	// =================================================================================================================

	public static MessageEmbed warn(String description) {
		return builder(WARNING, description).build();
	}

	public static MessageEmbed warn(String title, String description) {
		return builder(WARNING, title, description).build();
	}

	public static MessageEmbed warn(String title, String url, String description) {
		return builder(WARNING, title, url, description).build();
	}

	// =================================================================================================================

	public static MessageEmbed error(String description) {
		return builder(ERROR, description).build();
	}

	public static MessageEmbed error(String title, String description) {
		return builder(ERROR, title, description).build();
	}

	public static MessageEmbed error(String title, String url, String description) {
		return builder(ERROR, title, url, description).build();
	}

	// =================================================================================================================

	private static EmbedBuilder builder(int color, String description) {
		return new EmbedBuilder().setColor(color).setDescription(description);
	}

	private static EmbedBuilder builder(int color, String title, String description) {
		return builder(color, description).setTitle(title);
	}

	private static EmbedBuilder builder(int color, String title, String url, String description) {
		return builder(color, description).setTitle(title, url);
	}
}

package net.foxgenesis.customjail.jail.event;

import java.util.Objects;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

public abstract class AbstractJailEvent implements IJailEvent {

	private final Guild guild;
	private final Optional<Member> mod;
	private final Optional<String> reason;

	public AbstractJailEvent(Guild guild, Optional<Member> mod, Optional<String> reason) {
		this.guild = Objects.requireNonNull(guild);
		this.mod = Objects.requireNonNull(mod);
		this.reason = Objects.requireNonNull(reason);
	}

	@Override
	@NotNull
	public Guild getGuild() {
		return guild;
	}

	@NotNull
	public Optional<Member> getModerator() {
		return mod;
	}

	@NotNull
	public Optional<String> getReason() {
		return reason;
	}
}

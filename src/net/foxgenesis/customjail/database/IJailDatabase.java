package net.foxgenesis.customjail.database;

import java.time.temporal.TemporalAmount;
import java.util.Optional;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.entities.Member;

public interface IJailDatabase {

	boolean jail(@Nonnull Member member, @Nonnull TemporalAmount amount);
	boolean unjail(@Nonnull Member member);
	boolean isJailed(@Nonnull Member member);
	@Nonnull
	Optional<TemporalAmount> getJailDuration(@Nonnull Member member);
}

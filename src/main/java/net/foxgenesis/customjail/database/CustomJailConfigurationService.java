package net.foxgenesis.customjail.database;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;

@Service
@CacheConfig(cacheNames = "customjail")
public class CustomJailConfigurationService {

	@Autowired
	private CustomJailDatabase database;

	@Cacheable(cacheNames = "jailManagedRoles", key = "#guild.idLong")
	public Set<Long> getManagedRoles(Guild guild) {
		return get(guild).map(config -> {
			Set<Long> out = new HashSet<>();
			// Get all roles
			guild.getRoles()
					// Stream
					.stream()
					// Filter roles that start with prefix
					.filter(r -> r.getName().startsWith(config.getWarningsPrefix()))
					// Reverse order
					.sorted(Comparator.reverseOrder())
					// Limit amount of warning roles
					.limit(config.getMaxWarnings())
					// Get role ID
					.map(Role::getIdLong)
					// Add to list
					.forEach(out::add);
			// Add jail role
			out.add(config.getJailRole());
			return out;
		}).orElse(Set.of());
	}

//	private List<Role> getWarningRoles(Guild guild) {
//		return get(guild)
//				// Map
//				.map(config -> guild.getRoles()
//						// Stream
//						.stream()
//						// Filter roles that start with prefix
//						.filter(r -> r.getName().startsWith(config.getWarningsPrefix()))
//						// Reverse order
//						.sorted(Comparator.reverseOrder())
//						// As set
//						.toList())
//				.orElseThrow();
//	}

	public boolean isEnabled(Guild guild) {
		return get(guild).map(CustomJailConfiguration::isEnabled).orElse(false);
	}

	@Cacheable(key = "#guild")
	public Optional<CustomJailConfiguration> get(long guild) {
		return database.findByGuild(guild);
	}

	@Cacheable(key = "#guild.idLong")
	public Optional<CustomJailConfiguration> get(Guild guild) {
		return database.findByGuild(guild);
	}

	@CachePut(key = "#guild.idLong")
	public Optional<CustomJailConfiguration> getFresh(Guild guild) {
		return database.findByGuild(guild);
	}

	@CacheEvict(cacheNames = { "customjail", "jailManagedRoles" }, key = "#guild.idLong")
	public void delete(Guild guild) {
		database.deleteByGuild(guild);
	}

	@CacheEvict(cacheNames = { "customjail", "jailManagedRoles" }, key = "#config.guild")
	public CustomJailConfiguration save(CustomJailConfiguration config) {
		return database.save(config);
	}
}

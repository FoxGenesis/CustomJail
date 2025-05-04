package net.foxgenesis.customjail;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.localization.LocalizationFunction;
import net.dv8tion.jda.api.interactions.commands.localization.ResourceBundleLocalizationFunction;
import net.foxgenesis.customjail.database.CustomJailConfigurationService;
import net.foxgenesis.customjail.jail.JailScheduler;
import net.foxgenesis.customjail.jail.JailSystem;
import net.foxgenesis.customjail.jail.impl.JailSchedulerImpl;
import net.foxgenesis.customjail.jail.impl.JailSystemImpl;
import net.foxgenesis.customjail.util.CustomTime;
import net.foxgenesis.rolestorage.ManagedRoles;
import net.foxgenesis.springJDA.annotation.Permissions;
import net.foxgenesis.springJDA.annotation.SpringJDAAutoConfiguration;
import net.foxgenesis.springJDA.provider.GlobalCommandProvider;
import net.foxgenesis.watame.plugin.WatamePlugin;

@EntityScan
@ComponentScan
@EnableJpaRepositories
@SpringJDAAutoConfiguration
@WatamePlugin(id = "customjail")
public class CustomJailAutoConfiguration{

	@Bean
	@ConditionalOnMissingBean
	@Permissions({ Permission.MANAGE_ROLES, Permission.VOICE_MOVE_OTHERS, Permission.MESSAGE_EMBED_LINKS,
			Permission.MESSAGE_SEND })
	JailSystem defaultJailSystem(
			@Value("${customjail.timings:30m,1h,5h,12h,1D,2D,3D,1W,2W,1M,2M,3M}") String[] timings) {
		return new JailSystemImpl(timings);
	}

	@Bean
	@ConditionalOnMissingBean
	JailScheduler defaultJailScheduler(Scheduler scheduler) {
		return new JailSchedulerImpl(scheduler);
	}

	@Bean
	JailFrontend jailFrontend(JailSystem system) {
		return new JailFrontend(system);
	}

//	@Override
//	public void customize(SchedulerFactoryBean s) {
//		Properties properties = new Properties();
//		properties.setProperty("org.quartz.scheduler.classLoadHelper.class", CascadingClassLoadHelper.class.getName());
//		properties.setProperty("org.quartz.jobStore.useProperties", "" + true);
//		s.setQuartzProperties(properties);
//	}

	@Bean
	GlobalCommandProvider customjailCommands(JailSystem system, MessageSource source) {
		return () -> {
			// Timing localization function
			Function<String, Map<DiscordLocale, String>> choiceLocalization = timing -> {
				CustomTime time = new CustomTime(timing);

				Map<DiscordLocale, String> out = new HashMap<>();
				for (DiscordLocale locale : DiscordLocale.values()) {
					if (locale == DiscordLocale.UNKNOWN)
						continue;
					try {
						out.put(locale, time.getLocalizedDisplayString(source, locale.toLocale()));
					} catch (NoSuchMessageException e) {
					}
				}

				return out;
			};
			// Create timing choices
			List<Choice> choices = Arrays.stream(system.getJailTimings())
					.map(arr -> new Command.Choice(arr, arr).setNameLocalizations(choiceLocalization.apply(arr)))
					.toList();

			LocalizationFunction localization = ResourceBundleLocalizationFunction
					.fromBundles("plugins.customjail.lang.commands", DiscordLocale.ENGLISH_US).build();
			DefaultMemberPermissions perm = DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS);

			return Set.of(
					// Jail
					user("Jail User", perm, localization),
					// Jail
					slash("jail", "Jail a user", perm, localization)
							.addOption(OptionType.USER, "user", "User to jail", true)
							.addOptions(
									new OptionData(OptionType.STRING, "duration", "Amount of time to jail user", true)
											.addChoices(choices))
							.addOptions(new OptionData(OptionType.STRING, "reason", "Reason for the jailing")
									.setMaxLength(500))
							.addOption(OptionType.BOOLEAN, "add-warning", "Should this jail result in a warning"),

					// Un-jail
					slash("unjail", "Unjail a user", perm, localization)
							.addOption(OptionType.USER, "user", "User to unjail", true)
							.addOption(OptionType.STRING, "reason", "Reason for unjail"),

					// Force jail timer
					slash("forcestart", "Force start someone's jail timer", perm, localization)
							.addOption(OptionType.USER, "user", "User to start timer for", true)
							.addOption(OptionType.STRING, "reason", "Reason for warning update"),

					// Jail Details
					user("Jail Details", perm, localization),

					// ====== Warning Commands ======
					user("View Warnings", perm, localization),
					// Commands.user("Warnings2").setGuildOnly(true).setDefaultPermissions(perm),
					slash("warnings", "Commands that involve warnings", perm, localization).addSubcommands(
							// Decrease warning level
							new SubcommandData("decrease", "Decrease a users warning level")
									.addOption(OptionType.USER, "user", "User to decrease warning level for", true)
									.addOption(OptionType.STRING, "reason", "Reason for decreasing warning level"),

							// List warnings
							new SubcommandData("list", "List a user's warnings").addOption(OptionType.USER, "user",
									"User to get warnings for", true),

							// Add warning
							new SubcommandData("add", "Add a warning")
									.addOption(OptionType.USER, "user", "User to add warning to", true)
									.addOptions(new OptionData(OptionType.STRING, "reason", "Reason for the warning")
											.setMaxLength(500))
									.addOption(OptionType.BOOLEAN, "active",
											"Should this warning count to the member's warning level"),

							// Remove warning
							new SubcommandData("remove", "Remove a warning")
									.addOptions(new OptionData(OptionType.INTEGER, "case-id", "Warning id", true)
											.setMinValue(0))
									.addOption(OptionType.STRING, "reason", "Reason for warning removal"),

							// Clear member warnings
							new SubcommandData("clear", "Clear all warnings for a member")
									.addOption(OptionType.USER, "user", "User to add warning to", true)
									.addOption(OptionType.STRING, "reason", "Reason for the warning"),

							// Update warning
							new SubcommandData("update", "Update a warning").addOptions(
									new OptionData(OptionType.INTEGER, "case-id", "Warning id", true).setMinValue(0),
									new OptionData(OptionType.STRING, "new-reason", "New warning reason", true)
											.setMaxLength(500))
									.addOption(OptionType.STRING, "reason", "Reason for warning update"),

							// Refresh
							new SubcommandData("fix", "Attempt to fix a member's warning level and timers")
									.addOption(OptionType.USER, "user", "User to refresh", true)));
		};
	}

	private static CommandData user(String id, DefaultMemberPermissions permissions,
			LocalizationFunction localization) {
		return Commands.user(id)
				// Set guild only
				.setGuildOnly(true)
				// Set default permissions
				.setDefaultPermissions(permissions)
				// Set localization
				.setLocalizationFunction(localization);
	}

	private static SlashCommandData slash(String id, String description, DefaultMemberPermissions permissions,
			LocalizationFunction localization) {
		return Commands.slash(id, description)
				// Set guild only
				.setGuildOnly(true)
				// Set default permissions
				.setDefaultPermissions(permissions)
				// Set localization
				.setLocalizationFunction(localization);
	}

	@Configuration
	@ConditionalOnClass(ManagedRoles.class)
	public class CustomJailRoleConfiguration {

		@Bean
		ManagedRoles customJailRoles(CustomJailConfigurationService service) {
			return service::getManagedRoles;
		}
	}
}

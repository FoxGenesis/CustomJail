package net.foxgenesis.customjail.web;

import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import net.dv8tion.jda.api.entities.Guild;
import net.foxgenesis.customjail.database.CustomJailConfiguration;
import net.foxgenesis.customjail.database.CustomJailConfigurationService;
import net.foxgenesis.customjail.jail.JailSystem;
import net.foxgenesis.watame.web.WebPanelUtil;
import net.foxgenesis.watame.web.annotation.PluginMapping;
import net.foxgenesis.watame.web.security.DiscordOAuth2User;

@PluginMapping(plugin = "customjail")
public class CustomJailController {

	@Autowired
	private CustomJailConfigurationService database;

	@Autowired
	private JailSystem system;

	@Autowired
	private WebPanelUtil util;

	@GetMapping
	public String getView(Model model, @RequestAttribute Guild guild,
			@RequestParam(required = false, defaultValue = "false") boolean scan) {
		if (scan)
			CompletableFuture.runAsync(
					() -> guild.findMembers(member -> !(member.getUser().isBot() && member.getUser().isSystem()))
							.onSuccess(members -> members.parallelStream().forEach(system::fixMember)));
		model.addAttribute("customJailConfiguration", database.get(guild).orElseGet(() -> getNew(guild)));
		return "customjail";
	}

	@PostMapping
	public String update(Model model, @AuthenticationPrincipal DiscordOAuth2User oauth2User,
			@RequestAttribute Guild guild, @Valid CustomJailConfiguration customJailConfiguration,
			BindingResult bindingResult, HttpServletResponse res) {
		if (bindingResult.hasErrors()) {
			res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return "customjail";
		}

		System.out.println(customJailConfiguration);
		customJailConfiguration.setGuild(guild.getIdLong());
		model.addAttribute("customJailConfiguration", database.save(customJailConfiguration));

		util.logConfigurationChange(oauth2User, guild, "customjail");

		return "customjail";
	}

	private CustomJailConfiguration getNew(Guild guild) {
		CustomJailConfiguration config = new CustomJailConfiguration();
		config.setGuild(guild.getIdLong());
		return config;
	}
}

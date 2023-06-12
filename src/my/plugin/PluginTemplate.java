package my.plugin;

import java.util.Collection;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.configuration2.Configuration;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.foxgenesis.watame.WatameBot;
import net.foxgenesis.watame.plugin.IEventStore;
import net.foxgenesis.watame.plugin.Plugin;
import net.foxgenesis.watame.plugin.SeverePluginException;

/**
 * @author Ashley
 *
 */
public class PluginTemplate extends Plugin {

	@Override
	protected void onPropertiesLoaded(Properties properties) {}

	@Override
	protected void onConfigurationLoaded(String identifier, Configuration config) {}

	@Override
	public void preInit() {

	}

	@Override
	protected void init(IEventStore eventStore) throws SeverePluginException {
		// TODO Auto-generated method stub

	}

	@Override
	public void postInit(WatameBot bot) {

	}

	@Override
	public void onReady(WatameBot bot) {

	}

	@Override
	public void close() throws Exception {

	}

	@Override
	public Collection<CommandData> getCommands() {
		return Set.of();
	}
}

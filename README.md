# WatameBot-Plugin-Template
A template for watamebot plugins.

## Setup
1. **You must have WatameBot cloned and in the same directory** (Will upload WatameBot to maven later)
2. Move *PluginTemplate.java* to your package (if you have one) and rename it what you want.
3. Edit *assets/META-INF/services/net.foxgenesis.watame.plugin.IPlugin* and ensure it links to your plugin class. Ex: *net.foxgenesis.myplugin.MyNewPlugin*
4. Fill in the *PluginProperties* annotation on your plugin class

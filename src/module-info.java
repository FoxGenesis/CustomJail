module watamebot.customjail {
	exports net.foxgenesis.customjail;
	exports net.foxgenesis.customjail.time;
	exports net.foxgenesis.customjail.database;
	exports net.foxgenesis.customjail.jail;
	exports net.foxgenesis.customjail.jail.event;
	exports net.foxgenesis.customjail.jail.event.impl;
	exports net.foxgenesis.customjail.timer to quartz;

	requires transitive java.desktop;
	requires transitive net.dv8tion.jda;
	requires transitive org.apache.commons.lang3;
	requires org.jetbrains.annotations;
	requires transitive org.slf4j;
	requires transitive quartz;
	requires transitive watamebot;
	requires transitive com.zaxxer.hikari;

	provides net.foxgenesis.watame.plugin.Plugin with net.foxgenesis.customjail.CustomJailPlugin;
}
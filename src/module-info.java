module watamebot.customjail {
	requires transitive quartz;
	requires transitive watamebot;
	requires transitive java.sql;

	requires static org.jetbrains.annotations;

	requires com.zaxxer.hikari;
	requires org.apache.commons.lang3;
	
	exports net.foxgenesis.customjail;
	exports net.foxgenesis.customjail.time;
	exports net.foxgenesis.customjail.database;
	exports net.foxgenesis.customjail.jail;
	exports net.foxgenesis.customjail.jail.event;
	exports net.foxgenesis.customjail.jail.event.impl;
	exports net.foxgenesis.customjail.timer to quartz;

	provides net.foxgenesis.watame.plugin.Plugin with net.foxgenesis.customjail.CustomJailPlugin;
}
module watamebot.customjail {
	requires transitive quartz;
	requires transitive watamebot;

	requires static org.jetbrains.annotations;

	requires com.zaxxer.hikari;
	requires org.apache.commons.lang3;
	requires net.dv8tion.jda;
	requires java.sql;
	
	exports net.foxgenesis.customjail;
	exports net.foxgenesis.customjail.time;
	exports net.foxgenesis.customjail.database;
	exports net.foxgenesis.customjail.jail;
	exports net.foxgenesis.customjail.jail.event;
	exports net.foxgenesis.customjail.jail.event.impl;
	exports net.foxgenesis.customjail.timer to quartz;

	provides net.foxgenesis.watame.plugin.Plugin with net.foxgenesis.customjail.CustomJailPlugin;
}
module watamebot.customjail {
	exports net.foxgenesis.customjail;
	exports net.foxgenesis.customjail.timer;
	exports net.foxgenesis.customjail.time;

	requires java.desktop;
	requires jsr305;
	requires net.dv8tion.jda;
	requires org.apache.commons.lang3;
	requires org.slf4j;
	requires quartz;
	requires transitive watamebot;
	requires com.zaxxer.hikari;

	provides net.foxgenesis.watame.plugin.Plugin with net.foxgenesis.customjail.CustomJailPlugin;
}
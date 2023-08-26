package net.foxgenesis.customjail;

import java.util.Properties;

public record SchedulerSettings(String name, String id, boolean skipUpdateCheck, String threadCount) {
	
	public SchedulerSettings() {
		this("JailScheduler", "NON_CLUSTERED", true, "1");
	}
	
	public void addToProperties(Properties properties) {
		properties.put("org.quartz.scheduler.instanceName", name);
		properties.put("org.quartz.scheduler.instanceId", id);
		properties.put("org.quartz.scheduler.skipUpdateCheck", skipUpdateCheck);
		properties.put("org.quartz.threadPool.threadCount", threadCount);
	}
}

package net.foxgenesis.customjail.timer;

import java.util.Date;

import org.apache.commons.lang3.time.DateUtils;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobErrorListener implements JobListener {
	private static final Logger logger = LoggerFactory.getLogger(JobErrorListener.class);
	private static final String RETRY_COUNT = "retry";

	private final long delay;
	private final long maxDelay;

	public JobErrorListener(long seconds, long maxDelay) {
		this.delay = Math.abs(seconds);
		this.maxDelay = Math.abs(maxDelay);
	}

	@Override
	public String getName() {
		return "Job Error Listener";
	}

	@Override
	public void jobToBeExecuted(JobExecutionContext context) {}

	@Override
	public void jobExecutionVetoed(JobExecutionContext context) {}

	@Override
	public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
		if (jobException != null) {
			Trigger trigger = context.getTrigger();

			JobDataMap map = trigger.getJobDataMap();
			map.putIfAbsent(RETRY_COUNT, 0);

			int retry = map.getIntValue(RETRY_COUNT);
			retry++;
			map.put(RETRY_COUNT, retry);

			double newDelay = Math.min(maxDelay, Math.pow(delay, retry)) * 1000;

			Trigger newTrigger = trigger.getTriggerBuilder()
					.startAt(DateUtils.addMilliseconds(new Date(), (int) newDelay)).build();

			try {
				logger.warn("Trigger {} failed to fire, retrying in {} milliseconds", trigger.getKey(), newDelay);
				context.getScheduler().rescheduleJob(trigger.getKey(), newTrigger);
			} catch (SchedulerException e) {
				logger.error("Failed to rescheduler misfire", e);
			}
		}
	}

}

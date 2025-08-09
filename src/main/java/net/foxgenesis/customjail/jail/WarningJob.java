package net.foxgenesis.customjail.jail;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WarningJob implements Job {

	private static final Logger logger = LoggerFactory.getLogger(WarningJob.class);

	@Autowired
	private JailSystem system;

//	@Override
//	protected void executeInternal(JobExecutionContext ctx) throws JobExecutionException {
//		WarningDetails details = WarningDetails.resolveFromDataMap(ctx.getMergedJobDataMap());
//
//		logger.info("Warning job finished for member {} in {}: ", details.member(), details.guild(), details);
//		system.decreaseWarningLevel(details.guild(), details.member(), null, null);
//	}

	@Override
	public void execute(JobExecutionContext ctx) throws JobExecutionException {
		WarningDetails details = WarningDetails.resolveFromDataMap(ctx.getMergedJobDataMap());

		logger.info("Warning job finished for member {} in {}: ", details.member(), details.guild(), details);
		system.decreaseWarningLevel(details.guild(), details.member(), null, null);
	}
}

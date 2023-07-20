package net.foxgenesis.customjail.timer;

import java.time.temporal.TemporalAmount;
import java.util.function.Consumer;

import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.foxgenesis.customjail.database.IWarningDatabase;
import net.foxgenesis.customjail.jail.JailDetails;
import net.foxgenesis.customjail.time.CustomTime;

public interface IJailScheduler {

	boolean createJailTimer(JailDetails details) throws SchedulerException;

	boolean startJailTimer(Member member);

	boolean isJailed(Member member);

	boolean isJailTimerRunning(Member member);

	boolean removeJailTimer(Member member);

	boolean createWarningTimer(Member member, CustomTime time);

	boolean isWarningTimerRunning(Member member);

	boolean resetWarningTimer(Member member);

	boolean updateWarningTimer(Member member, TemporalAmount newTime);

	boolean removeWarningTimer(Member member);

	SimpleTrigger createWarningTrigger(JobKey key, CustomTime time);

	void unjail(Member member, Role timeoutRole, Member mod, String reason, IWarningDatabase database,
			Consumer<Member> success, Consumer<Throwable> err);
}

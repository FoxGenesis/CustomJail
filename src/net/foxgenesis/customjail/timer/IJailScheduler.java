package net.foxgenesis.customjail.timer;

import java.time.temporal.TemporalAmount;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.requests.RestAction;
import net.foxgenesis.customjail.database.IWarningDatabase;
import net.foxgenesis.customjail.time.CustomTime;

public interface IJailScheduler {

	boolean createJailTimer(Member member, CustomTime time) throws SchedulerException;
	boolean startJailTimer(Member member);
	boolean extendJailTimer(Member member, TemporalAmount newTime);
	boolean isJailed(Member member);
	boolean isJailTimerRunning(Member member);
	boolean removeJailTimer(Member member);
	
	boolean createWarningTimer(Member member, CustomTime time);
	boolean isWarningTimerRunning(Member member);
	boolean resetWarningTimer(Member member);
	boolean updateWarningTimer(Member member, TemporalAmount newTime);
	boolean removeWarningTimer(Member member);
	
	@Nullable
	RestAction<?> updateWarningLevel(Member member, int newLevel);
	
	@Nonnull
	SimpleTrigger createWarningTrigger(JobKey key, CustomTime time);
	
	void unjail(Member member, Role timeoutRole, Member mod, String reason, IWarningDatabase database,
			Consumer<Member> success, Consumer<Throwable> err);
}

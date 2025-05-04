package net.foxgenesis.customjail.database;

import java.util.Objects;

import org.hibernate.validator.constraints.Range;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;

import net.foxgenesis.customjail.util.CustomTime;
import net.foxgenesis.springJDA.annotation.Snowflake;
import net.foxgenesis.watame.data.PluginConfiguration;

@Entity
public class CustomJailConfiguration extends PluginConfiguration {

	@Column(nullable = false)
	private boolean enabled = false;

	@Column
	@Snowflake
	private Long jailRole;

	@Column
	@Snowflake
	private Long jailChannel;

	@Column
	@Snowflake
	private Long logChannel;

	@Convert(converter = CustomTimeConverter.class)
	private CustomTime warningTime = new CustomTime("1M");

	@Column(nullable = false)
	private String warningsPrefix = "Warning";

	@Column(nullable = false)
	private boolean notifyMember = true;

	@Range(min = 1, max = 10)
	@Column(nullable = false)
	private int maxWarnings = 3;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Long getJailRole() {
		return jailRole;
	}

	public void setJailRole(Long jailRole) {
		this.jailRole = jailRole;
	}

	public Long getJailChannel() {
		return jailChannel;
	}

	public void setJailChannel(Long jailChannel) {
		this.jailChannel = jailChannel;
	}

	public Long getLogChannel() {
		return logChannel;
	}

	public void setLogChannel(Long logChannel) {
		this.logChannel = logChannel;
	}

	public CustomTime getWarningTime() {
		return warningTime;
	}

	public void setWarningTime(CustomTime warningTime) {
		this.warningTime = warningTime;
	}

	public String getWarningsPrefix() {
		return warningsPrefix;
	}

	public void setWarningsPrefix(String warningsPrefix) {
		this.warningsPrefix = warningsPrefix;
	}

	public boolean isNotifyMember() {
		return notifyMember;
	}

	public void setNotifyMember(boolean notifyMember) {
		this.notifyMember = notifyMember;
	}

	public int getMaxWarnings() {
		return maxWarnings;
	}

	public void setMaxWarnings(int maxWarnings) {
		this.maxWarnings = maxWarnings;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(enabled, jailChannel, jailRole, logChannel, maxWarnings, notifyMember,
				warningTime, warningsPrefix);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		CustomJailConfiguration other = (CustomJailConfiguration) obj;
		return enabled == other.enabled && Objects.equals(jailChannel, other.jailChannel)
				&& Objects.equals(jailRole, other.jailRole) && Objects.equals(logChannel, other.logChannel)
				&& maxWarnings == other.maxWarnings && notifyMember == other.notifyMember
				&& Objects.equals(warningTime, other.warningTime)
				&& Objects.equals(warningsPrefix, other.warningsPrefix);
	}

	@Override
	public String toString() {
		return "CustomJailConfiguration [enabled=" + enabled + ", jailRole=" + jailRole + ", jailChannel=" + jailChannel
				+ ", logChannel=" + logChannel + ", warningTime=" + warningTime + ", warningsPrefix=" + warningsPrefix
				+ ", notifyMember=" + notifyMember + ", maxWarnings=" + maxWarnings + "]";
	}
}

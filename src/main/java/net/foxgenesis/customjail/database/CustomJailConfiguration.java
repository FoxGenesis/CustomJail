package net.foxgenesis.customjail.database;

import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.Range;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.foxgenesis.customjail.util.CustomTime;
import net.foxgenesis.springJDA.annotation.Snowflake;
import net.foxgenesis.watame.data.PluginConfiguration;

@Data
@EqualsAndHashCode(callSuper = true)
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

	@Length(max = 500)
	@Column
	private String jailEmbedDescription;
}

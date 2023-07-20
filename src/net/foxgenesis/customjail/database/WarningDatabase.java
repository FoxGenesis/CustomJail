package net.foxgenesis.customjail.database;

import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.foxgenesis.database.AbstractDatabase;
import net.foxgenesis.util.StringUtils;
import net.foxgenesis.util.resource.ModuleResource;

public class WarningDatabase extends AbstractDatabase implements IWarningDatabase {

	public WarningDatabase() {
		super("Warning Database", new ModuleResource("watamebot.customjail", "/META-INF/sql statements.kvp"),
				new ModuleResource("watamebot.customjail", "/META-INF/tableSetup.sql"));

	}

	@Override
	public int getWarningLevelForMember(Member member) {
		return mapStatement("get_warning_level", statement -> {
			statement.setLong(1, member.getGuild().getIdLong());
			statement.setLong(2, member.getIdLong());

			// Execute query
			try (ResultSet result = statement.executeQuery()) {

				// Get first row if present
				if (result.next()) { return result.getInt("warning_level"); }
			}
			return -1;
		}, err -> logger.error("Error while getting warning level for member", err)).orElseThrow();
	}

	@Override
	public int getTotalWarnings(Member member) {
		return mapStatement("get_warning_count_for_member", statement -> {
			statement.setLong(1, member.getGuild().getIdLong());
			statement.setLong(2, member.getIdLong());

			// Execute query
			try (ResultSet result = statement.executeQuery()) {

				// Get first row if present
				return result.next() ? result.getInt("total") : -1;
			}
		}, err -> logger.error("Error while total warnings for member", err)).orElseThrow();
	}

	@Override
	public Optional<Warning> getWarning(Guild guild, int case_id) {
		validate(case_id);
		return mapStatement("get_warning", statement -> {
			statement.setLong(1, guild.getIdLong());
			statement.setInt(2, case_id);

			// Execute query
			try (ResultSet result = statement.executeQuery()) {

				// Get first row if present or null
				return result.next() ? parseWarning(result) : null;
			}
		}, err -> logger.error("Error while getting warning", err));
	}

	@Override
	public Warning[] getWarningsPageForMember(Member member, int itemsPerPage, int page) {
		return getWarningsPageForMember(member.getGuild().getIdLong(), member.getIdLong(), itemsPerPage, page);
	}

	@Override
	public Warning[] getWarningsPageForMember(long guildID, long memberID, int itemsPerPage, int page) {
		return mapStatement("get_warnings_page_for_member", statement -> {
			int _itemsPerPage = Math.max(1, itemsPerPage);
			int _page = Math.max(1, page);

			statement.setLong(1, guildID);
			statement.setLong(2, memberID);
			statement.setInt(3, (_page - 1) * _itemsPerPage);
			statement.setInt(4, _itemsPerPage);

			// Execute query
			try (ResultSet result = statement.executeQuery()) {
				return parseWarnings(result);
			}
		}, err -> logger.error("Error while getting warnings page", err)).orElseThrow();
	}

	@Override
	public int addWarningForMember(@NotNull Member member, @NotNull Member moderator, String reason, boolean active) {
		return mapCallable("add_warning", statement -> {
			statement.setLong(1, member.getGuild().getIdLong());
			statement.setLong(2, member.getIdLong());
			statement.setString(3, moderator.getUser().getId());
			statement.setString(4, StringUtils.limit(reason, 500));
			statement.setBoolean(5, active);
			statement.registerOutParameter(6, JDBCType.INTEGER);
			statement.execute();
			return statement.getInt(6);
		}, err -> logger.error("Error while adding warning for member", err)).orElse(-1);
	}

	@Override
	public int decreaseAndGetWarningLevel(@NotNull Member member) {
		return mapCallable("decrease_and_get_warning_level", statement -> {
			statement.setLong(1, member.getGuild().getIdLong());
			statement.setLong(2, member.getIdLong());
			statement.registerOutParameter(3, JDBCType.INTEGER);
			statement.execute();
			return statement.getInt(3);
		}, err -> logger.error("Error while adding warning for member", err)).orElseThrow();
	}

	@Override
	public boolean deleteWarning(@NotNull Guild guild, int case_id) {
		validate(case_id);
		return mapCallable("delete_warning", statement -> {
			statement.setLong(1, guild.getIdLong());
			statement.setInt(2, case_id);
			return statement.executeUpdate() > 0;
		}, err -> logger.error("Error while deleting case", err)).orElseThrow();
	}

	@Override
	public boolean updateWarningReason(@NotNull Guild guild, int case_id, @NotNull String reason) {
		validate(case_id);
		return mapCallable("update_warning_reason", statement -> {
			statement.setString(1, StringUtils.limit(reason, 500));
			statement.setLong(2, guild.getIdLong());
			statement.setInt(3, case_id);
			return statement.executeUpdate() > 0;
		}, err -> logger.error("Error while deleting case", err)).orElseThrow();
	}

	@Override
	public boolean deleteWarnings(@NotNull Member member) {
		return mapCallable("delete_all_warnings", statement -> {
			statement.setLong(1, member.getGuild().getIdLong());
			statement.setLong(2, member.getIdLong());
			return statement.executeUpdate() > 0;
		}, err -> logger.error("Error while deleting member warnings", err)).orElseThrow();
	}

	private void validate(int case_id) {
		if (!isValidCaseID(case_id))
			throw new IllegalArgumentException("Invalid Case ID");
	}

	@Override
	public void close() throws Exception {}

	@Override
	protected void onReady() {}

	@NotNull
	private static Warning[] parseWarnings(ResultSet result) throws SQLException {
		List<Warning> list = new ArrayList<>();
		while (result.next())
			list.add(parseWarning(result));
		return list.toArray(Warning[]::new);
	}

	@Nullable
	private static Warning parseWarning(ResultSet result) throws SQLException {
		return new Warning(result.getLong("guild_id"), result.getLong("member_id"), result.getString("reason"),
				result.getString("moderator"), result.getTimestamp("date"), result.getInt("case_id"),
				result.getBoolean("active"));
	}
}

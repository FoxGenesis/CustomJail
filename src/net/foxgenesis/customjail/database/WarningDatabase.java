package net.foxgenesis.customjail.database;

import java.sql.CallableStatement;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import net.foxgenesis.customjail.jail.WarningDetails;
import net.foxgenesis.database.AbstractDatabase;
import net.foxgenesis.util.StringUtils;
import net.foxgenesis.util.resource.ModuleResource;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

public class WarningDatabase extends AbstractDatabase implements IWarningDatabase {

	public WarningDatabase() {
		super("Warning Database", new ModuleResource("watamebot.customjail", "/META-INF/sql statements.kvp"),
				new ModuleResource("watamebot.customjail", "/META-INF/tableSetup.sql"));

	}

	@Override
	public int getWarningLevelForMember(Member member) {
		try {
			return mapStatement("get_warning_level", statement -> {
				statement.setLong(1, member.getGuild().getIdLong());
				statement.setLong(2, member.getIdLong());

				// Execute query
				try (ResultSet result = statement.executeQuery()) {

					// Get first row if present
					if (result.next()) { return result.getInt("warning_level"); }
				}
				return -1;
			}).orElseThrow();
		} catch (SQLException e) {
			logger.error("Failed to get warning level for " + member, e);
			return -1;
		}
	}

	@Override
	public int getTotalWarnings(Member member) {
		try {
			return mapStatement("get_warning_count_for_member", statement -> {
				statement.setLong(1, member.getGuild().getIdLong());
				statement.setLong(2, member.getIdLong());

				// Execute query
				try (ResultSet result = statement.executeQuery()) {

					// Get first row if present
					return result.next() ? result.getInt("total") : -1;
				}
			}).orElseThrow();
		} catch (SQLException e) {
			logger.error("Failed to get total warnings for " + member, e);
			return -1;
		}
	}

	@Override
	public Optional<Warning> getWarning(Guild guild, int case_id) {
		validate(case_id);
		try {
			return mapStatement("get_warning", statement -> {
				statement.setLong(1, guild.getIdLong());
				statement.setInt(2, case_id);

				// Execute query
				try (ResultSet result = statement.executeQuery()) {

					// Get first row if present or null
					return result.next() ? parseWarning(result) : null;
				}
			});
		} catch (SQLException e) {
			logger.error("Failed to get warning " + case_id, e);
			return Optional.empty();
		}
	}

	@Override
	public Collection<Warning> getWarnings(Member member) {
		try {
			return mapStatement("get_all_warnings_for_member", statement -> {
				statement.setLong(1, member.getGuild().getIdLong());
				statement.setLong(2, member.getIdLong());

				try (ResultSet result = statement.executeQuery()) {
					return List.of(parseWarnings(result));
				}

			}).orElse(Collections.emptyList());
		} catch (SQLException e) {
			logger.error("Failed to get warnings for " + member, e);
			return Collections.emptyList();
		}
	}

	@Override
	public Warning[] getWarningsPageForMember(Member member, int itemsPerPage, int page) {
		return getWarningsPageForMember(member.getGuild().getIdLong(), member.getIdLong(), itemsPerPage, page);
	}

	@Override
	public Warning[] getWarningsPageForMember(long guildID, long memberID, int itemsPerPage, int page) {
		try {
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
			}).orElseThrow();
		} catch (SQLException e) {
			logger.error("Failed to get warning page for member " + memberID, e);
			return null;
		}
	}

	@Override
	public int addWarningForMember(@NotNull Member member, @NotNull Member moderator, String reason, boolean active) {
		try {
			return mapCallable("add_warning", statement -> {
				statement.setLong(1, member.getGuild().getIdLong());
				statement.setLong(2, member.getIdLong());
				statement.setString(3, moderator.getUser().getId());
				statement.setString(4, StringUtils.limit(reason, 500));
				statement.setBoolean(5, active);
				statement.registerOutParameter(6, JDBCType.INTEGER);
				statement.execute();
				return statement.getInt(6);
			}).orElse(-1);
		} catch (SQLException e) {
			logger.error("Failed to add warning for member " + member, e);
			return -1;
		}
	}

	@SuppressWarnings("resource")
	@Override
	public Transaction<WarningDetails> createWarningTransaction(Member member, Member moderator, String reason,
			boolean active) {
		try {
			return new WarningTransaction(openConnection(), c -> {
				try (CallableStatement statement = c.prepareCall(getRawStatement("add_warning"))) {
					statement.setLong(1, member.getGuild().getIdLong());
					statement.setLong(2, member.getIdLong());
					statement.setString(3, moderator.getUser().getId());
					statement.setString(4, StringUtils.limit(reason, 500));
					statement.setBoolean(5, active);
					statement.registerOutParameter(6, JDBCType.INTEGER);
					statement.execute();
					int id = statement.getInt(6);
					return new WarningDetails(member, moderator, reason, System.currentTimeMillis(), id, active);
				}
			});
		} catch (SQLException e) {
			logger.error("Failed to add warning for member " + member, e);
			return null;
		}
	}

	@Override
	public int decreaseAndGetWarningLevel(@NotNull Member member) {
		try {
			return mapCallable("decrease_and_get_warning_level", statement -> {
				statement.setLong(1, member.getGuild().getIdLong());
				statement.setLong(2, member.getIdLong());
				statement.registerOutParameter(3, JDBCType.INTEGER);
				statement.execute();
				return statement.getInt(3);
			}).orElseThrow();
		} catch (SQLException e) {
			logger.error("Failed to decrease warning level for " + member, e);
			return -1;
		}
	}

	@Override
	public boolean deleteWarning(@NotNull Guild guild, int case_id) {
		validate(case_id);
		try {
			return mapCallable("delete_warning", statement -> {
				statement.setLong(1, guild.getIdLong());
				statement.setInt(2, case_id);
				return statement.executeUpdate() > 0;
			}).orElseThrow();
		} catch (SQLException e) {
			logger.error("Failed to delete warning " + case_id, e);
			return false;
		}
	}

	@Override
	public boolean updateWarningReason(@NotNull Guild guild, int case_id, @NotNull String reason) {
		validate(case_id);
		try {
			return mapCallable("update_warning_reason", statement -> {
				statement.setString(1, StringUtils.limit(reason, 500));
				statement.setLong(2, guild.getIdLong());
				statement.setInt(3, case_id);
				return statement.executeUpdate() > 0;
			}).orElseThrow();
		} catch (SQLException e) {
			logger.error("Failed to update warning reason for case id " + case_id, e);
			return false;
		}
	}

	@Override
	public boolean deleteWarnings(@NotNull Member member) {
		try {
			return mapCallable("delete_all_warnings", statement -> {
				statement.setLong(1, member.getGuild().getIdLong());
				statement.setLong(2, member.getIdLong());
				return statement.executeUpdate() > 0;
			}).orElseThrow();
		} catch (SQLException e) {
			logger.error("Failed to delete warnings for " + member, e);
			return false;
		}
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
		return new Warning(result.getLong("guild_id"), result.getLong("member_id"), result.getLong("moderator"),
				result.getString("reason"), result.getTimestamp("date"), result.getInt("case_id"),
				result.getBoolean("active"));
	}
}

package net.foxgenesis.customjail.database;

import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.foxgenesis.database.AbstractDatabase;
import net.foxgenesis.util.StringUtils;
import net.foxgenesis.util.resource.ModuleResource;

public class WarningDatabase extends AbstractDatabase implements IWarningDatabase {

	public WarningDatabase() {
		super("Warning Database", new ModuleResource("watamebot.customjail", "/META-INF/sql statements.kvp"),
				new ModuleResource("watamebot.customjail", "/META-INF/tableSetup.sql"));

	}

	@Override
	public int getWarningLevelForMember(@Nonnull Member member) {
		return mapStatement("get_warning_level", statement -> {
			statement.setLong(1, member.getGuild().getIdLong());
			statement.setLong(2, member.getIdLong());

			// Execute query
			try (ResultSet result = statement.executeQuery()) {

				// Get first row if present
				if (result.next()) { return result.getInt("warning_level"); }
			}
			return -1;
		}, err -> logger.error("Error while getting warning level for member", err));
	}

	@Override
	public int getTotalWarnings(@Nonnull Member member) {
		return mapStatement("get_warning_count_for_member", statement -> {
			statement.setLong(1, member.getGuild().getIdLong());
			statement.setLong(2, member.getIdLong());

			// Execute query
			try (ResultSet result = statement.executeQuery()) {

				// Get first row if present
				if (result.next()) { return result.getInt("total"); }
			}
			return -1;
		}, err -> logger.error("Error while total warnings for member", err));
	}

	@Override
	public Warning[] getWarningsPageForMember(@Nonnull Member member, int itemsPerPage, int page) {
		return getWarningsPageForMember(member.getGuild().getIdLong(), member.getIdLong(), itemsPerPage, page);
	}

	@Override
	public Warning[] getWarningsPageForMember(long guildID, long memberID, int itemsPerPage, int page) {
		return mapStatement("get_warnings_page_for_member", statement -> {

			statement.setLong(1, guildID);
			statement.setLong(2, memberID);
			statement.setInt(3, (page - 1) * itemsPerPage);
			statement.setInt(4, itemsPerPage);

			logger.info(statement.toString());

			// Execute query
			try (ResultSet result = statement.executeQuery()) {
				return parseWarnings(result);
			}
		}, err -> logger.error("Error while getting warnings page", err));
	}

	@Override
	public int addWarningForMember(@Nonnull Member member, @Nonnull Member moderator, @Nonnull String reason, boolean active) {
		User modUser = moderator.getUser();
		return mapCallable("add_warning", statement -> {
			statement.setLong(1, member.getGuild().getIdLong());
			statement.setLong(2, member.getIdLong());
			statement.setString(3, modUser.getName() + "#" + modUser.getDiscriminator());
			statement.setString(4, StringUtils.limit(reason, 500));
			statement.setBoolean(5, active);
			statement.registerOutParameter(6, JDBCType.INTEGER);
			statement.execute();
			return statement.getInt(6);
		}, err -> logger.error("Error while adding warning for member", err));
	}

	@Override
	public int decreaseAndGetWarningLevel(@Nonnull Member member) {
		return mapCallable("decrease_and_get_warning_level", statement -> {
			statement.setLong(1, member.getGuild().getIdLong());
			statement.setLong(2, member.getIdLong());
			statement.registerOutParameter(3, JDBCType.INTEGER);
			statement.execute();
			return statement.getInt(3);
		}, err -> logger.error("Error while adding warning for member", err));
	}

	@Override
	public boolean deleteWarning(@Nonnull Guild guild, int case_id) {
		return mapCallable("delete_warning", statement -> {
			statement.setLong(1, guild.getIdLong());
			statement.setInt(2, case_id);
			return statement.executeUpdate() > 0;
		}, err -> logger.error("Error while deleting case", err));
	}

	@Override
	public boolean updateWarningReason(@Nonnull Guild guild, int case_id, @Nonnull String reason) {
		return mapCallable("update_warning_reason", statement -> {
			statement.setString(1, StringUtils.limit(reason, 500));
			statement.setLong(2, guild.getIdLong());
			statement.setInt(3, case_id);
			return statement.executeUpdate() > 0;
		}, err -> logger.error("Error while deleting case", err));
	}
	
	@Override
	public boolean deleteWarnings(Member member) {
		// FIXME add SQL to delete all member warnings
		throw new UnsupportedOperationException("Not yet implemented!");
	}

	@Override
	public void close() throws Exception {}

	@Override
	protected void onReady() {}

	private static Warning[] parseWarnings(@Nonnull ResultSet result) throws SQLException {
		List<Warning> list = new ArrayList<>();
		while (result.next()) {
			list.add(new Warning(result.getString("reason"), result.getString("moderator"), result.getTimestamp("date"),
					result.getInt("case_id"), result.getBoolean("active")));
		}
		return list.toArray(Warning[]::new);
	}
}

package net.foxgenesis.customjail.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.Objects;
import java.util.Stack;

import net.foxgenesis.customjail.jail.WarningDetails;
import net.foxgenesis.database.AbstractDatabase.SQLFunction;

public class WarningTransaction implements Transaction<WarningDetails> {

	private final Stack<Savepoint> saves = new Stack<>();
	private final Connection connection;
	private final SQLFunction<Connection, WarningDetails> supplier;

	@SuppressWarnings("exports")
	public WarningTransaction(Connection connection, SQLFunction<Connection, WarningDetails> getWarning)
			throws SQLException {
		this.connection = Objects.requireNonNull(connection);
		this.supplier = Objects.requireNonNull(getWarning);
		connection.setAutoCommit(false);
	}

	@Override
	public WarningDetails get() throws SQLException {
		return supplier.apply(connection);
	}

	@Override
	public void rollback() throws SQLException {
		connection.rollback();
	}

	@Override
	public void commit() throws SQLException {
		connection.commit();
	}

	@Override
	public boolean hasSave() {
		return !saves.isEmpty();
	}

	@Override
	public void pushSave() throws SQLException {
		saves.push(connection.setSavepoint());
	}

	@Override
	public void popSave() throws SQLException {
		connection.releaseSavepoint(saves.pop());
	}

	@Override
	public boolean isClosed() throws SQLException {
		return connection.isClosed();
	}

	@Override
	public synchronized void close() throws SQLException {
		connection.rollback();
		connection.setAutoCommit(true);
		connection.close();
	}
}

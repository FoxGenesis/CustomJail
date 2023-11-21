package net.foxgenesis.customjail.database;

import java.sql.SQLException;

public interface Transaction<T> extends AutoCloseable {

	void rollback() throws SQLException;

	void commit() throws SQLException;

	T get() throws SQLException;

	void pushSave() throws SQLException;

	void popSave() throws SQLException;

	boolean hasSave();

	boolean isClosed() throws SQLException;

	@Override
	void close() throws SQLException;
}

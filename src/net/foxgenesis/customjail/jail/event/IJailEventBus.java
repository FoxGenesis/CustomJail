package net.foxgenesis.customjail.jail.event;

public interface IJailEventBus {

	/**
	 * Add a {@link JailEventListener} to this event bus.
	 * 
	 * @param listener - listener to add
	 * 
	 * @return Returns {@code true} if operation completed without errors
	 * 
	 * @throws NullPointerException Thrown if {@code listener} is null
	 */
	public boolean addListener(JailEventListener listener) throws NullPointerException;

	/**
	 * Remove a {@link JailEventListener} from this event bus.
	 * 
	 * @param listener - listener to remove
	 * 
	 * @return Returns {@code true} if operation completed without errors
	 * 
	 * @throws NullPointerException Thrown if {@code listener} is null
	 */
	public boolean removeListener(JailEventListener listener) throws NullPointerException;
}

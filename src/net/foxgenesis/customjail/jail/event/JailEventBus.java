package net.foxgenesis.customjail.jail.event;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JailEventBus implements IJailEventBus, Closeable {
	private static final Logger logger = LoggerFactory.getLogger(JailEventBus.class);

	private final ConcurrentHashMap<Class<? extends IJailEvent>, Set<MethodEntry<JailEventListener>>> map;
	private final ExecutorService executor;

	public JailEventBus(@Nullable ExecutorService executor) {
		this.executor = Objects.requireNonNullElse(executor, ForkJoinPool.commonPool());
		this.map = new ConcurrentHashMap<>();
	}

	public void fireEvent(IJailEvent event) {
		Class<?> c = event.getClass();

		if (map.containsKey(c)) {
			logger.debug("Firing event [{}]", event.getClass().getName());

			for (MethodEntry<JailEventListener> entry : map.get(c))
				CompletableFuture.runAsync(() -> {
					try {
						entry.method.invoke(entry.instance, event);
					} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
						logger.error("Error while invoking event method [" + c + "]", e);
						throw new CompletionException(e);
					}
				}, executor);
		}
	}

	@Override
	public boolean addListener(JailEventListener listener) throws NullPointerException {
		Objects.requireNonNull(listener);
		Map<Class<? extends IJailEvent>, Set<MethodEntry<JailEventListener>>> eventMethods = getMethods(listener);

		// If no methods are declared, return true
		if (eventMethods.isEmpty())
			return true;

		// Add methods to bus map
		for (Class<? extends IJailEvent> c : eventMethods.keySet())
			map.merge(c, eventMethods.get(c), (s1, s2) -> {
				Set<MethodEntry<JailEventListener>> newSet = new HashSet<>(s1);
				newSet.addAll(s2);
				return newSet;
			});
		return true;
	}

	@Override
	public boolean removeListener(JailEventListener listener) throws NullPointerException {
		Objects.requireNonNull(listener);
		Map<Class<? extends IJailEvent>, Set<MethodEntry<JailEventListener>>> eventMethods = getMethods(listener);

		// If no methods are declared, return true
		if (eventMethods.isEmpty())
			return true;

		// Remove event methods from bus map
		for (Class<? extends IJailEvent> c : eventMethods.keySet())
			map.computeIfPresent(c, (key, value) -> {
				Set<MethodEntry<JailEventListener>> methods = eventMethods.get(key);
				int newSize = value.size() - methods.size();

				if (newSize == 0)
					return null;

				Set<MethodEntry<JailEventListener>> newSet = new HashSet<>(value);
				newSet.removeAll(methods);
				return newSet;
			});
		return true;
	}

	@NotNull
	public Executor getExecutor() {
		return executor;
	}

	public int size() {
		return map.size();
	}

	@Override
	public void close() throws IOException {
		if (executor != ForkJoinPool.commonPool())
			executor.shutdown();
	}

	public synchronized void awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		if (executor != ForkJoinPool.commonPool())
			executor.awaitTermination(timeout, unit);
	}

	@NotNull
	private static Map<Class<? extends IJailEvent>, Set<MethodEntry<JailEventListener>>> getMethods(
			JailEventListener listener) {
		Map<Class<? extends IJailEvent>, Set<MethodEntry<JailEventListener>>> map = new HashMap<>();
		Class<? extends JailEventListener> class1 = listener.getClass();
		for (Method method : class1.getDeclaredMethods()) {
			// Only use event methods
			if (method.getParameterCount() != 1)
				continue;
			// Only use normal methods
			if (method.isSynthetic() || method.isBridge())
				continue;
			// Only use accessible methods
			if (!method.trySetAccessible())
				continue;

			// Add to map if method parameter is of our event type
			Class<?> pType = method.getParameterTypes()[0];
			if (IJailEvent.class.isAssignableFrom(pType))
				map.putIfAbsent((Class<? extends IJailEvent>) pType, Set.of(new MethodEntry<>(listener, method)));
		}

		return map;
	}

	private static record MethodEntry<T>(T instance, Method method) {}
}

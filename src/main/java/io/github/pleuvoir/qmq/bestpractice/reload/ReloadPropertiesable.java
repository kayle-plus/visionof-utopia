package io.github.pleuvoir.qmq.bestpractice.reload;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.StringUtils;

/**
 * 支持自动重载的 properties 操作
 * @author pleuvoir
 *
 */
public class ReloadPropertiesable extends ReloadPropertiesableSupport.Adapter {

	private final String propPath;
	private volatile File file;
	private volatile long lastModified;
	private volatile Map<String, String> config;
	private CountDownLatch countDownLatch = new CountDownLatch(1);
	private AtomicBoolean loaded = new AtomicBoolean(false);

	public ReloadPropertiesable(String propPath) {
		this.propPath = propPath;
		if (!loaded.get()) {
			this.file = getFileByName(propPath);
			startWatcher();
			try {
				countDownLatch.await();
			} catch (InterruptedException e) {
				throw new RuntimeException("await checkWatch failed", e);
			}
		}
		loaded.compareAndSet(false, true);
	}

	private void startWatcher() {

		ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "[" + propPath + "]-config-watcher");
			t.setDaemon(true);
			return t;
		});

		executorService.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				checkWatch();
				if (!loaded.get()) {
					countDownLatch.countDown();
				}
			}
		}, 0L, 10L, TimeUnit.SECONDS);
	}

	private void checkWatch() {
		final long lastModified = getLastModified();
		if (lastModified == this.lastModified) {
			return;
		}
		this.lastModified = lastModified;
		onConfigModified();
	}

	private File getFileByName(final String name) {
		try {
			final URL res = this.getClass().getClassLoader().getResource(name);
			if (res == null) {
				return null;
			}
			return Paths.get(res.toURI()).toFile();
		} catch (URISyntaxException e) {
			throw new RuntimeException("load config file failed", e);
		}
	}

	long getLastModified() {
		if (file == null) {
			file = getFileByName(propPath);
		}

		if (file == null) {
			return 0;
		} else {
			return file.lastModified();
		}
	}

	synchronized void onConfigModified() {
		if (file == null) {
			return;
		}
		loadConfig();
	}

	private void loadConfig() {
		try {
			final Properties p = new Properties();
			try (Reader reader = new BufferedReader(new FileReader(file))) {
				p.load(reader);
			}
			final Map<String, String> map = new LinkedHashMap<>(p.size());
			for (String key : p.stringPropertyNames()) {
				map.put(key, StringUtils.trim(p.getProperty(key)));
			}
			config = Collections.unmodifiableMap(map);
		} catch (IOException e) {
			throw new RuntimeException("load local config failed. config: " + file.getAbsolutePath(), e);
		}
	}

	@Override
	protected Map<String, String> getConfig() {
		return this.config;
	}

}

package io.github.pleuvoir.apollo;

import java.util.Map;

import org.springframework.core.env.MapPropertySource;

public abstract class RefreshablePropertySource extends MapPropertySource {


	// source 为 map
	public RefreshablePropertySource(String name, Map<String, Object> source) {
		super(name, source);
	}

	@Override
	public Object getProperty(String name) {
		return this.source.get(name);
	}

	/**
	 * refresh property
	 */
	protected abstract void refresh();

}

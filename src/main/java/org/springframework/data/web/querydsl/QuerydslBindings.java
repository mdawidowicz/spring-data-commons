/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.web.querydsl;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.mapping.PropertyPath;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.mysema.query.types.Path;
import com.mysema.query.types.PathMetadata;
import com.mysema.query.types.Predicate;

/**
 * {@link QuerydslBindings} allows definition of path specific {@link SingleValueBinding}.
 * 
 * <pre>
 * <code>
 * new QuerydslBindings() {
 *   {
 *     bind(QUser.user.address.city).using((path, value) -> path.like(value.toString()));
 *     bind("lastname").using((path, value) -> path.like(value.toString()));
 *   }
 * }
 * </code>
 * </pre>
 * 
 * @author Christoph Strobl
 * @author Oliver Gierke
 * @since 1.11
 */
public class QuerydslBindings {

	private final Map<String, PathAndBinding<?, ?>> pathSpecs;
	private final Map<Class<?>, PathAndBinding<?, ?>> typeSpecs;
	private final Set<String> whiteList;
	private final Set<String> blackList;

	private boolean excludeUnlistedProperties;

	/**
	 * Creates a new {@link QuerydslBindings} instance.
	 */
	public QuerydslBindings() {

		this.pathSpecs = new LinkedHashMap<String, PathAndBinding<?, ?>>();
		this.typeSpecs = new LinkedHashMap<Class<?>, PathAndBinding<?, ?>>();
		this.whiteList = new HashSet<String>();
		this.blackList = new HashSet<String>();
	}

	/**
	 * Returns a new {@link PathBinder} for the given {@link Path}s to define bindings for them.
	 * 
	 * @param paths must not be {@literal null} or emtpy.
	 * @return
	 */
	public final <T extends Path<S>, S> PathBinder<T, S> bind(T... paths) {
		return new PathBinder<T, S>(paths);
	}

	/**
	 * Defines a binding for the given
	 * 
	 * @param paths
	 * @return
	 */
	public final PropertyBinder bind(String... paths) {
		return new PropertyBinder(Arrays.asList(paths));
	}

	public final <T> TypeBinder<T> bind(Class<T> type) {
		return new TypeBinder<T>(type);
	}

	/**
	 * Exclude properties from binding. Exclusion of all properties of a nested type can be done by exclusion on a higher
	 * level. E.g. {@code address} would exclude both {@code address.city} and {@code address.street}.
	 * 
	 * @param paths must not be {@literal null} or empty.
	 */
	public final void excluding(Path<?>... paths) {

		Assert.notEmpty(paths, "At least one path has to be provided!");

		for (Path<?> path : paths) {
			this.blackList.add(toDotPath(path));
		}
	}

	/**
	 * Exclude properties from binding. Exclusion of all properties of a nested type can be done by exclusion on a higher
	 * level. E.g. {@code address} would exclude both {@code address.city} and {@code address.street}.
	 * 
	 * @param properties
	 */
	public final void excluding(String... properties) {
		this.blackList.addAll(Arrays.asList(properties));
	}

	/**
	 * Include properties for binding. Include the property considered a binding candidate.
	 * 
	 * @param properties must not be {@literal null} or empty.
	 */
	public final void including(Path<?>... paths) {

		Assert.notEmpty(paths, "At least one path has to be provided!");

		for (Path<?> path : paths) {
			this.whiteList.add(toDotPath(path));
		}
	}

	/**
	 * Include properties for binding. Include the property considered a binding candidate.
	 * 
	 * @param properties
	 */
	public final void including(String... properties) {

		Assert.notEmpty(properties, "At least one property has to be provided!");

		this.whiteList.addAll(Arrays.asList(properties));
	}

	/**
	 * Returns whether to exclude all properties for which no explicit binding has been defined or it has been explicitly
	 * white-listed. This defaults to {@literal false} which means that for properties without an explicitly defined
	 * binding a type specific default binding will be applied.
	 * 
	 * @param excludeUnlistedProperties
	 * @return
	 * @see #including(String...)
	 * @see #including(Path...)
	 */
	public final QuerydslBindings excludeUnlistedProperties(boolean excludeUnlistedProperties) {

		this.excludeUnlistedProperties = excludeUnlistedProperties;
		return this;
	}

	/**
	 * Checks if a given {@link PropertyPath} should be visible for binding values.
	 * 
	 * @param path
	 * @return
	 */
	boolean isPathVisible(PropertyPath path) {

		List<String> segments = Arrays.asList(path.toDotPath().split("\\."));

		for (int i = 1; i <= segments.size(); i++) {

			if (!isPathVisible(StringUtils.collectionToDelimitedString(segments.subList(0, i), "."))) {

				// check if full path is on whitelist if though partial one is not
				if (!whiteList.isEmpty()) {
					return whiteList.contains(path.toDotPath());
				}

				return false;
			}
		}

		return true;
	}

	/**
	 * Returns the {@link SingleValueBinding} for the given {@link PropertyPath}. Prefers a path configured for the
	 * specific path but falls back to the builder registered for a given type.
	 * 
	 * @param path must not be {@literal null}.
	 * @return
	 */
	@SuppressWarnings("unchecked")
	<S extends Path<T>, T> MultiValueBinding<S, T> getBindingForPath(PropertyPath path) {

		Assert.notNull(path, "PropertyPath must not be null!");

		PathAndBinding<S, T> pathAndBinding = (PathAndBinding<S, T>) pathSpecs.get(path.toDotPath());

		if (pathAndBinding != null) {
			return pathAndBinding.getBinding();
		}

		pathAndBinding = (PathAndBinding<S, T>) typeSpecs.get(path.getLeafProperty().getType());

		return pathAndBinding == null ? null : pathAndBinding.getBinding();
	}

	/**
	 * Returns a {@link Path} for the {@link PropertyPath} instance.
	 * 
	 * @param path must not be {@literal null}.
	 * @return
	 */
	Path<?> getExistingPath(PropertyPath path) {

		PathAndBinding<?, ?> pathAndBuilder = pathSpecs.get(path.toDotPath());
		return pathAndBuilder == null ? null : pathAndBuilder.getPath();
	}

	/**
	 * Returns whether the given path is visible, which means either on the white list or not on the black list if no
	 * white list configured.
	 * 
	 * @param path must not be {@literal null}.
	 * @return
	 */
	private boolean isPathVisible(String path) {

		if (!whiteList.isEmpty()) {

			if (whiteList.contains(path)) {
				return true;
			}

			return false;
		}

		return excludeUnlistedProperties ? false : !blackList.contains(path);
	}

	/**
	 * Returns the property path for the given {@link Path}.
	 * 
	 * @param path can be {@literal null}.
	 * @return
	 */
	private String toDotPath(Path<?> path) {

		if (path == null) {
			return "";
		}

		PathMetadata<?> metadata = path.getMetadata();

		return path.toString().substring(metadata.getRoot().getMetadata().getName().length() + 1);
	}

	/**
	 * A binder for {@link Path}s.
	 *
	 * @author Oliver Gierke
	 */
	protected final class PathBinder<P extends Path<? extends T>, T> {

		private final List<P> paths;

		/**
		 * Creates a new {@link PathBinder} for the given {@link Path}s.
		 * 
		 * @param paths must not be {@literal null} or empty.
		 */
		public PathBinder(P... paths) {

			Assert.notEmpty(paths, "At least one path has to be provided!");
			this.paths = Arrays.asList(paths);
		}

		/**
		 * Defines the given {@link SingleValueBinding} to be used for the paths,
		 * 
		 * @param binding must not be {@literal null}.
		 * @return
		 */
		public void single(SingleValueBinding<P, T> binding) {

			Assert.notNull(binding, "Binding must not be null!");

			multi(new MultiValueBindingAdapter<P, T>(binding));
		}

		public void multi(MultiValueBinding<P, T> binding) {

			Assert.notNull(binding, "Binding must not be null!");

			for (P path : paths) {
				QuerydslBindings.this.pathSpecs.put(toDotPath(path), new PathAndBinding<P, T>(path, binding));
			}
		}
	}

	protected final class TypeBinder<T> {

		private final Class<T> type;

		public TypeBinder(Class<T> type) {
			this.type = type;
		}

		public <P extends Path<T>> void single(SingleValueBinding<P, T> binding) {

			Assert.notNull(binding, "Binding must not be null!");
			multi(new MultiValueBindingAdapter<P, T>(binding));
		}

		public <P extends Path<T>> void multi(MultiValueBinding<P, T> binding) {

			Assert.notNull(binding, "Binding must not be null!");
			QuerydslBindings.this.typeSpecs.put(type, new PathAndBinding<P, T>(null, binding));
		}
	}

	protected final class PropertyBinder {

		private final List<String> paths;

		private PropertyBinder(List<String> paths) {
			this.paths = paths;
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		public void using(SingleValueBinding<?, ?> binding) {

			Assert.notNull(binding, "Binding must not be null!");
			using(new MultiValueBindingAdapter(binding));
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		public void using(MultiValueBinding<?, ?> binding) {

			Assert.notNull(binding, "Binding must not be null!");

			for (String path : paths) {
				QuerydslBindings.this.pathSpecs.put(path, new PathAndBinding(null, binding));
			}
		}
	}

	/**
	 * @author Christoph Strobl
	 * @since 1.11
	 */
	private static class PathAndBinding<S extends Path<? extends T>, T> {

		private final Path<?> path;
		private final MultiValueBinding<S, T> binding;

		public PathAndBinding(S path, MultiValueBinding<S, T> binding) {

			this.path = path;
			this.binding = binding;
		}

		public Path<?> getPath() {
			return path;
		}

		public MultiValueBinding<S, T> getBinding() {
			return binding;
		}
	}

	/**
	 * @author Oliver Gierke
	 */
	static class MultiValueBindingAdapter<T extends Path<? extends S>, S> implements MultiValueBinding<T, S> {

		private final SingleValueBinding<T, S> delegate;

		/**
		 * @param delegate
		 */
		public MultiValueBindingAdapter(SingleValueBinding<T, S> delegate) {
			this.delegate = delegate;
		}

		/* 
		 * (non-Javadoc)
		 * @see org.springframework.data.web.querydsl.MultiValueBinding#bind(com.mysema.query.types.Path, java.util.Collection)
		 */
		@Override
		public Predicate bind(T path, Collection<? extends S> value) {
			return delegate.bind(path, value.iterator().next());
		}
	}
}
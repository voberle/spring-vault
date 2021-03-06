/*
 * Copyright 2018 the original author or authors.
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
package org.springframework.vault.core;

import java.util.List;
import java.util.Map;

import org.springframework.lang.Nullable;
import org.springframework.vault.support.Versioned;
import org.springframework.vault.support.Versioned.Metadata;
import org.springframework.vault.support.Versioned.Version;

/**
 * Interface that specifies a basic set of Vault operations using Vault's versioned
 * Key/Value (kv version 2) secret backend. Paths used in this operations interface are
 * relative and outgoing requests prepend paths with the according operation-specific
 * prefix.
 * <p/>
 * Clients using versioned Key/Value must be aware they are reading from a versioned
 * backend as the versioned Key/Value API (kv version 2) is different from the unversioned
 * Key/Value API (kv version 1).
 *
 * @author Mark Paluch
 * @since 2.1
 */
public interface VaultVersionedKeyValueOperations {

	/**
	 * Enumerate keys from a Vault path.
	 *
	 * @param path must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	@Nullable
	List<String> list(String path);

	/**
	 * Read the most recent secret at {@code path}.
	 *
	 * @param path must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	@Nullable
	default Versioned<Map<String, Object>> read(String path) {
		return read(path, Version.unversioned());
	}

	/**
	 * Read the requested {@link Version} of the secret at {@code path}.
	 *
	 * @param path must not be {@literal null}.
	 * @param version must not be {@literal null}.
	 * @return the data. May be {@literal null} if the path does not exist.
	 */
	@Nullable
	Versioned<Map<String, Object>> read(String path, Version version);

	/**
	 * Write the {@link Versioned versioned secret} at {@code path}. {@code body} may be
	 * either plain secrets (e.g. map) or {@link Versioned} objects. Using
	 * {@link Versioned} will apply versioning for Compare-and-Set (CAS).
	 *
	 * @param path must not be {@literal null}.
	 * @param body must not be {@literal null}.
	 * @return the resulting {@link Metadata}.
	 */
	Metadata write(String path, Object body);

	/**
	 * Delete latest version of the secret at {@code path}.
	 *
	 * @param path must not be {@literal null}.
	 */
	void delete(String path);

	/**
	 * Delete one or more {@link Version versions} of the secret at {@code path}.
	 *
	 * @param path must not be {@literal null}.
	 * @param versionsToDelete must not be {@literal null} or empty.
	 */
	void delete(String path, Version... versionsToDelete);

	/**
	 * Undelete (restore) one or more {@link Version versions} of the secret at
	 * {@code path}.
	 *
	 * @param path must not be {@literal null}.
	 * @param versionsToDelete must not be {@literal null} or empty.
	 */
	void undelete(String path, Version... versionsToDelete);

	/**
	 * Permanently remove the specified {@link Version versions} of the secret at
	 * {@code path}.
	 *
	 * @param path must not be {@literal null}.
	 * @param versionsToDelete must not be {@literal null} or empty.
	 */
	void destroy(String path, Version... versionsToDelete);
}

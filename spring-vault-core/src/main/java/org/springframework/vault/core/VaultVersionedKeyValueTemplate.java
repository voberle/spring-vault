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

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.Versioned;
import org.springframework.vault.support.Versioned.Metadata;
import org.springframework.vault.support.Versioned.Version;
import org.springframework.vault.support.Versioned.Metadata.MetadataBuilder;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * Default implementation of {@link VaultVersionedKeyValueOperations}.
 *
 * @author Mark Paluch
 * @since 2.1
 */
public class VaultVersionedKeyValueTemplate implements VaultVersionedKeyValueOperations {

	private final VaultOperations vaultOperations;

	private final String path;

	/**
	 * Create a new {@link VaultVersionedKeyValueTemplate} given {@link VaultOperations}
	 * and the mount {@code path}.
	 *
	 * @param vaultOperations must not be {@literal null}.
	 * @param path must not be empty or {@literal null}.
	 */
	public VaultVersionedKeyValueTemplate(VaultOperations vaultOperations, String path) {

		Assert.notNull(vaultOperations, "VaultOperations must not be null");
		Assert.hasText(path, "Path must not be empty");

		this.vaultOperations = vaultOperations;
		this.path = path;
	}

	@Nullable
	@Override
	public List<String> list(String path) {
		return vaultOperations.list(createBackendPath("metadata", path));
	}

	@Override
	@Nullable
	public Versioned<Map<String, Object>> read(String path, Version version) {

		Assert.hasText(path, "Path must not be empty");
		Assert.notNull(version, "Version must not be null");

		String secretPath = version.isVersioned() ? String.format(
				"%s/data/%s?version=%d", this.path, path, version.getVersion())
				: createDataPath(path);

		VaultResponse response = vaultOperations.doWithSession(restOperations -> {

			try {
				return restOperations.getForObject(secretPath, VaultResponse.class);
			}
			catch (HttpStatusCodeException e) {

				if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
					if (e.getResponseBodyAsString().contains("deletion_time")) {
						return VaultResponses.unwrap(e.getResponseBodyAsString(),
								VaultResponse.class);
					}

					return null;
				}

				throw VaultResponses.buildException(e, path);
			}
		});

		if (response == null) {
			return null;
		}

		Map<String, Object> responseData = response.getRequiredData();
		Metadata metadata = getMetadata((Map) responseData.get("metadata"));

		return Versioned.create((Map<String, Object>) responseData.get("data"), metadata);
	}

	@Override
	public Metadata write(String path, Object body) {

		Assert.hasText(path, "Path must not be empty");

		Map<Object, Object> data = new LinkedHashMap<>();
		Map<Object, Object> requestOptions = new LinkedHashMap<>();

		if (body instanceof Versioned) {

			Versioned<?> versioned = (Versioned<?>) body;

			data.put("data", versioned.getData());
			data.put("options", requestOptions);

			requestOptions.put("cas", versioned.getVersion().getVersion());
		}
		else {
			data.put("data", body);
		}

		VaultResponse response = vaultOperations.write(createDataPath(path), data);

		return getMetadata(response.getRequiredData());
	}

	private static Metadata getMetadata(Map<String, Object> responseMetadata) {

		MetadataBuilder builder = Metadata.builder();
		TemporalAccessor created_time = getDate(responseMetadata, "created_time");
		TemporalAccessor deletion_time = getDate(responseMetadata, "deletion_time");

		builder.createdAt(Instant.from(created_time));

		if (deletion_time != null) {
			builder.deletedAt(Instant.from(deletion_time));
		}

		if (Boolean.TRUE.equals(responseMetadata.get("destroyed"))) {
			builder.destroyed();
		}

		Integer version = (Integer) responseMetadata.get("version");
		builder.version(Version.from(version));

		return builder.build();
	}

	@Nullable
	private static TemporalAccessor getDate(Map<String, Object> responseMetadata,
			String key) {

		String date = (String) responseMetadata.getOrDefault(key, "");
		if (StringUtils.hasText(date)) {
			return DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(date);
		}
		return null;
	}

	@Override
	public void delete(String path) {

		Assert.hasText(path, "Path must not be empty");

		vaultOperations.delete(createDataPath(path));
	}

	@Override
	public void delete(String path, Version... versionsToDelete) {

		Assert.hasText(path, "Path must not be empty");
		Assert.noNullElements(versionsToDelete, "Versions must not be null");

		if (versionsToDelete.length == 0) {
			delete(path);
			return;
		}

		List<Integer> versions = toVersionList(versionsToDelete);

		vaultOperations.write(createBackendPath("delete", path),
				Collections.singletonMap("versions", versions));
	}

	private static List<Integer> toVersionList(Version[] versionsToDelete) {
		return Arrays.stream(versionsToDelete).filter(Version::isVersioned)
				.map(Version::getVersion).collect(Collectors.toList());
	}

	@Override
	public void undelete(String path, Version... versionsToDelete) {

		Assert.hasText(path, "Path must not be empty");
		Assert.noNullElements(versionsToDelete, "Versions must not be null");

		List<Integer> versions = toVersionList(versionsToDelete);

		vaultOperations.write(createBackendPath("undelete", path),
				Collections.singletonMap("versions", versions));
	}

	@Override
	public void destroy(String path, Version... versionsToDelete) {

		Assert.hasText(path, "Path must not be empty");
		Assert.noNullElements(versionsToDelete, "Versions must not be null");

		List<Integer> versions = toVersionList(versionsToDelete);

		vaultOperations.write(createBackendPath("destroy", path),
				Collections.singletonMap("versions", versions));
	}

	private String createDataPath(String path) {
		return createBackendPath("data", path);
	}

	private String createBackendPath(String segment, String path) {
		return String.format("%s/%s/%s", this.path, segment, path);
	}
}

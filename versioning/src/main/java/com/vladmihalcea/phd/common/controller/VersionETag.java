package com.vladmihalcea.phd.common.controller;

import org.springframework.http.ETag;
import org.springframework.http.ResponseEntity;

/**
 * Maps the aggregate root version to the entity tag of the
 * {@code If-Match} request header and the {@code ETag} response header.
 * Apart from the {@code ETag} header, a write responds as in the {@code core} module,
 * with {@code 200 OK} and the same body, so that the two modules differ only in the version.
 *
 * @author Vlad Mihalcea
 */
public final class VersionETag {

    private VersionETag() {
    }

    public static short parse(String entityTag) {
        return Short.parseShort(ETag.create(entityTag).tag());
    }

    public static String format(short version) {
        return ETag.quoteETagIfNecessary(String.valueOf(version));
    }

    public static ResponseEntity<Void> ok(short version) {
        return ResponseEntity.ok().eTag(format(version)).build();
    }

    public static <T> ResponseEntity<T> ok(short version, T body) {
        return ResponseEntity.ok().eTag(format(version)).body(body);
    }
}

package com.vladmihalcea.phd.jpa.event;

import org.hibernate.Hibernate;
import org.hibernate.LockMode;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.TransactionCompletionCallbacks.BeforeCompletionCallback;
import org.hibernate.event.spi.EventSource;

import java.util.Objects;

/**
 * @author Vlad Mihalcea
 */
final class RootVersion {

    private RootVersion() {
    }

    /**
     * Schedules the aggregate root version increment for the end of the current transaction.
     * <p>
     * Unlike {@link LockMode#OPTIMISTIC_FORCE_INCREMENT}, the increment is skipped if the flush
     * has already incremented the root version because the root was modified as well,
     * so that a transaction increments the root version only once.
     * <p>
     * A root that was inserted, updated, or already scheduled in the current transaction
     * holds a lock mode at least as strong as {@link LockMode#OPTIMISTIC_FORCE_INCREMENT}.
     * A child entity removed from the root collection no longer references its root,
     * but Hibernate increments the root version since the collection is dirty.
     *
     * @return {@code true} if the root version increment was scheduled
     */
    static boolean increment(EventSource session, Object root) {
        if (root == null) {
            return false;
        }
        Object rootEntity = Hibernate.unproxy(root);
        EntityEntry rootEntry = session.getPersistenceContextInternal().getEntry(rootEntity);
        if (rootEntry == null ||
            rootEntry.getStatus().isDeletedOrGone() ||
            !LockMode.OPTIMISTIC_FORCE_INCREMENT.greaterThan(rootEntry.getLockMode())) {
            return false;
        }
        rootEntry.setLockMode(LockMode.OPTIMISTIC_FORCE_INCREMENT);
        Object version = rootEntry.getVersion();
        session.getActionQueue().registerCallback(
            (BeforeCompletionCallback) completingSession -> {
                EntityEntry entry = completingSession.getPersistenceContextInternal()
                    .getEntry(rootEntity);
                if (entry != null && Objects.equals(entry.getVersion(), version)) {
                    Object nextVersion = entry.getPersister()
                        .forceVersionIncrement(entry.getId(), version, completingSession);
                    entry.forceLocked(rootEntity, nextVersion);
                }
            }
        );
        return true;
    }
}

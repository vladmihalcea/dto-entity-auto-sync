package com.vladmihalcea.phd.jpa.event;

import com.vladmihalcea.phd.jpa.domain.RootAware;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.Status;
import org.hibernate.event.spi.FlushEntityEvent;
import org.hibernate.event.spi.FlushEntityEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Increments the aggregate root version when a child entity is updated or deleted.
 *
 * @author Vlad Mihalcea
 */
public class RootAwareUpdateAndDeleteEventListener implements FlushEntityEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RootAwareUpdateAndDeleteEventListener.class);

    public static final RootAwareUpdateAndDeleteEventListener INSTANCE = new RootAwareUpdateAndDeleteEventListener();

    @Override
    public void onFlushEntity(FlushEntityEvent event) throws HibernateException {
        final EntityEntry entry = event.getEntityEntry();
        final Object entity = event.getEntity();
        final boolean mightBeDirty = entry.requiresDirtyCheck(entity);

        if (mightBeDirty && entity instanceof RootAware<?> rootAware) {
            if (isEntityUpdated(event)) {
                Object root = rootAware.root();
                if (RootVersion.increment(event.getSession(), root)) {
                    LOGGER.info(
                        "Incrementing the [{}] entity version " +
                        "because the [{}] child entity has been updated",
                        root,
                        entity
                    );
                }
            } else if (isEntityDeleted(event)) {
                Object root = rootAware.root();
                if (RootVersion.increment(event.getSession(), root)) {
                    LOGGER.info(
                        "Incrementing the [{}] entity version " +
                        "because the [{}] child entity has been deleted",
                        root,
                        entity
                    );
                }
            }
        }
    }

    private boolean isEntityUpdated(FlushEntityEvent event) {
        final EntityEntry entry = event.getEntityEntry();
        final Object entity = event.getEntity();

        int[] dirtyProperties;
        EntityPersister persister = entry.getPersister();
        final Object[] values = event.getPropertyValues();
        SessionImplementor session = event.getSession();

        if (event.hasDatabaseSnapshot()) {
            dirtyProperties = persister.findModified(
                event.getDatabaseSnapshot(),
                values,
                entity,
                session
            );
        } else {
            dirtyProperties = persister.findDirty(
                values,
                entry.getLoadedState(),
                entity,
                session
            );
        }

        return dirtyProperties != null;
    }

    private boolean isEntityDeleted(FlushEntityEvent event) {
        return event.getEntityEntry().getStatus() == Status.DELETED;
    }
}

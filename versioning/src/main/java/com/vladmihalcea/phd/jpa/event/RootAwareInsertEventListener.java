package com.vladmihalcea.phd.jpa.event;

import com.vladmihalcea.phd.jpa.domain.RootAware;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.event.spi.PersistContext;
import org.hibernate.event.spi.PersistEvent;
import org.hibernate.event.spi.PersistEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Increments the aggregate root version when a child entity is inserted.
 *
 * @author Vlad Mihalcea
 */
public class RootAwareInsertEventListener implements PersistEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RootAwareInsertEventListener.class);

    public static final RootAwareInsertEventListener INSTANCE = new RootAwareInsertEventListener();

    @Override
    public void onPersist(PersistEvent event) throws HibernateException {
        final Object entity = event.getObject();

        if (entity instanceof RootAware<?> rootAware && isEntityInserted(event)) {
            Object root = rootAware.root();
            if (RootVersion.increment(event.getSession(), root)) {
                LOGGER.info(
                    "Incrementing the [{}] entity version " +
                    "because the [{}] child entity has been inserted",
                    root,
                    entity
                );
            }
        }
    }

    @Override
    public void onPersist(PersistEvent event, PersistContext persistContext)
            throws HibernateException {
        onPersist(event);
    }

    /**
     * Every flush cascades the persist event to all the managed child entities,
     * so only an entity whose row is not yet in the database is being inserted.
     */
    private boolean isEntityInserted(PersistEvent event) {
        EntityEntry entry = event.getSession().getPersistenceContextInternal()
            .getEntry(event.getObject());
        return entry != null && !entry.isExistsInDatabase();
    }
}

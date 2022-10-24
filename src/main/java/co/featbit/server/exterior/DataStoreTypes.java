package co.featbit.server.exterior;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Contains information about the internal data model for storage objects
 * <p>
 * The implementation of internal components is not public to application code (although of course developers can easily
 * look at the code or the data) so that changes to SDK implementation details will not be breaking changes to the application.
 * This class provide a high-level description of storage objects so that custom integration code or test code can
 * store or serialize them.
 */
public abstract class DataStoreTypes {

    /**
     * The {@link Category} instance that describes feature flag data.
     * <p>
     * Applications should not need to reference this object directly.It is public so that custom integrations
     * and test code can serialize or deserialize data or inject it into a data storage.
     */
    public final static Category FEATURES = new Category("featureFlags", "ff");

    public final static Category SEGMENTS = new Category("segments", "seg");

    public final static Category DATATEST = new Category("datatests", "test");

    /**
     * An enumeration of all supported {@link Category} types.
     * <p>
     * Applications should not need to reference this object directly. It is public so that custom data storage
     * implementations can determine what kinds of model objects may need to be stored.
     */

    public final List<Category> FFC_ALL_CATS = ImmutableList.of(FEATURES, SEGMENTS, DATATEST);

    private DataStoreTypes() {
    }

    /**
     * Represents a separated namespace of storable data items.
     * <p>
     * The SDK passes instances of this type to the data store to specify whether it is referring to
     * a feature flag, a user segment, etc
     */
    public static final class Category implements Serializable {
        private final String name;

        private final String tag;

        private Category(String name, String tag) {
            this.name = name;
            this.tag = tag;
        }

        /**
         * build an external category
         *
         * @param name the name of namespace
         * @return a Category
         */
        public static Category of(String name) {
            return new Category(name, "unknown");
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("name", name)
                    .add("tag", tag)
                    .toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Category category = (Category) o;
            return Objects.equals(name, category.name) && Objects.equals(tag, category.tag);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, tag);
        }
    }

    /**
     * interface for the object to represent a versioned/timestamped data
     */

    public interface Item {
        Integer FFC_FEATURE_FLAG = 100;
        Integer FFC_ARCHIVED_ITEM = 200;
        Integer FFC_SEGMENT = 300;

        /**
         * return the unique id
         *
         * @return a string
         */
        String getId();

        /**
         * return true if object is archived
         *
         * @return true if object is archived
         */
        boolean isArchived();

        /**
         * return the version/timestamp of the object
         *
         * @return a long value
         */
        Long getTimestamp();

        /**
         * return the type of versioned/timestamped object
         *
         * @return an integer
         */
        Integer getType();
    }


}

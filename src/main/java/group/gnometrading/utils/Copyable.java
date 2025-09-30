package group.gnometrading.utils;

/**
 * An interface representing a DTO (Data Transfer Object) or component that can be copied from another instance.
 * Implementations of this interface provide a {@code copyFrom()} method to copy the state of another object.
 *
 * @param <T> type of the copyable object
 */
public interface Copyable<T extends Copyable<T>> {
    /**
     * Copies the state of another object into this object.
     *
     * @param other the object to copy from
     */
    void copyFrom(T other);
}

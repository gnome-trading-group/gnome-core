package group.gnometrading.utils;

/**
 * An interface representing a DTO (Data Transfer Object) or component that can be reset to its initial state.
 * Implementations of this interface provide a {@code reset()} method to reset the state of the object.
 */
public interface Resettable {
    /**
     * Resets the state of the object to its initial state.
     * Implementations should restore the object's internal fields or properties
     * to their default values or the values set during initialization.
     */
    void reset();
}

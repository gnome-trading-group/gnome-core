package group.gnometrading.collections.buffer;

@FunctionalInterface
public interface ArrayFactory<T> {
    T[] createArray(int size);
}

package group.gnometrading.collections.buffer;

@FunctionalInterface
public interface MessageConsumer<T> {
    void accept(T message);
}

package group.gnometrading.pools;

public interface Pool<T> {
    PoolNode<T> acquire();
    void release(PoolNode<T> node);

    void releaseAll();
}

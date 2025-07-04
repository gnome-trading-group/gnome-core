package group.gnometrading.concurrent;

@FunctionalInterface
public interface ErrorHandler {
    void onError(Throwable t);
}
package net.vayzd.orbit.database;

/**
 * Represents a method which may be called once a result has been computed
 * asynchronously.
 *
 * @param <T> the type of result
 */
@FunctionalInterface
public interface Completable<T> {

    /**
     * Called when the computation is done.
     *
     * @param result the result of the computation
     */
    void complete(T result);
}

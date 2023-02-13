package com.flipkart.krystal.utils;

import static java.lang.Math.max;

import java.util.Deque;
import java.util.LinkedList;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MultiLeasePool<T> implements AutoCloseable {

  private final Supplier<T> factory;

  private final int maxActiveLeasesPerObject;
  private final Consumer<T> destroyer;
  private double peakAvgActiveLeasesPerObject;
  private int maxPoolSize;

  private final Deque<PooledObject<T>> queue = new LinkedList<>();
  private volatile boolean closed;

  public MultiLeasePool(Supplier<T> factory, int maxActiveLeasesPerObject, Consumer<T> destroyer) {
    this.factory = factory;
    this.maxActiveLeasesPerObject = maxActiveLeasesPerObject;
    this.destroyer = destroyer;
  }

  public final synchronized Lease<T> lease() {
    if (closed) {
      throw new IllegalStateException("MultiLeasePool already closed");
    }
    PooledObject<T> pooledObject = queue.poll();
    int count = queue.size();
    while (pooledObject != null
        && count-- > 0
        && (pooledObject.shouldDelete()
            || pooledObject.activeLeases() == maxActiveLeasesPerObject)) {
      if (!pooledObject.shouldDelete()) {
        queue.add(pooledObject);
      }
      pooledObject = queue.poll();
    }
    if (pooledObject == null || pooledObject.activeLeases() == maxActiveLeasesPerObject) {
      pooledObject = addNewForLeasing();
    } else {
      pooledObject.incrementActiveLeases();
    }
    queue.add(pooledObject);
    peakAvgActiveLeasesPerObject =
        max(
            peakAvgActiveLeasesPerObject,
            queue.stream().mapToInt(PooledObject::activeLeases).average().orElse(0));
    return new Lease<>(pooledObject, this::giveBack);
  }

  private synchronized void giveBack(PooledObject<T> pooledObject) {
    pooledObject.decrementActiveLeases();
  }

  private PooledObject<T> addNewForLeasing() {
    T t = factory.get();
    PooledObject<T> pooledObject = new PooledObject<>(t, maxActiveLeasesPerObject(), destroyer);
    pooledObject.incrementActiveLeases();
    queue.add(pooledObject);
    maxPoolSize = max(maxPoolSize, queue.size());
    return pooledObject;
  }

  public final int maxActiveLeasesPerObject() {
    return maxActiveLeasesPerObject;
  }

  public final double peakAvgActiveLeasesPerObject() {
    return peakAvgActiveLeasesPerObject;
  }

  public final int maxPoolSize() {
    return maxPoolSize;
  }

  @Override
  public void close() {
    this.closed = true;
    PooledObject<T> pooledObject;
    while ((pooledObject = queue.pollLast()) != null) {
      destroyer.accept(pooledObject.ref());
    }
  }

  public static final class Lease<T> implements AutoCloseable {

    private PooledObject<T> pooledObject;
    private final Consumer<PooledObject<T>> giveback;

    private Lease(PooledObject<T> pooledObject, Consumer<PooledObject<T>> giveback) {
      this.pooledObject = pooledObject;
      this.giveback = giveback;
    }

    public T get() {
      if (pooledObject == null) {
        throw new IllegalStateException("Lease already released");
      }
      return pooledObject.ref();
    }

    @Override
    public void close() {
      if (pooledObject != null) {
        giveback.accept(pooledObject);
        pooledObject = null;
      }
    }
  }

  private static final class PooledObject<T> {

    private final T ref;
    private final int maxActiveLeasesPerObject;
    private int activeLeases = 0;
    private int markForDeletion;
    private final Consumer<T> destroyer;

    private PooledObject(T ref, int maxActiveLeasesPerObject, Consumer<T> destroyer) {
      this.ref = ref;
      this.maxActiveLeasesPerObject = maxActiveLeasesPerObject;
      this.destroyer = destroyer;
    }

    private T ref() {
      return ref;
    }

    private int activeLeases() {
      return activeLeases;
    }

    private void incrementActiveLeases() {
      activeLeases++;
      markForDeletion = 0;
    }

    private void decrementActiveLeases() {
      activeLeases--;
      if (activeLeases() == 0 && maxActiveLeasesPerObject > 1) {
        markForDeletion++;
      }
      if (shouldDelete() && activeLeases() == 0) {
        destroyer.accept(ref());
      }
    }

    private boolean shouldDelete() {
      return markForDeletion
          > 100; // This number is a bit random - need to find a better way to calibrate this.
    }
  }
}
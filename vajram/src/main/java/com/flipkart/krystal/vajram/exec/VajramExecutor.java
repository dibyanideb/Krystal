package com.flipkart.krystal.vajram.exec;

import com.flipkart.krystal.vajram.ApplicationRequestContext;
import com.flipkart.krystal.vajram.VajramID;
import com.flipkart.krystal.vajram.VajramRequest;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.Nullable;

public interface VajramExecutor<C extends ApplicationRequestContext> extends AutoCloseable {

  <T> CompletableFuture<@Nullable T> execute(
      VajramID vajramId, Function<C, VajramRequest> vajramInputProviders);

  /** Flushes any pending requests */
  void flush();

  // Override to suppress "throws Exception"
  @Override
  void close();
}

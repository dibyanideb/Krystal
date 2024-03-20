package com.flipkart.krystal.vajramexecutor.krystex;

import com.flipkart.krystal.krystex.kryon.DefaultDependantChain;
import com.flipkart.krystal.krystex.kryon.DependantChain;
import com.flipkart.krystal.krystex.kryon.DependantChainStart;
import com.flipkart.krystal.krystex.kryon.KryonDefinitionRegistry;
import com.flipkart.krystal.krystex.logicdecoration.LogicExecutionContext;
import com.flipkart.krystal.krystex.logicdecoration.OutputLogicDecorator;
import com.flipkart.krystal.krystex.logicdecoration.OutputLogicDecoratorConfig.DecoratorContext;
import com.flipkart.krystal.vajram.Vajram;
import com.flipkart.krystal.vajram.batching.FacetsConverter;
import com.flipkart.krystal.vajram.batching.InputBatcher;
import com.flipkart.krystal.vajram.facets.FacetValuesAdaptor;
import com.flipkart.krystal.vajram.tags.AnnotationTags;
import com.flipkart.krystal.vajram.tags.VajramTags;
import com.google.common.collect.ImmutableSet;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public record InputBatcherConfig(
    Function<LogicExecutionContext, String> instanceIdGenerator,
    Predicate<LogicExecutionContext> shouldModulate,
    Function<BatcherContext, OutputLogicDecorator> decoratorFactory) {

  /**
   * Creates a default InputBatcherConfig which guarantees that every unique {@link DependantChain}
   * of a vajram gets its own {@link InputBatchingDecorator} and its own corresponding {@link
   * InputBatcher}. The instance id corresponding to a particular {@link DependantChain} is of the
   * form:
   *
   * <p>{@code [Start]>vajramId_1:dep_1>vajramId_2:dep_2>....>vajramId_n:dep_n}
   *
   * @param inputBatcherSupplier Supplies the {@link InputBatcher} corresponding to an {@link
   *     InputBatchingDecorator}. This supplier is guaranteed to be called exactly once for every
   *     unique {@link InputBatchingDecorator} instance.
   */
  public static InputBatcherConfig simple(
      Supplier<InputBatcher<FacetValuesAdaptor, FacetValuesAdaptor>> inputBatcherSupplier) {
    return new InputBatcherConfig(
        logicExecutionContext ->
            generateInstanceId(
                    logicExecutionContext.dependants(),
                    logicExecutionContext.kryonDefinitionRegistry())
                .toString(),
        _x -> true,
        batcherContext -> {
          @SuppressWarnings("unchecked")
          var inputsConvertor =
              (FacetsConverter<FacetValuesAdaptor, FacetValuesAdaptor>)
                  batcherContext.vajram().getInputsConvertor();
          return new InputBatchingDecorator<>(
              batcherContext.decoratorContext().instanceId(),
              inputBatcherSupplier.get(),
              inputsConvertor,
              dependantChain ->
                  batcherContext
                      .decoratorContext()
                      .logicExecutionContext()
                      .dependants()
                      .equals(dependantChain));
        });
  }

  public static InputBatcherConfig sharedBatcher(
      Supplier<InputBatcher<FacetValuesAdaptor, FacetValuesAdaptor>> inputBatcherSupplier,
      String instanceId,
      DependantChain... dependantChains) {
    return sharedBatcher(inputBatcherSupplier, instanceId, ImmutableSet.copyOf(dependantChains));
  }

  public static InputBatcherConfig sharedBatcher(
      Supplier<InputBatcher<FacetValuesAdaptor, FacetValuesAdaptor>> inputBatcherSupplier,
      String instanceId,
      ImmutableSet<DependantChain> dependantChains) {
    return new InputBatcherConfig(
        logicExecutionContext -> instanceId,
        logicExecutionContext -> dependantChains.contains(logicExecutionContext.dependants()),
        batcherContext -> {
          @SuppressWarnings("unchecked")
          var inputsConvertor =
              (FacetsConverter<FacetValuesAdaptor, FacetValuesAdaptor>)
                  batcherContext.vajram().getInputsConvertor();
          return new InputBatchingDecorator<>(
              instanceId, inputBatcherSupplier.get(), inputsConvertor, dependantChains::contains);
        });
  }

  /**
   * @return decorator instanceId of the form {@code
   *     [Start]>vajramId_1:dep_1>vajramId_2:dep_2>....>vajramId_n:dep_n}
   */
  private static StringBuilder generateInstanceId(
      DependantChain dependantChain, KryonDefinitionRegistry kryonDefinitionRegistry) {
    if (dependantChain instanceof DependantChainStart dependantChainStart) {
      return new StringBuilder(dependantChainStart.toString());
    } else if (dependantChain instanceof DefaultDependantChain defaultDependantChain) {
      if (defaultDependantChain.dependantChain() instanceof DependantChainStart) {
        String vajramId =
            AnnotationTags.getNamedValueTag(
                    VajramTags.VAJRAM_ID,
                    kryonDefinitionRegistry
                        .get(defaultDependantChain.kryonId())
                        .getOutputLogicDefinition()
                        .logicTags())
                .orElseThrow(
                    () ->
                        new NoSuchElementException(
                            "Could not find tag %s for kryon %s"
                                .formatted(VajramTags.VAJRAM_ID, defaultDependantChain.kryonId())))
                .value();
        return generateInstanceId(defaultDependantChain.dependantChain(), kryonDefinitionRegistry)
            .append('>')
            .append(vajramId)
            .append(':')
            .append(defaultDependantChain.dependencyName());
      } else {
        return generateInstanceId(defaultDependantChain.dependantChain(), kryonDefinitionRegistry)
            .append('>')
            .append(defaultDependantChain.dependencyName());
      }
    }
    throw new UnsupportedOperationException();
  }

  public record BatcherContext(Vajram<?> vajram, DecoratorContext decoratorContext) {}
}

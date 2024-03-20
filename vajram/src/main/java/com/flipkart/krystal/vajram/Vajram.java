package com.flipkart.krystal.vajram;

import static com.flipkart.krystal.vajram.facets.MultiExecute.executeFanoutWith;

import com.flipkart.krystal.data.Facets;
import com.flipkart.krystal.vajram.batching.FacetsConverter;
import com.flipkart.krystal.vajram.facets.DependencyCommand;
import com.flipkart.krystal.vajram.facets.FacetValuesAdaptor;
import com.flipkart.krystal.vajram.facets.VajramFacetDefinition;
import com.flipkart.krystal.vajram.facets.resolution.InputResolver;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.concurrent.CompletableFuture;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Vajrams represent the smallest units of work in the Krystal programming paradigm.
 *
 * <p>A vajram models state using immutable-after-set, type safe units of data called Facets. Facets
 * include:
 *
 * <ul>
 *   <li>Inputs - these need to be provided to execute a vajram
 *   <li>Dependencies - a vajram can depend on other vajrams. These are called dependencies.
 *   <li>Output - the final result computed by the vajram
 *   <li>Fields (To be introduced in the future) - intermediate state computed using other facets
 * </ul>
 *
 * Vajrams model computation a set of functions called logics which consume some facets and compute
 * other facets. Logics are of three types:
 *
 * <ul>
 *   <li>Resolver logic: These consume other facets and compute the inputs of dependency vajrams
 *   <li>Output logic: The logic which computes the final output by consuming any of the other
 *       facets. Output logic is the only logic which is allowed to have side-effects.
 *   <li>Computer logics: (To be introduced in the future) These consume other facets and compute
 *       the values of fields.
 * </ul>
 *
 * Vajrams can be of three types based on how soon the output logic returns the final value.
 *
 * <ul>
 *   <li>Now: ({@link ComputeVajram}s) These are vajrams which execute the output logic in the
 *       calling thread itself.
 *   <li>Soon: ({@link IOVajram}s) These are vajrams whose output logics delegate the output
 *       computation outside the current thread, and expect the final result to be available soon
 *       (within a few minutes)
 *   <li>Later:(DelayableVajrams) (To be introduced in the future) These are vajrams whose output
 *       logics delegate computation to a longrunning process which is not expected to finish
 *       anytime soon - i.e it might take hours, days or maybe even years for that long running
 *       process to complete.
 * </ul>
 *
 * @param <T>
 */
public sealed interface Vajram<T> permits AbstractVajram {

  default ImmutableCollection<InputResolver> getSimpleInputResolvers() {
    return ImmutableList.of();
  }

  default DependencyCommand<Facets> resolveInputOfDependency(
      String dependency, ImmutableSet<String> resolvableInputs, Facets facets) {
    return executeFanoutWith(ImmutableList.of());
  }

  VajramID getId();

  ImmutableCollection<VajramFacetDefinition> getFacetDefinitions();

  ImmutableMap<Facets, CompletableFuture<@Nullable T>> execute(ImmutableList<Facets> inputs);

  default FacetsConverter<? extends FacetValuesAdaptor, ? extends FacetValuesAdaptor>
      getInputsConvertor() {
    throw new UnsupportedOperationException(
        "getInputsConvertor method should be implemented by an IOVajram");
  }
}

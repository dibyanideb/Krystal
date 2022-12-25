package com.flipkart.krystal.krystex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.flipkart.krystal.krystex.node.LogicDefinitionRegistry;
import com.flipkart.krystal.krystex.node.NodeDefinition;
import com.flipkart.krystal.krystex.node.NodeDefinitionRegistry;
import com.flipkart.krystal.krystex.node.NodeId;
import com.flipkart.krystal.krystex.node.NodeInputs;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KrystalNodeExecutorTest {

  private KrystalNodeExecutor krystalNodeExecutor;
  private NodeDefinitionRegistry nodeDefinitionRegistry;

  private static <T> T timedGet(CompletableFuture<T> future)
      throws InterruptedException, ExecutionException, TimeoutException {
    return future.get(5, TimeUnit.HOURS);
  }

  @BeforeEach
  void setUp() {
    nodeDefinitionRegistry = new NodeDefinitionRegistry(new LogicDefinitionRegistry());
    this.krystalNodeExecutor = new KrystalNodeExecutor(nodeDefinitionRegistry, "test");
  }

  @AfterEach
  void tearDown() {
    this.krystalNodeExecutor.close();
  }

  @Test
  void requestExecution_noDependencies_success() throws Exception {
    NodeDefinition nodeDefinition =
        nodeDefinitionRegistry.newClusterDefinition(
            "node",
            nodeDefinitionRegistry
                .nodeDefinitionRegistry()
                .newNonBlockingNode("nodeLogic", dependencyValues -> "computed_value")
                .nodeId());

    String result =
        timedGet(
            krystalNodeExecutor.executeNode(
                nodeDefinition.nodeId(), new NodeInputs(), new RequestId("req_1")));
    assertEquals("computed_value", result);
  }

  @Test
  void requestExecution_unboundInputs_success() throws Exception {
    String logicId = "requestExecution_noDependencies_success_nodeName";
    NodeId nodeId =
        nodeDefinitionRegistry
            .newClusterDefinition(
                logicId,
                nodeDefinitionRegistry
                    .nodeDefinitionRegistry()
                    .newNonBlockingNode(
                        logicId,
                        Set.of("a", "b", "c"),
                        dependencyValues ->
                            "computed_values: a=%s;b=%s;c=%s"
                                .formatted(
                                    dependencyValues.getValue("a").value().orElseThrow(),
                                    dependencyValues.getValue("b").value().orElseThrow(),
                                    dependencyValues.getValue("c").value().orElseThrow()))
                    .nodeId(),
                ImmutableMap.of(),
                ImmutableList.of())
            .nodeId();
    String result =
        timedGet(
            krystalNodeExecutor.executeNode(
                nodeId,
                new NodeInputs(
                    ImmutableMap.of(
                        "a",
                        new SingleValue<Object>(1),
                        "b",
                        new SingleValue<Object>(2),
                        "c",
                        new SingleValue<Object>("3"))),
                new RequestId("r")));
    assertEquals("computed_values: a=1;b=2;c=3", result);
  }

  @Test
  void requestExecution_singleDependency_success() throws Exception {
    LogicDefinitionRegistry logicDefinitionRegistry =
        nodeDefinitionRegistry.nodeDefinitionRegistry();
    NodeDefinition n1 =
        nodeDefinitionRegistry.newClusterDefinition(
            "n1",
            logicDefinitionRegistry
                .newNonBlockingNode("n1_logic", dependencyValues -> "dependency_value")
                .nodeId());

    NodeDefinition n2 =
        nodeDefinitionRegistry.newClusterDefinition(
            "n2",
            logicDefinitionRegistry
                .newNonBlockingNode(
                    "n2_logic",
                    ImmutableSet.of("dep"),
                    dependencyValues ->
                        dependencyValues.get("dep").orElseThrow() + ":computed_value")
                .nodeId(),
            ImmutableMap.of("dep", n1.nodeId()),
            ImmutableList.of());

    String results =
        timedGet(
            krystalNodeExecutor.executeNode(n2.nodeId(), new NodeInputs(), new RequestId("r1")));

    assertEquals("dependency_value:computed_value", results);
  }

  @Test
  void requestExecution_multiLevelDependencies_success() throws Exception {
    LogicDefinitionRegistry logicDefinitionRegistry =
        nodeDefinitionRegistry.nodeDefinitionRegistry();
    String l1Dep = "requestExecution_multiLevelDependencies_level1";
    nodeDefinitionRegistry.newClusterDefinition(
        l1Dep,
        logicDefinitionRegistry.newNonBlockingNode(l1Dep, dependencyValues -> "l1").nodeId());

    String l2Dep = "requestExecution_multiLevelDependencies_level2";
    nodeDefinitionRegistry.newClusterDefinition(
        l2Dep,
        logicDefinitionRegistry
            .newNonBlockingNode(
                l2Dep,
                ImmutableSet.of("input"),
                dependencyValues -> dependencyValues.getOrThrow("input") + ":l2")
            .nodeId(),
        ImmutableMap.of("input", new NodeId(l1Dep)));

    String l3Dep = "requestExecution_multiLevelDependencies_level3";
    nodeDefinitionRegistry.newClusterDefinition(
        l3Dep,
        logicDefinitionRegistry
            .newNonBlockingNode(
                l3Dep,
                ImmutableSet.of("input"),
                dependencyValues -> dependencyValues.getOrThrow("input") + ":l3")
            .nodeId(),
        ImmutableMap.of("input", new NodeId(l2Dep)));

    String l4Dep = "requestExecution_multiLevelDependencies_level4";
    nodeDefinitionRegistry.newClusterDefinition(
        l4Dep,
        logicDefinitionRegistry
            .newNonBlockingNode(
                l4Dep,
                ImmutableSet.of("input"),
                dependencyValues -> dependencyValues.getOrThrow("input") + ":l4")
            .nodeId(),
        ImmutableMap.of("input", new NodeId(l3Dep)));

    String results =
        timedGet(
            krystalNodeExecutor.executeNode(
                nodeDefinitionRegistry
                    .newClusterDefinition(
                        "requestExecution_multiLevelDependencies_final",
                        logicDefinitionRegistry
                            .newNonBlockingNode(
                                "requestExecution_multiLevelDependencies_final",
                                ImmutableSet.of("input"),
                                dependencyValues -> dependencyValues.getOrThrow("input") + ":final")
                            .nodeId(),
                        ImmutableMap.of("input", new NodeId(l4Dep)))
                    .nodeId(),
                new NodeInputs(),
                new RequestId("r")));
    assertEquals("l1:l2:l3:l4:final", results);
  }

  @Test
  void close_preventsNewExecutionRequests() {
    krystalNodeExecutor.close();
    assertThrows(
        Exception.class,
        () ->
            krystalNodeExecutor.executeNode(
                nodeDefinitionRegistry
                    .newClusterDefinition(
                        "shutdown_preventsNewExecutionRequests",
                        nodeDefinitionRegistry
                            .nodeDefinitionRegistry()
                            .newNonBlockingNode(
                                "shutdown_preventsNewExecutionRequests",
                                ImmutableSet.of(),
                                dependencyValues -> ImmutableList.of(""))
                            .nodeId())
                    .nodeId(),
                new NodeInputs(),
                new RequestId("")));
  }
}
package at.pro2future.shopfloors.monitored;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.util.ConversionUtil.l;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;

public interface MonitoredItemConfiguration {
	public OpcUaClient getConfigurationClient();
	public CompletableFuture<Integer> createClient(String endpoint) throws Exception;
	public CompletableFuture<StatusCode> destroyClient(int clientIndex);
	public CompletableFuture<Integer> createSubscription(int clientIndex);
	@Deprecated
	public CompletableFuture<StatusCode> destroySubscription(int clientIndex, Integer subscriptionId);
	public CompletableFuture<NodeId> addBooleanVariable(int namespace, String identifier, String displayName, String browsename);
	public CompletableFuture<NodeId> addIntegerVariable(int namespace, String identifier, String displayName, String browsename);
	public CompletableFuture<NodeId> addFloatVariable(int namespace, String identifier, String displayName, String browsename);
	public CompletableFuture<NodeId> addDoubleVariable(int namespace, String identifier, String displayName, String browsename);
	public CompletableFuture<StatusCode> writeFloat(NodeId nodeId, Float value);
	public CompletableFuture<StatusCode> writeInteger(NodeId nodeId, Integer value);
	public CompletableFuture<StatusCode> writeDouble(NodeId nodeId, Double value);
	public CompletableFuture<StatusCode> writeBoolean(NodeId nodeId, Boolean value);
	public CompletableFuture<Boolean> readBoolean(NodeId nodeId);
	public CompletableFuture<Float> readFloat(NodeId nodeId);
	public CompletableFuture<Integer> readInteger(NodeId nodeId);
	public CompletableFuture<Double> readDouble(NodeId nodeId);
	public CompletableFuture<Integer> createMonitoredItem(int clientIndex, Integer subscriptionIndex, NodeId nodeId);
	public CompletableFuture<StatusCode> destroyMonitoredItem(int clientIndex, Integer subscriptionId, int monitoredItemId);
	public CompletableFuture<StatusCode> createMapping(Integer subscriptionId, int monitoredItemId, NodeId nodeId);
	public CompletableFuture<StatusCode> destroyMapping(Integer subscriptionId, int monitoredItemId);
	public CompletableFuture<StatusCode> stopServer();
	public UShort getNamespaceIndex(int clientIndex);
}

package at.pro2future.shopfloors.monitored;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.util.ConversionUtil.l;

import java.io.File;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.stack.client.UaTcpStackClient;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;

public class MonitoredItemConfiguration {
	private static int securityCount = 0;
	public static CompletableFuture<OpcUaClient> connectToServer(String url) {
		CompletableFuture<OpcUaClient> future = new CompletableFuture<>();
		EndpointDescription endpoint;
		EndpointDescription[] endpoints;
		File securityTempDir = new File(System.getProperty("java.io.tmpdir"), String.format("security%03d",securityCount));
        securityCount++;
		if (!securityTempDir.exists() && !securityTempDir.mkdirs()) {
            future.completeExceptionally(new Exception("unable to create security dir: " + securityTempDir));
            return future;
        }
		try {
        KeyStoreLoader loader = new KeyStoreLoader().load(securityTempDir);
        SecurityPolicy securityPolicy = SecurityPolicy.None;
        
        endpoints = UaTcpStackClient
            .getEndpoints(url)
            .get();

        endpoint = Arrays.stream(endpoints)
            .filter(e -> e.getSecurityPolicyUri().equals(securityPolicy.getSecurityPolicyUri()))
            .findFirst().orElseThrow(() -> new Exception("no desired endpoints returned"));

		OpcUaClientConfig config = OpcUaClientConfig.builder()
				.setApplicationName(LocalizedText.english("My Client"))
				.setApplicationUri(String.format("urn:example-client:%s", UUID.randomUUID()))
				.setCertificate(loader.getClientCertificate())
				.setKeyPair(loader.getClientKeyPair())
				.setEndpoint(endpoint)
				.setRequestTimeout(uint(60000)).build();
		

        OpcUaClient client = new OpcUaClient(config);
		future.complete(client);
		return future;
		
		} catch(Exception e) {
			future.completeExceptionally(e);
			return future;
		}
	}
	public static CompletableFuture<Integer> createClient(OpcUaClient client, String endpoint) {
		NodeId objectId = new NodeId(0,85); // ObjectsFolder
		NodeId methodId = NodeId.parse("ns=1;s=createclient");

        CallMethodRequest request = new CallMethodRequest(
            objectId, methodId, new Variant[]{new Variant(endpoint)});

        return client.call(request).thenCompose(result -> {
            StatusCode statusCode = result.getStatusCode();

            if (statusCode.isGood()) {
                Integer value = (Integer) l(result.getOutputArguments()).get(0).getValue();
                return CompletableFuture.completedFuture(value);
            } else {
                CompletableFuture<Integer> f = new CompletableFuture<>();
                f.completeExceptionally(new UaException(statusCode));
                return f;
            }
        });
	}
	public static CompletableFuture<StatusCode> destroyClient(OpcUaClient client, int clientIndex) {
		NodeId objectId = new NodeId(0,85); // ObjectsFolder
		NodeId methodId = NodeId.parse("ns=1;s=destroyclient");

        CallMethodRequest request = new CallMethodRequest(
            objectId, methodId, new Variant[]{new Variant(clientIndex)});

        return client.call(request).thenCompose(result -> {
            StatusCode statusCode = result.getStatusCode();

            if (statusCode.isGood()) {
                return CompletableFuture.completedFuture(StatusCode.GOOD);
            } else {
                CompletableFuture<StatusCode> f = new CompletableFuture<>();
                f.completeExceptionally(new UaException(statusCode));
                return f;
            }
        });
	}
	public static CompletableFuture<Integer> createSubscription(OpcUaClient client, int clientIndex) {
		NodeId objectId = new NodeId(0,85); // ObjectsFolder
		NodeId methodId = NodeId.parse("ns=1;s=createsubscription");

        CallMethodRequest request = new CallMethodRequest(
            objectId, methodId, new Variant[]{new Variant(clientIndex)});

        return client.call(request).thenCompose(result -> {
            StatusCode statusCode = result.getStatusCode();

            if (statusCode.isGood()) {
                Integer value = (Integer) l(result.getOutputArguments()).get(0).getValue();
                return CompletableFuture.completedFuture(value);
            } else {
                CompletableFuture<Integer> f = new CompletableFuture<>();
                f.completeExceptionally(new UaException(statusCode));
                return f;
            }
        });
	}
	public static CompletableFuture<StatusCode> destroySubscription(OpcUaClient client, int clientIndex, int subscriptionId) {
		NodeId objectId = new NodeId(0,85); // ObjectsFolder
		NodeId methodId = NodeId.parse("ns=1;s=destroycreatesubscription");

        CallMethodRequest request = new CallMethodRequest(
            objectId, methodId, new Variant[]{new Variant(clientIndex), new Variant(subscriptionId)});

        return client.call(request).thenCompose(result -> {
            StatusCode statusCode = result.getStatusCode();

            if (statusCode.isGood()) {
                return CompletableFuture.completedFuture(StatusCode.GOOD);
            } else {
                CompletableFuture<StatusCode> f = new CompletableFuture<>();
                f.completeExceptionally(new UaException(statusCode));
                return f;
            }
        });
	}


	 public static CompletableFuture<NodeId> addBooleanVariable(OpcUaClient client, int namespace, String displayName, String browsename) {
		 NodeId objectId = new NodeId(0, 85); // ObjectsFolder
	        NodeId methodId = NodeId.parse("ns=1;s=addBooleanVariable");

	        CallMethodRequest request = new CallMethodRequest(
	            objectId, methodId, new Variant[]{new Variant(namespace), new Variant(displayName), new Variant(browsename)});

	        return client.call(request).thenCompose(result -> {
	            StatusCode statusCode = result.getStatusCode();

	            if (statusCode.isGood()) {
	                NodeId value = (NodeId) l(result.getOutputArguments()).get(0).getValue();
	                return CompletableFuture.completedFuture(value);
	            } else {
	                CompletableFuture<NodeId> f = new CompletableFuture<>();
	                f.completeExceptionally(new UaException(statusCode));
	                return f;
	            }
	        });
	 }
	 public static CompletableFuture<NodeId> addIntegerVariable(OpcUaClient client, int namespace, String displayName, String browsename) {
		 NodeId objectId = new NodeId(0, 85); // ObjectsFolder
	        NodeId methodId = NodeId.parse("ns=1;s=addIntVariable");

	        CallMethodRequest request = new CallMethodRequest(
	            objectId, methodId, new Variant[]{new Variant(namespace), new Variant(displayName), new Variant(browsename)});

	        return client.call(request).thenCompose(result -> {
	            StatusCode statusCode = result.getStatusCode();

	            if (statusCode.isGood()) {
	                NodeId value = (NodeId) l(result.getOutputArguments()).get(0).getValue();
	                return CompletableFuture.completedFuture(value);
	            } else {
	                CompletableFuture<NodeId> f = new CompletableFuture<>();
	                f.completeExceptionally(new UaException(statusCode));
	                return f;
	            }
	        });
	 }
	 public static CompletableFuture<NodeId> addFloatVariable(OpcUaClient client, int namespace, String displayName, String browsename) {
		 NodeId objectId = new NodeId(0, 85); // ObjectsFolder
	        NodeId methodId = NodeId.parse("ns=1;s=addFloatVariable");

	        CallMethodRequest request = new CallMethodRequest(
	            objectId, methodId, new Variant[]{new Variant(namespace), new Variant(displayName), new Variant(browsename)});

	        return client.call(request).thenCompose(result -> {
	            StatusCode statusCode = result.getStatusCode();

	            if (statusCode.isGood()) {
	                NodeId value = (NodeId) l(result.getOutputArguments()).get(0).getValue();
	                return CompletableFuture.completedFuture(value);
	            } else {
	                CompletableFuture<NodeId> f = new CompletableFuture<>();
	                f.completeExceptionally(new UaException(statusCode));
	                return f;
	            }
	        });
	 }
	 public static CompletableFuture<NodeId> addDoubleVariable(OpcUaClient client, int namespace, String displayName, String browsename) {
		 NodeId objectId = new NodeId(0, 85); // ObjectsFolder
	        NodeId methodId = NodeId.parse("ns=1;s=addDoubleVariable");

	        CallMethodRequest request = new CallMethodRequest(
	            objectId, methodId, new Variant[]{new Variant(namespace), new Variant(displayName), new Variant(browsename)});

	        return client.call(request).thenCompose(result -> {
	            StatusCode statusCode = result.getStatusCode();

	            if (statusCode.isGood()) {
	                NodeId value = (NodeId) l(result.getOutputArguments()).get(0).getValue();
	                return CompletableFuture.completedFuture(value);
	            } else {
	                CompletableFuture<NodeId> f = new CompletableFuture<>();
	                f.completeExceptionally(new UaException(statusCode));
	                return f;
	            }
	        });
	 }
	 
	 public static CompletableFuture<StatusCode> writeFloat(OpcUaClient client, NodeId nodeId, Float value) {
		 Variant varPower = new Variant(value);
	        DataValue writing = new DataValue(varPower);
	        CompletableFuture<StatusCode> writeFuture = client.writeValue(nodeId, writing);
	        return writeFuture;
	 }
	 public static CompletableFuture<StatusCode> writeInteger(OpcUaClient client, NodeId nodeId, Integer value) {
		 Variant varPower = new Variant(value);
	        DataValue writing = new DataValue(varPower);
	        CompletableFuture<StatusCode> writeFuture = client.writeValue(nodeId, writing);
	        return writeFuture;
	 }
	 public static CompletableFuture<StatusCode> writeDouble(OpcUaClient client, NodeId nodeId, Double value) {
		 Variant varPower = new Variant(value);
	        DataValue writing = new DataValue(varPower);
	        CompletableFuture<StatusCode> writeFuture = client.writeValue(nodeId, writing);
	        return writeFuture;
	 }

	 public static CompletableFuture<Float> readFloat(OpcUaClient client, NodeId nodeId) {

	     CompletableFuture<DataValue> dataFuture = client.readValue(0.0, TimestampsToReturn.Both, nodeId);
	     DataValue dValue;
		try {
			dValue = dataFuture.get();
		     Float f = (float)dValue.getValue().getValue();
		        CompletableFuture<Float> readFuture = new CompletableFuture<>();
		        readFuture.complete(f);
		        return readFuture;
		} catch (InterruptedException | ExecutionException e) {
			CompletableFuture<Float> exFuture = new CompletableFuture<>();
			exFuture.completeExceptionally(new Exception("Read Error"));
			return exFuture;
		}
	 }

	 public static CompletableFuture<Integer> readInteger(OpcUaClient client, NodeId nodeId) {

	     CompletableFuture<DataValue> dataFuture = client.readValue(0.0, TimestampsToReturn.Both, nodeId);
	     DataValue dValue;
		try {
			dValue = dataFuture.get();
		     Integer f = (int)dValue.getValue().getValue();
		        CompletableFuture<Integer> readFuture = new CompletableFuture<>();
		        readFuture.complete(f);
		        return readFuture;
		} catch (InterruptedException | ExecutionException e) {
			CompletableFuture<Integer> exFuture = new CompletableFuture<>();
			exFuture.completeExceptionally(new Exception("Read Error"));
			return exFuture;
		}
	 }

	 public static CompletableFuture<Double> readDouble(OpcUaClient client, NodeId nodeId) {

	     CompletableFuture<DataValue> dataFuture = client.readValue(0.0, TimestampsToReturn.Both, nodeId);
	     DataValue dValue;
		try {
			dValue = dataFuture.get();
		     Double f = (double)dValue.getValue().getValue();
		        CompletableFuture<Double> readFuture = new CompletableFuture<>();
		        readFuture.complete(f);
		        return readFuture;
		} catch (InterruptedException | ExecutionException e) {
			CompletableFuture<Double> exFuture = new CompletableFuture<>();
			exFuture.completeExceptionally(new Exception("Read Error"));
			return exFuture;
		}
	 }
	 public static CompletableFuture<Integer> createMonitoredItem(OpcUaClient client, int clientIndex, int subscriptionIndex, NodeId nodeId) {
			NodeId objectId = new NodeId(0,85); // ObjectsFolder
			NodeId methodId = NodeId.parse("ns=1;s=createmonitoreditem");

	        CallMethodRequest request = new CallMethodRequest(
	            objectId, methodId, new Variant[]{new Variant(clientIndex), new Variant(subscriptionIndex), new Variant(nodeId)});

	        return client.call(request).thenCompose(result -> {
	            StatusCode statusCode = result.getStatusCode();

	            if (statusCode.isGood()) {
	                Integer value = (Integer) l(result.getOutputArguments()).get(0).getValue();
	                return CompletableFuture.completedFuture(value);
	            } else {
	                CompletableFuture<Integer> f = new CompletableFuture<>();
	                f.completeExceptionally(new UaException(statusCode));
	                return f;
	            }
	        });
		}
	 public static CompletableFuture<StatusCode> destroyMonitoredItem(OpcUaClient client, int clientIndex, int subscriptionId, int monitoredItemId) {
			NodeId objectId = new NodeId(0,85); // ObjectsFolder
			NodeId methodId = NodeId.parse("ns=1;s=destroymonitoreditem");

	        CallMethodRequest request = new CallMethodRequest(
	            objectId, methodId, new Variant[]{new Variant(clientIndex), 
	            		new Variant(subscriptionId),
	            		new Variant(monitoredItemId)});


	        return client.call(request).thenCompose(result -> {
	            StatusCode statusCode = result.getStatusCode();

	            if (statusCode.isGood()) {
	                return CompletableFuture.completedFuture(StatusCode.GOOD);
	            } else {
	                CompletableFuture<StatusCode> f = new CompletableFuture<>();
	                f.completeExceptionally(new UaException(statusCode));
	                return f;
	            }
	        });
		}
	 public static CompletableFuture<StatusCode> createMapping(OpcUaClient client, int subscriptionId, int monitoredItemId, NodeId nodeId) {
			NodeId objectId = new NodeId(0,85); // ObjectsFolder
			NodeId methodId = NodeId.parse("ns=1;s=createmapping");

	        CallMethodRequest request = new CallMethodRequest(
	            objectId, methodId, new Variant[]{new Variant(subscriptionId), 
	            		new Variant(monitoredItemId),
	            		new Variant(nodeId)});


	        return client.call(request).thenCompose(result -> {
	            StatusCode statusCode = result.getStatusCode();

	            if (statusCode.isGood()) {
	                return CompletableFuture.completedFuture(StatusCode.GOOD);
	            } else {
	                CompletableFuture<StatusCode> f = new CompletableFuture<>();
	                f.completeExceptionally(new UaException(statusCode));
	                return f;
	            }
	        });
		}
	 public static CompletableFuture<StatusCode> destroyMapping(OpcUaClient client, int subscriptionId, int monitoredItemId) {
			NodeId objectId = new NodeId(0,85); // ObjectsFolder
			NodeId methodId = NodeId.parse("ns=1;s=destroymonitoreditem");

	        CallMethodRequest request = new CallMethodRequest(
	            objectId, methodId, new Variant[]{new Variant(subscriptionId),
	            		new Variant(monitoredItemId)});


	        return client.call(request).thenCompose(result -> {
	            StatusCode statusCode = result.getStatusCode();

	            if (statusCode.isGood()) {
	                return CompletableFuture.completedFuture(StatusCode.GOOD);
	            } else {
	                CompletableFuture<StatusCode> f = new CompletableFuture<>();
	                f.completeExceptionally(new UaException(statusCode));
	                return f;
	            }
	        });
		}
}

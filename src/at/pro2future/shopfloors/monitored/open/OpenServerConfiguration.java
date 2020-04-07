package at.pro2future.shopfloors.monitored.open;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.util.ConversionUtil.l;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Security;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.stack.client.DiscoveryClient;
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
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;

import at.pro2future.shopfloors.monitored.KeyStoreLoader;
import at.pro2future.shopfloors.monitored.MonitoredItemConfiguration;

@Deprecated
public class OpenServerConfiguration implements MonitoredItemConfiguration {

    static {
        // Required for SecurityPolicy.Aes256_Sha256_RsaPss
        Security.addProvider(new BouncyCastleProvider());
    }
    
	private Process process;
	private OpcUaClient client;
	private String endpoint;
		private static class UniqueInteger {
			private int value;
			private static UniqueInteger inst;
			public static UniqueInteger instance() {
				if(inst == null) inst = new UniqueInteger();
				return inst;
			}
			public int getValue() {
				value ++;
				return value;
			}
			private UniqueInteger() {
				value = 0;
			}
		}
		private CompletableFuture<OpcUaClient> createConfigurationClient(String url) throws Exception {
			endpoint = url;
			CompletableFuture<OpcUaClient> future = new CompletableFuture<>();
			EndpointDescription endpoint;
			List<EndpointDescription> endpoints;
			Path securityTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "security");
	        Files.createDirectories(securityTempDir);
	        if (!Files.exists(securityTempDir)) {
	            throw new Exception("unable to create security dir: " + securityTempDir);
	        }
	        KeyStoreLoader loader = new KeyStoreLoader().load(securityTempDir);
	        try {
	            endpoints = DiscoveryClient.getEndpoints(url).get();
	        } catch (Throwable ex) {
	            // try the explicit discovery endpoint as well
	            String discoveryUrl = url;

	            if (!discoveryUrl.endsWith("/")) {
	                discoveryUrl += "/";
	            }
	            discoveryUrl += "discovery";

	            endpoints = DiscoveryClient.getEndpoints(discoveryUrl).get();
	        }
	        SecurityPolicy securityPolicy = SecurityPolicy.None;
	        try {
	        endpoint = endpoints.stream()
	            .filter(e -> e.getSecurityPolicyUri().equals(securityPolicy.getUri()))
	            .findFirst().orElseThrow(() -> new Exception("no desired endpoints returned"));

			OpcUaClientConfig config = OpcUaClientConfig.builder()
					.setApplicationName(LocalizedText.english("My Client"))
					.setApplicationUri(String.format("urn:example-client:%s", UUID.randomUUID()))
					.setCertificate(loader.getClientCertificate())
					.setKeyPair(loader.getClientKeyPair())
					.setEndpoint(endpoint)
					.setRequestTimeout(uint(60000)).build();
			
	        OpcUaClient client = OpcUaClient.create(config);
			future.complete(client);
			System.out.println("Connected to " + endpoint.getEndpointUrl());
			return future;
			
			} catch(Exception e) {
				future.completeExceptionally(e);
				return future;
			}
		}
		public CompletableFuture<Integer> createClient(String rendpoint) {
			NodeId objectId = new NodeId(0,85); // ObjectsFolder
			NodeId methodId = NodeId.parse("ns=1;s=createclient");

	        CallMethodRequest request = new CallMethodRequest(
	            objectId, methodId, new Variant[]{new Variant(rendpoint)});

	        return client.call(request).thenCompose(result -> {
	            StatusCode statusCode = result.getStatusCode();

	            if (statusCode.isGood()) {
	                Integer value = (Integer) l(result.getOutputArguments()).get(0).getValue();
	                System.out.println(endpoint + " is connected to " + rendpoint);
	                return CompletableFuture.completedFuture(value);
	            } else {
	                CompletableFuture<Integer> f = new CompletableFuture<>();
	                f.completeExceptionally(new UaException(statusCode));
	                return f;
	            }
	        });
		}
		public CompletableFuture<StatusCode> destroyClient(int clientIndex) {
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
		public CompletableFuture<Integer> createSubscription(int clientIndex) {
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
		public CompletableFuture<StatusCode> destroySubscription(int clientIndex, Integer subscriptionId) {
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


		 public CompletableFuture<NodeId> addBooleanVariable(int namespace, String identifier, String displayName, String browsename) {
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
		 public CompletableFuture<NodeId> addIntegerVariable(int namespace, String identifier, String displayName, String browsename) {
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
		 public CompletableFuture<NodeId> addFloatVariable( int namespace, String identifier, String displayName, String browsename) {
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
		 public CompletableFuture<NodeId> addDoubleVariable(int namespace, String identifier, String displayName, String browsename) {
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
		 
		 public CompletableFuture<StatusCode> writeFloat(NodeId nodeId, Float value) {
			 Variant varPower = new Variant(value);
		        DataValue writing = new DataValue(varPower);
		        CompletableFuture<StatusCode> writeFuture = client.writeValue(nodeId, writing);
		        return writeFuture;
		 }
		 public CompletableFuture<StatusCode> writeInteger(NodeId nodeId, Integer value) {
			 Variant varPower = new Variant(value);
		        DataValue writing = new DataValue(varPower);
		        CompletableFuture<StatusCode> writeFuture = client.writeValue(nodeId, writing);
		        return writeFuture;
		 }
		 public CompletableFuture<StatusCode> writeDouble(NodeId nodeId, Double value) {
			 Variant varPower = new Variant(value);
		        DataValue writing = new DataValue(varPower);
		        CompletableFuture<StatusCode> writeFuture = client.writeValue(nodeId, writing);
		        return writeFuture;
		 }

		 public CompletableFuture<StatusCode> writeBoolean(NodeId nodeId, Boolean value) {
			 Variant varPower = new Variant(value);
		        DataValue writing = new DataValue(varPower);
		        CompletableFuture<StatusCode> writeFuture = client.writeValue(nodeId, writing);
		        return writeFuture;
		 }

		 public CompletableFuture<Boolean> readBoolean(NodeId nodeId) {

		     CompletableFuture<DataValue> dataFuture = client.readValue(0.0, TimestampsToReturn.Both, nodeId);
		     DataValue dValue;
			try {
				dValue = dataFuture.get();
			     Boolean f = (boolean)dValue.getValue().getValue();
			        CompletableFuture<Boolean> readFuture = new CompletableFuture<>();
			        readFuture.complete(f);
			        return readFuture;
			} catch (InterruptedException | ExecutionException e) {
				CompletableFuture<Boolean> exFuture = new CompletableFuture<>();
				exFuture.completeExceptionally(new Exception("Read Error"));
				return exFuture;
			}
		 }
		 public CompletableFuture<Float> readFloat(NodeId nodeId) {

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

		 public CompletableFuture<Integer> readInteger(NodeId nodeId) {

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

		 public CompletableFuture<Double> readDouble(NodeId nodeId) {

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
		 public CompletableFuture<Integer> createMonitoredItem(int clientIndex, Integer subscriptionIndex, NodeId nodeId) {
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
		 public CompletableFuture<StatusCode> destroyMonitoredItem(int clientIndex, Integer subscriptionId, int monitoredItemId) {
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
		 public CompletableFuture<StatusCode> createMapping(Integer subscriptionId, int monitoredItemId, NodeId nodeId) {
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
		 public CompletableFuture<StatusCode> destroyMapping(Integer subscriptionId, int monitoredItemId) {
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
		 public CompletableFuture<StatusCode> stopServer() {
			NodeId objectId = new NodeId(0,85); // ObjectsFolder
			NodeId methodId = NodeId.parse("ns=1;s=stopserver");
			
			
			System.out.println("Stopping "+ client);

	        CallMethodRequest request = new CallMethodRequest(
	            objectId, methodId, null);

	        try {
	        CompletableFuture<CallMethodResult> stopFuture = client.call(request);
	        CallMethodResult stopResult = stopFuture.get();
	        if(stopResult.getStatusCode() == StatusCode.GOOD) {
	        	CompletableFuture<OpcUaClient> discFuture = client.disconnect();
	        	discFuture.get();
	        	CompletableFuture<StatusCode> retFuture = new CompletableFuture<>();
	        	retFuture.complete(StatusCode.GOOD);
	        	return retFuture;
	        }
        	CompletableFuture<StatusCode> retFuture = new CompletableFuture<>();
        	retFuture.complete(stopResult.getStatusCode());
        	return retFuture;
	        } catch(Exception e) {

	        	CompletableFuture<StatusCode> retFuture = new CompletableFuture<>();
	        	retFuture.complete(StatusCode.BAD);
	        	return retFuture;
	        }
	        
	        
		}
		 

		public OpenServerConfiguration(int port) {
			 try {
				process = new ProcessBuilder("SimplifiedCompile.exe", String.valueOf(port)).start();
				Thread.sleep(2500);
				
				CompletableFuture<OpcUaClient> future = createConfigurationClient("opc.tcp://127.0.0.1:"+port);
				client = future.get();
				client.connect().get();
			} catch (IOException e) {
				e.printStackTrace();
			} catch(ExecutionException ee) {
				ee.printStackTrace();
			} catch (InterruptedException ie) {
				// TODO Auto-generated catch block
				ie.printStackTrace();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		 }
		@Override
		public OpcUaClient getConfigurationClient() {
			return client;
		}
		@Override
		public UShort getNamespaceIndex(int clientIndex) {
			return UShort.valueOf(1);
		}

	
}

package at.pro2future.shopfloors.monitored.milo;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.stack.client.DiscoveryClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoredItemCreateRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoringParameters;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;

import at.pro2future.shopfloors.monitored.KeyStoreLoader;
import at.pro2future.shopfloors.monitored.MonitoredItemConfiguration;

import static com.google.common.collect.Lists.newArrayList;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.util.ConversionUtil.l;


public class MiloConfiguration implements MonitoredItemConfiguration {
	
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
	
	private int clientId;
	private int subId;
	private int monId;
	
	private OpcUaClient client;
	private Map<Integer, UShort> foreignNamespaces;
	private Map<Integer, OpcUaClient> foreignClients;
	private Map<Integer, UaSubscription> subscriptions;
	private Map<Integer, UaMonitoredItem> monitoreds;
	private Map<Integer, ValueMapper> handlers;
	private ConfigurableServer server;
    private final AtomicLong clientHandles = new AtomicLong(1L);

	public MiloConfiguration(int port) throws Exception {
		foreignNamespaces = new HashMap<>();
		foreignClients = new HashMap<>();
		subscriptions = new HashMap<>();
		handlers = new HashMap<>();
		monitoreds = new HashMap<>();
		server = new ConfigurableServer(port);
		server.startup().get();
		clientId = 0;
		subId = 0;
	}
	
	@Override
	public OpcUaClient getConfigurationClient() {
		return client;
	}

	@Override
	public CompletableFuture<Integer> createClient(String endpointUrl) throws Exception {
		Path securityTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "security");
        Files.createDirectories(securityTempDir);
        if (!Files.exists(securityTempDir)) {
            throw new Exception("unable to create security dir: " + securityTempDir);
        }
        KeyStoreLoader loader = new KeyStoreLoader().load(securityTempDir);

        SecurityPolicy securityPolicy = SecurityPolicy.None;

        List<EndpointDescription> endpoints;

        try {
            endpoints = DiscoveryClient.getEndpoints(endpointUrl).get();
        } catch (Throwable ex) {
            // try the explicit discovery endpoint as well
            String discoveryUrl = endpointUrl;

            if (!discoveryUrl.endsWith("/")) {
                discoveryUrl += "/";
            }
            discoveryUrl += "discovery";

            endpoints = DiscoveryClient.getEndpoints(discoveryUrl).get();
        }

        EndpointDescription endpoint = endpoints.stream()
            .filter(e -> e.getSecurityPolicyUri().equals(securityPolicy.getUri()))
            .findFirst()
            .orElseThrow(() -> new Exception("no desired endpoints returned"));


        OpcUaClientConfig config = OpcUaClientConfig.builder()
            .setApplicationName(LocalizedText.english("eclipse milo opc-ua client"))
            .setApplicationUri("urn:eclipse:milo:examples:client")
            .setCertificate(loader.getClientCertificate())
            .setKeyPair(loader.getClientKeyPair())
            .setEndpoint(endpoint)
            .setRequestTimeout(uint(5000))
            .build();

        OpcUaClient c = OpcUaClient.create(config);
        c.connect().get();
        UShort index = c.getNamespaceTable().getIndex("at.pro2future.monitored");
        System.out.println("Namespace index is " + index);
        clientId++;
        foreignClients.put(clientId, c);
        foreignNamespaces.put(clientId, index);
        CompletableFuture<Integer> fut = new CompletableFuture<>();
        fut.complete(clientId);
        return fut;
	}

	@Override
	public CompletableFuture<StatusCode> destroyClient(int clientIndex) {
		OpcUaClient c = foreignClients.get(clientIndex);
		try {
			c.disconnect().get();
			foreignClients.remove(clientIndex);
		} catch(Exception e) {
			
		}
		CompletableFuture<StatusCode> ret = new CompletableFuture<>();
		ret.complete(StatusCode.GOOD);
		return ret;
	}

	@Override
	public CompletableFuture<Integer> createSubscription(int clientIndex) {
		CompletableFuture<Integer> ret = new CompletableFuture<>();
		OpcUaClient c = foreignClients.get(clientIndex);
        try {
			UaSubscription subscription = c.getSubscriptionManager().createSubscription(10.0).get();
			System.out.println(subscription.getRevisedPublishingInterval());
			subId++;
			subscriptions.put(subId, subscription);
			ret.complete(subId);
		} catch (Exception e) {
			e.printStackTrace();
			ret.complete(Integer.MIN_VALUE);
		} 
		return ret;
	}

	@Override
	public CompletableFuture<StatusCode> destroySubscription(int clientIndex, Integer subscriptionId) {
		CompletableFuture<StatusCode> ret = new CompletableFuture<>();
		UaSubscription subscription = subscriptions.get(subscriptionId);
		OpcUaClient cl = foreignClients.get(clientIndex);
		if(cl != null && subscription != null) {
			List<UInteger> ids = new LinkedList<>();
			ids.add(subscription.getSubscriptionId());
			cl.deleteSubscriptions(ids);
		}
		return null;
	}

	@Override
	public CompletableFuture<NodeId> addBooleanVariable(int namespace, String identifier, String displayName, String browsename) {
		NodeId id = server.addBoolean(namespace, identifier, displayName, browsename);
		System.out.println(id);
		CompletableFuture<NodeId> fut = new CompletableFuture<>();
		if(id == null) {
			fut.completeExceptionally(new NullPointerException());
		} else {
			fut.complete(id);
		}
		return fut;
	}

	@Override
	public CompletableFuture<NodeId> addIntegerVariable(int namespace, String identifier, String displayName, String browsename) {
		NodeId id = server.addInteger(namespace, identifier, displayName, browsename);
		System.out.println(id);
		CompletableFuture<NodeId> fut = new CompletableFuture<>();
		if(id == null) {
			fut.completeExceptionally(new NullPointerException());
		} else {
			fut.complete(id);
		}
		return fut;
	}

	@Override
	public CompletableFuture<NodeId> addFloatVariable(int namespace, String identifier, String displayName, String browsename) {
		NodeId id = server.addFloat(namespace, identifier, displayName, browsename);
		System.out.println(id);
		CompletableFuture<NodeId> fut = new CompletableFuture<>();
		if(id == null) {
			fut.completeExceptionally(new NullPointerException());
		} else {
			fut.complete(id);
		}
		return fut;
	}

	@Override
	public CompletableFuture<NodeId> addDoubleVariable(int namespace, String identifier, String displayName, String browsename) {
		NodeId id = server.addDouble(namespace, identifier, displayName, browsename);
		System.out.println(id);
		CompletableFuture<NodeId> fut = new CompletableFuture<>();
		if(id == null) {
			fut.completeExceptionally(new NullPointerException());
		} else {
			fut.complete(id);
		}
		return fut;
	}

	@Override
	public CompletableFuture<StatusCode> writeFloat(NodeId nodeId, Float value) {
		CompletableFuture<StatusCode> resFut = new CompletableFuture<>();
		boolean res = server.writeFloat(nodeId, value);
		if(res) {
			resFut.complete(StatusCode.GOOD);
		} else {
			resFut.complete(StatusCode.BAD);
		}
		return resFut;
	}

	@Override
	public CompletableFuture<StatusCode> writeInteger(NodeId nodeId, Integer value) {
		CompletableFuture<StatusCode> resFut = new CompletableFuture<>();
		boolean res = server.writeInteger(nodeId, value);
		if(res) {
			resFut.complete(StatusCode.GOOD);
		} else {
			resFut.complete(StatusCode.BAD);
		}
		return resFut;
	}

	@Override
	public CompletableFuture<StatusCode> writeDouble(NodeId nodeId, Double value) {
		CompletableFuture<StatusCode> resFut = new CompletableFuture<>();
		boolean res = server.writeDouble(nodeId, value);
		if(res) {
			resFut.complete(StatusCode.GOOD);
		} else {
			resFut.complete(StatusCode.BAD);
		}
		return resFut;
	}

	@Override
	public CompletableFuture<StatusCode> writeBoolean(NodeId nodeId, Boolean value) {
		CompletableFuture<StatusCode> resFut = new CompletableFuture<>();
		boolean res = server.writeBoolean(nodeId, value);
		if(res) {
			resFut.complete(StatusCode.GOOD);
		} else {
			resFut.complete(StatusCode.BAD);
		}
		return resFut;
	}
	
	@Override
	public CompletableFuture<Boolean> readBoolean(NodeId nodeId) {
		CompletableFuture<Boolean> resFut = new CompletableFuture<>();
		Boolean res = server.readBoolean(nodeId);
		resFut.complete(res);
		return resFut;
	}

	@Override
	public CompletableFuture<Float> readFloat(NodeId nodeId) {
		CompletableFuture<Float> resFut = new CompletableFuture<>();
		Float res = server.readFloat(nodeId);
		resFut.complete(res);
		return resFut;
	}

	@Override
	public CompletableFuture<Integer> readInteger(NodeId nodeId) {
		CompletableFuture<Integer> resFut = new CompletableFuture<>();
		Integer res = server.readInteger(nodeId);
		resFut.complete(res);
		return resFut;
	}

	@Override
	public CompletableFuture<Double> readDouble(NodeId nodeId) {
		CompletableFuture<Double> resFut = new CompletableFuture<>();
		Double res = server.readDouble(nodeId);
		resFut.complete(res);
		return resFut;
	}

	@Override
	public CompletableFuture<Integer> createMonitoredItem(int clientIndex, Integer subscriptionIndex, NodeId nodeId) {
		CompletableFuture<Integer> res = new CompletableFuture<>();
        ReadValueId readValueId = new ReadValueId(
                nodeId,
                AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE);

        // important: client handle must be unique per item
        UInteger clientHandle = uint(clientHandles.getAndIncrement());

        MonitoringParameters parameters = new MonitoringParameters(
            clientHandle,
            10.0,     // sampling interval
            null,       // filter, null means use default
            uint(10),   // queue size
            true        // discard oldest
        );

        MonitoredItemCreateRequest request = new MonitoredItemCreateRequest(
            readValueId, MonitoringMode.Reporting, parameters);
        
        ValueMapper mapper = new ValueMapper(server.getTheNamespace());
        monId++;
        //System.out.println(monId);
        handlers.put(monId, mapper);
        //System.out.println(subscriptionIndex);
        UaSubscription subscription = subscriptions.get(subscriptionIndex);
        
        
        BiConsumer<UaMonitoredItem, Integer> onItemCreated = (item, id) -> item.setValueConsumer(mapper);

        try {
            List<UaMonitoredItem> items = subscription.createMonitoredItems(
                TimestampsToReturn.Both,
                newArrayList(request),
                onItemCreated
            ).get();
            res.complete(monId);
            monitoreds.put(monId, items.get(0));
        } catch(Exception e) {
        	e.printStackTrace();
        	res.complete(-1);
        }
        return res;
	}

	@Override
	public CompletableFuture<StatusCode> destroyMonitoredItem(int clientIndex, Integer subscriptionId,
			int monitoredItemId) {
        CompletableFuture<StatusCode> res = new CompletableFuture<>();
        UaSubscription subscription = subscriptions.get(subscriptionId);
		UaMonitoredItem mon = monitoreds.get(monitoredItemId);
		ValueMapper map = handlers.get(monitoredItemId);
		monitoreds.remove(monitoredItemId);
		handlers.remove(monitoredItemId);
		List<UaMonitoredItem> items = new LinkedList<>();
		items.add(mon);
		subscription.deleteMonitoredItems(items);
        res.complete(StatusCode.GOOD);
        return res;
		
	}

	@Override
	public CompletableFuture<StatusCode> createMapping(Integer subscriptionId, int monitoredItemId, NodeId nodeId) {
		System.out.println(monitoredItemId + " --> " + nodeId);
        ValueMapper mapper = handlers.get(monitoredItemId);
        CompletableFuture<StatusCode> res = new CompletableFuture<>();
        if(mapper != null) {
        	mapper.setTarget(nodeId);
            res.complete(StatusCode.GOOD);
        } else {
        	System.out.println("mapper is null");
        	res.complete(StatusCode.BAD);
        }
        return res;
	}

	@Override
	public CompletableFuture<StatusCode> destroyMapping(Integer subscriptionId, int monitoredItemId) {
		ValueMapper m = handlers.get(monitoredItemId);
		CompletableFuture<StatusCode> res = new CompletableFuture<>();
		res.complete(StatusCode.GOOD);
		return res;
	}

	@Override
	public CompletableFuture<StatusCode> stopServer() {
		CompletableFuture<StatusCode> resFuture = new CompletableFuture<>();
		try {
			CompletableFuture<OpcUaServer> shutFuture = server.shutdown();
			OpcUaServer serv = shutFuture.get();
			resFuture.complete(StatusCode.GOOD);
		} catch (InterruptedException e) {
			e.printStackTrace();
			resFuture.complete(StatusCode.BAD);
		} catch (ExecutionException e) {
			e.printStackTrace();
			resFuture.complete(StatusCode.BAD);
		}
		return resFuture;
	}

	@Override
	public UShort getNamespaceIndex(int clientIndex) {
		return foreignNamespaces.get(clientIndex);
	}

}

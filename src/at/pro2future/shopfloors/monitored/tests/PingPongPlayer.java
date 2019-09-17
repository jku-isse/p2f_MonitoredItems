package at.pro2future.shopfloors.monitored.tests;

import java.util.concurrent.CompletableFuture;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;

import at.pro2future.shopfloors.monitored.MonitoredItemConfiguration;

public class PingPongPlayer implements Runnable{
	OpcUaClient client;
	int port;
	NodeId localId;
	NodeId remoteId;
	int localVal;
	public boolean running = false;
	public boolean ready = false;
	String myName;
	
	public PingPongPlayer(int otherPort, OpcUaClient ownClient, boolean starter, String name) {
		myName = name;
		port = otherPort;
		client = ownClient;
		try {
			client.connect().get();
	        CompletableFuture<NodeId> localFuture = MonitoredItemConfiguration.addIntegerVariable(client, 1, "localValue", "localValue");
	        localId = localFuture.get();
        	System.out.println(name + " add localVal");
	        if(starter) {
	        	localVal = 1;
	        } else {
	        	localVal = 0;
	        }
        	CompletableFuture writeF = MonitoredItemConfiguration.writeInteger(ownClient, localId, localVal);
        	writeF.get();
        	System.out.println(name + " set localVal");
	        CompletableFuture<NodeId> remoteFuture = MonitoredItemConfiguration.addIntegerVariable(client, 1, "remoteValue", "remoteValue");
	        remoteId = remoteFuture.get();
        	System.out.println(name + " add remoteVal");
        	CompletableFuture writeFg = MonitoredItemConfiguration.writeInteger(ownClient, remoteId, -5);
        	writeF.get();
        	System.out.println(name + " set remoteVal");
        	ready = true;
		} catch(Exception e) {
			
		}
	}

	@Override
	public void run() {
		try {
			CompletableFuture<Integer> publishFuture = MonitoredItemConfiguration.createClient(client, 
					String.format("opc.tcp://127.0.0.1:%d",port));
	        Integer index = publishFuture.get();
        	System.out.println(myName + " createClient " + index);
	        CompletableFuture<Integer> subscribeFuture = MonitoredItemConfiguration.createSubscription(client, index);
	        Integer subIndex = subscribeFuture.get();
        	System.out.println(myName + " createSubscription " + subIndex);
			CompletableFuture<Integer> monitorFuture = MonitoredItemConfiguration.createMonitoredItem(client, index, subIndex, new NodeId(1, 4000));
	        Integer monIndex = monitorFuture.get();
        	System.out.println(myName + " createMonitoredItem " +monIndex);
	        CompletableFuture<StatusCode> mapFuture = MonitoredItemConfiguration.createMapping(client, subIndex, monIndex, remoteId);
	        StatusCode code = mapFuture.get();
        	System.out.println(myName + " map");

			running = true;
			
	        Integer val = 0;
	        do {
	        	CompletableFuture<Integer> fFuture = MonitoredItemConfiguration.readInteger(client, remoteId);
	        	val = fFuture.get();
	        	if(val > localVal) {
	        		localVal = val + 1;
	        		System.out.println(myName + ": " + localVal);
		        	CompletableFuture writeF = MonitoredItemConfiguration.writeInteger(client, localId, localVal);
		        	writeF.get();
	        	}
	        	Thread.sleep(500);
	        } while(localVal < 20 || val < 20);
	        
	        client.disconnect().get();
		} catch(Exception e) {
			
		}
        running = false;
	}
}

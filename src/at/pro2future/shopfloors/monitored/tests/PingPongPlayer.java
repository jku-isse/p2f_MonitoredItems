package at.pro2future.shopfloors.monitored.tests;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import at.pro2future.shopfloors.monitored.MonitoredItemConfiguration;
import at.pro2future.shopfloors.monitored.milo.MiloConfiguration;
import at.pro2future.shopfloors.monitored.open.OpenServerConfiguration;

/**
 * An entity for cooperative counting, with all entities on localHost.
 * Synchronises with a second PingPongPlayer on distantPort
 * @author Darth_Mue
 *
 */
public class PingPongPlayer implements Runnable{
	
	OpcUaClient client;
	//port of the other player
	int distantPort;
	int remoteClientIndex;
	int subIndex, monIndex;
	
	//node id of the local and remote counting state in the opc namespace
	NodeId localId;
	NodeId remoteId;
	
	// local counting state
	int localVal;
	
	//Wether this is the starting or the second player 
	public boolean starting;
	
	// flags to indicate wether the player has reached a certain progress
	public boolean started;
	public boolean initialized;
	public boolean ready;
	public boolean finished;
	public boolean stopped;

	
	// flags to signal that the next initialisation step can be made
	public boolean upsetting;
	public boolean initializing;
	public boolean going;

	// Name for debug prints
	String myName;
	
	// the configurator for the opc namespace, server and data information exchange
	MonitoredItemConfiguration conf;
	
	public PingPongPlayer(int myPort, int otherPort, boolean starter, String name) throws Exception {
		
		upsetting = false;
		initializing = false;
		going = false;
		
		finished = false;
		started = false;
		initialized = false;
		ready = false;
		stopped = false;
		
		starting = starter;
		myName = name;
		distantPort = otherPort;
		
		conf = new MiloConfiguration(myPort);
		client = conf.getConfigurationClient();
		
		// Server Variable creation and initialisation with different, 
		// but clearly nonsense values to get more information during debugging
		try {
	        CompletableFuture<NodeId> localFuture = conf.addIntegerVariable(1, "localValue", "Lokaler Wert", "localValue");
	        localId = localFuture.get();
        	System.out.println(name + " add localVal");

        	CompletableFuture<StatusCode> writeF = conf.writeInteger(localId, -3);
        	writeF.get();
        	
	        CompletableFuture<NodeId> remoteFuture = conf.addIntegerVariable(1, "remoteValue", "remoteValue", "remoteValue");
	        remoteId = remoteFuture.get();
        	System.out.println(name + " add remoteVal");
        	
        	CompletableFuture<StatusCode> writeFg = conf.writeInteger(remoteId, -5);
        	writeFg.get();
        	writeFg = conf.writeInteger(localId, -15);
        	writeFg.get();
        	
        	started = true;
		} catch(Exception e) {
			
		}
	}

	@Override
	public void run() {
		// wait until other Player is ready
		while(!upsetting) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		// create client, subscription and monitored item
		try {
			System.out.println(myName + " READY FOR SETUP");
			
			// create client on remote player
			String url = String.format("opc.tcp://127.0.0.1:%d",distantPort);
			CompletableFuture<Integer> publishFuture = conf.createClient(url);
	        remoteClientIndex = publishFuture.get();
	    	System.out.println(myName + " createClient " + remoteClientIndex + " to " + url);
	    	
	    	// create subscription
	        CompletableFuture<Integer> subscribeFuture = conf.createSubscription(remoteClientIndex);
	        subIndex = subscribeFuture.get();
	    	System.out.println(myName + " createSubscription " + subIndex);
	    	
	    	// create monitored item on remote "localValue"
	    	NodeId targetId = new NodeId(conf.getNamespaceIndex(remoteClientIndex), "localValue");
			CompletableFuture<Integer> monitorFuture = conf.createMonitoredItem(remoteClientIndex, subIndex, targetId);
	        monIndex = monitorFuture.get();
	    	System.out.println(myName + " createMonitoredItem " +monIndex);
	    	
	    	// map updates from the monitored item to the local server variable "remoteValue"
	        CompletableFuture<StatusCode> mapFuture = conf.createMapping(subIndex, monIndex, remoteId);
	        StatusCode code = mapFuture.get();
	    	System.out.println(myName + " map " +subIndex+"/"+monIndex+" to " + remoteId +" result: "+ code);
	    	
	    	initialized = true;
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		// wait until remote player has set up monitored item
		while(!initializing) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println(myName + " READY FOR INIT");

		// almost ready to count
		try {
			// another distinguishable nonsense value for debugging
	    	CompletableFuture<StatusCode> writeF = conf.writeInteger(localId, -300);
	    	writeF.get();
	    	Thread.sleep(300);
	        if(starting) {
	        	localVal = 1;
	        } else {
	        	localVal = 0;
	        }
	        // set starting values for counting
	    	 writeF = conf.writeInteger(localId, localVal);
	    	writeF.get();
	    	Thread.sleep(300);
	    	System.out.println(myName + " set localVal");
		} catch(Exception e) {
			e.printStackTrace();
		}
		ready = true;
		// wait for variable initialisation on remote player
		while(!going) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		// ready to play
		try {
			
	        Integer val = 0;
	        Integer inactive = 0;
	        
	        // whenever remote counting value exceeds local counting value, 
	        // increase local counting value to remote value + 1
	        // and write it to server
	        // which causes transmission to remote player via the remote players monitored item
	        
	        // the "inactive" counter guarantees safe termination of the test in case
	        // something in the monitored item configuration fails and no updates arrive
	        
	        do {
	        	CompletableFuture<Integer> fFuture = conf.readInteger(remoteId);
	        	val = fFuture.get();
	        	if(val > localVal) {
	        		inactive = 0;
	        		localVal = val + 1;
	        		System.out.println(myName + ": " + localVal);
		        	CompletableFuture<StatusCode> writeF = conf.writeInteger(localId, localVal);
		        	writeF.get();
	        	} else {
	        		inactive ++;
	        	}
	        	Thread.sleep(100);
	        } while(inactive < 1000 &&( localVal < 18 || val < 18));
			//Thread.sleep(20000);
	        
	        //cleanup
	        conf.destroyMapping(remoteClientIndex, monIndex).get();
	        conf.destroyMonitoredItem(remoteClientIndex, subIndex, monIndex).get();
	        conf.destroyClient(remoteClientIndex).get();
	        conf.stopServer().get();
	        
	        stopped = true;
	        	        
		} catch(Exception e) {
			e.printStackTrace();
		}
        CompletableFuture<StatusCode> stopFuture = conf.stopServer();
        try {
			stopFuture.get();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        finished = true;
	}
	public void forceStop() {
        CompletableFuture<StatusCode> stopFuture = conf.stopServer();
        try {
			stopFuture.get();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}

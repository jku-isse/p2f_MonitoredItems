package at.pro2future.shopfloors.monitored.tests;

import java.util.concurrent.CompletableFuture;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;

import at.pro2future.shopfloors.monitored.MonitoredItemConfiguration;

public class PingPongTest {
	//run 4850.bat
	//run 4860.bat
	
	public static void main(String[] args) {
		try {
		CompletableFuture<OpcUaClient> aFuture = MonitoredItemConfiguration.connectToServer("opc.tcp://127.0.0.1:4850");
		OpcUaClient clientA = aFuture.get();
		CompletableFuture<OpcUaClient> bFuture = MonitoredItemConfiguration.connectToServer("opc.tcp://127.0.0.1:4860");
		OpcUaClient clientB = bFuture.get();
		
		PingPongPlayer rA = new PingPongPlayer(4860, clientA, true, "Arianne");
		PingPongPlayer rB = new PingPongPlayer(4850, clientB, false,"Bigelow");
		
		while(!(rA.ready && rB.ready)) {
			Thread.sleep(500);
		}

		Thread tA = new Thread(rA);
		Thread tB = new Thread(rB);
		tA.start();
		while(!rA.running) {
			Thread.sleep(50);
		}
		tB.start();
		
		
		while(rA.running && rB.running) {
			Thread.sleep(500);
		}
		
		} catch(Exception e) {
			
		}
		
	}
}

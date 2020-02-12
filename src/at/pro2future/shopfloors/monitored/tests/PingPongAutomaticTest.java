package at.pro2future.shopfloors.monitored.tests;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;

import at.pro2future.shopfloors.monitored.MonitoredItemConfiguration;
import at.pro2future.shopfloors.monitored.milo.MiloConfiguration;
import at.pro2future.shopfloors.monitored.open.OpenServerConfiguration;

public class PingPongAutomaticTest {
	
	// When using the deprecated OpenServerConfiguration,
	// make sure that "SimplifiedCompile.exe" is found by java
	
	
	// setup script to realise cooperative counting
	// using two PingPongPlayers
	public static void main(String[] args) {
		try {
			int portA = 4850;
			int portB = 4860;			
			
			PingPongPlayer rA = new PingPongPlayer(portA, portB, true, "Arianne");
			PingPongPlayer rB = new PingPongPlayer(portB, portA, false,"Bigelow");
			while(!(rA.started && rB.started)) {
				Thread.sleep(50);
			}
			// both servers are started and have variables set up
			
			
			Thread tA = new Thread(rA);
			Thread tB = new Thread(rB);
			tA.start();
			tB.start();
			rA.upsetting = true;
			rB.upsetting = true;
			while(!(rA.initialized && rB.initialized)) {
				Thread.sleep(50);
			}
			// both servers have subscriptions and monitored items set up
			
			rA.initializing = true;
			rB.initializing=true;
			while(!(rA.ready && rB.ready)) {
				Thread.sleep(50);
			}
			// both servers have variables initialised to useful values
			
			rA.going = true;
			rB.going = true;
			while(!(rA.finished && rB.finished)) {
				Thread.sleep(50);
			}
			// both servers finished their main thread
			
			if(!rA.stopped) {
				rA.forceStop();
			}
			if(!rB.stopped) {
				rB.forceStop();
			}
			
			
			System.out.println("Done");
		} catch(Exception e) {
			e.printStackTrace();
		}
		
	}
}

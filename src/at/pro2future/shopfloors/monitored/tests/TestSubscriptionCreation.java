package at.pro2future.shopfloors.monitored.tests;

import java.util.concurrent.CompletableFuture;

import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;

import at.pro2future.shopfloors.monitored.MonitoredItemConfiguration;
import at.pro2future.shopfloors.monitored.milo.MiloConfiguration;
import at.pro2future.shopfloors.monitored.open.OpenServerConfiguration;

public class TestSubscriptionCreation {

	public static void main(String[] args) {
		try {
			MonitoredItemConfiguration confA = new MiloConfiguration(4850);
			MonitoredItemConfiguration confB = new MiloConfiguration(4860);
			
			CompletableFuture<NodeId> intFuture = confA.addIntegerVariable(1, "origin", "Original Integer", "originInt");
			NodeId originalId = intFuture.get();
			 intFuture = confB.addIntegerVariable(1, "origin", "Original Integer", "originInt");
			NodeId originalIdB = intFuture.get();
			CompletableFuture<NodeId> subIntFuture = confA.addIntegerVariable(1, "subscribe", "Subscribed Integer", "subscribeInt");
			NodeId subIntId = subIntFuture.get();
			
			 subIntFuture = confB.addIntegerVariable(1, "subscribe", "Subscribed Integer", "subscribeInt");
			NodeId subIntIdB = subIntFuture.get();
			
			CompletableFuture<StatusCode> writeFutureB = confB.writeInteger(subIntId, -1);
			StatusCode codeB = writeFutureB.get();
			
			CompletableFuture<StatusCode> writeFutureA = confA.writeInteger(subIntId, -3);
			StatusCode code = writeFutureA.get();
			

			CompletableFuture<StatusCode> writeFutureBB = confB.writeInteger(originalId, -4);
			codeB = writeFutureBB.get();
			
			CompletableFuture<StatusCode> writeFutureAA = confA.writeInteger(originalId, -2);
			code = writeFutureAA.get();

			CompletableFuture<Integer> clientFutureA = confA.createClient("opc.tcp://127.0.0.1:4860");
			Integer clientIdA = clientFutureA.get();
			CompletableFuture<Integer> clientFutureB = confB.createClient("opc.tcp://127.0.0.1:4850");
			Integer clientIdB = clientFutureB.get();
			
			CompletableFuture<Integer> subscriptionFutureA = confA.createSubscription(clientIdB);
			Integer subscriptionIdA = subscriptionFutureA.get();
			
			CompletableFuture<Integer> subscriptionFutureB = confB.createSubscription(clientIdA);
			Integer subscriptionIdB = subscriptionFutureB.get();
			
			CompletableFuture<Integer> monFutureA = confA.createMonitoredItem(clientIdB, subscriptionIdB, originalId);
			Integer monIdA = monFutureA.get();

			CompletableFuture<Integer> monFutureB = confB.createMonitoredItem(clientIdA, subscriptionIdA, originalId);
			Integer monIdB = monFutureB.get();
			
			CompletableFuture<StatusCode> mapFutureA = confA.createMapping(subscriptionIdA, monIdA, subIntId);
			StatusCode mapCodeA = mapFutureA.get();

			CompletableFuture<StatusCode> mapFutureB = confB.createMapping(subscriptionIdB, monIdB, subIntId);
			StatusCode mapCodeB = mapFutureB.get();
			
			Thread.sleep(1500);
			writeFutureA = confA.writeInteger(originalId, 3);
			code = writeFutureA.get();
			writeFutureB = confB.writeInteger(originalId, 2);
			code = writeFutureB.get();
			Thread.sleep(300);
			writeFutureA = confA.writeInteger(originalId, 6);
			code = writeFutureA.get();
			writeFutureB = confB.writeInteger(originalId, 5);
			code = writeFutureB.get();
			Thread.sleep(300);
			writeFutureA = confA.writeInteger(originalId, 9);
			code = writeFutureA.get();
			writeFutureB = confB.writeInteger(originalId, 8);
			code = writeFutureB.get();
			Thread.sleep(300);
			writeFutureA = confA.writeInteger(originalId, 12);
			code = writeFutureA.get();
			writeFutureB = confB.writeInteger(originalId, 11);
			code = writeFutureB.get();
			Thread.sleep(300);
			writeFutureA = confA.writeInteger(originalId, 15);
			code = writeFutureA.get();
			writeFutureB = confB.writeInteger(originalId, 14);
			code = writeFutureB.get();
			Thread.sleep(300);
			
			
			CompletableFuture<Integer> readFuture = confB.readInteger(subIntId);
			Integer read = readFuture.get();
			System.out.println(read);
			
			//Thread.sleep(10000);
			
			CompletableFuture<StatusCode> destructionFuture = confB.destroyClient(clientIdA);
			StatusCode destructionCode = destructionFuture.get();
			
			CompletableFuture<StatusCode> aFuture = confA.stopServer();
			StatusCode aCode = aFuture.get();
			System.out.println(aCode);

			CompletableFuture<StatusCode> bFuture = confB.stopServer();
			StatusCode bCode = bFuture.get();
			System.out.println(bCode);
			
			
			
		} catch(Exception e) {
			e.printStackTrace();
		}
	
	}
}

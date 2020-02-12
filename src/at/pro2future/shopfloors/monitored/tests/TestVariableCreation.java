package at.pro2future.shopfloors.monitored.tests;

import java.util.concurrent.CompletableFuture;

import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;

import at.pro2future.shopfloors.monitored.MonitoredItemConfiguration;
import at.pro2future.shopfloors.monitored.milo.MiloConfiguration;
import at.pro2future.shopfloors.monitored.open.OpenServerConfiguration;

public class TestVariableCreation {
	public static void main(String[] args) {
		try {
			//OpenServerConfiguration confA = new OpenServerConfiguration(4850);
			MonitoredItemConfiguration confA = new MiloConfiguration(4840);
			
			CompletableFuture<NodeId> intFuture = confA.addIntegerVariable(1, "demo", "Demo Integer", "testInt");
			NodeId intId = intFuture.get();
			
			CompletableFuture<StatusCode> writeFuture = confA.writeInteger(intId, 15);
			StatusCode code = writeFuture.get();
			
			CompletableFuture<Integer> readFuture = confA.readInteger(intId);
			Integer read = readFuture.get();
			System.out.println(read);
			
			//Thread.sleep(20000);
			
			CompletableFuture<StatusCode> aFuture = confA.stopServer();
			StatusCode aCode = aFuture.get();
			System.out.println(aCode);
			
			
			
		} catch(Exception e) {
			e.printStackTrace();
		}
	
	}
}

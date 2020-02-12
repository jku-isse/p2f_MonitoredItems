package at.pro2future.shopfloors.monitored.tests;

import java.util.concurrent.CompletableFuture;

import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;

import at.pro2future.shopfloors.monitored.MonitoredItemConfiguration;
import at.pro2future.shopfloors.monitored.milo.MiloConfiguration;
import at.pro2future.shopfloors.monitored.open.OpenServerConfiguration;

public class TestClientCreation {
	public static void main(String[] args) {
		try {
			/*
			OpenServerConfiguration confA = new OpenServerConfiguration(4850);
			OpenServerConfiguration confB = new OpenServerConfiguration(4860);
			*/
			MonitoredItemConfiguration confA = new MiloConfiguration(4850);
			System.out.println("Server A started");
			MonitoredItemConfiguration confB = new MiloConfiguration(4860);
			System.out.println("Server B started");
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
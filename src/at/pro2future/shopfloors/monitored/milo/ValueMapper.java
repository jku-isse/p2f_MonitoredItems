package at.pro2future.shopfloors.monitored.milo;

import java.util.function.Consumer;

import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.server.model.nodes.variables.BaseVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

public class ValueMapper implements Consumer<DataValue>{

    private NodeId target;
    private Namespace names;
    
    public ValueMapper(Namespace space) {
    	target = null;
    	names = space;
    }
    
    public void setTarget(NodeId nodeId) {
    	target = nodeId;
    }

	@Override
	public void accept(DataValue t) {
		//System.out.println("accepting " + t + " for " + target);
    	if(target != null) {
    		UaNode node = names.getTheNodeManager().get(target);
    		try {
    			BaseVariableNode bvn = (BaseVariableNode)node;
    			bvn.setValue(t);
    		} catch(Exception e) {
    			
    		}
    	}
	}
}

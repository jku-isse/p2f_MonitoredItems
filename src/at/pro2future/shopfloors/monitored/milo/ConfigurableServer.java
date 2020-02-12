/*
 * Copyright (c) 2019 the Eclipse Milo Authors
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package at.pro2future.shopfloors.monitored.milo;

import java.io.File;
import java.security.KeyPair;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.identity.CompositeValidator;
import org.eclipse.milo.opcua.sdk.server.identity.UsernameIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.identity.X509IdentityValidator;
import org.eclipse.milo.opcua.sdk.server.model.nodes.variables.BaseVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaRuntimeException;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo;
import org.eclipse.milo.opcua.stack.core.util.CertificateUtil;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedHttpsCertificateBuilder;
import org.eclipse.milo.opcua.stack.server.EndpointConfiguration;
import org.slf4j.LoggerFactory;

import at.pro2future.shopfloors.monitored.KeyStoreLoader;
import at.pro2future.shopfloors.monitored.KeyStoreLoaderServer;

import static com.google.common.collect.Lists.newArrayList;
import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS;
import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME;
import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_X509;

public class ConfigurableServer {
	
	int myPort;
	Namespace configurableSpace;
	int nextId = 4000;
	
    static {
        // Required for SecurityPolicy.Aes256_Sha256_RsaPss
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void main(String[] args) throws Exception {
    	ConfigurableServer server = new ConfigurableServer(4840);

        server.startup().get();
        System.out.println("Server started");

        final CompletableFuture<Void> future = new CompletableFuture<>();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> future.complete(null)));

        future.get();
    }

    private final OpcUaServer server;

    public ConfigurableServer(int port) throws Exception {
    	myPort = port;
        File securityTempDir = new File(System.getProperty("java.io.tmpdir"), "security");
        if (!securityTempDir.exists() && !securityTempDir.mkdirs()) {
            throw new Exception("unable to create security temp dir: " + securityTempDir);
        }
        LoggerFactory.getLogger(getClass()).info("security temp dir: {}", securityTempDir.getAbsolutePath());

        KeyStoreLoaderServer loader = new KeyStoreLoaderServer().load(securityTempDir);

        DefaultCertificateManager certificateManager = new DefaultCertificateManager(
            loader.getServerKeyPair(),
            loader.getServerCertificateChain()
        );

        File pkiDir = securityTempDir.toPath().resolve("pki").toFile();
        DefaultTrustListManager trustListManager = new DefaultTrustListManager(pkiDir);
        LoggerFactory.getLogger(getClass()).info("pki dir: {}", pkiDir.getAbsolutePath());

        DefaultCertificateValidator certificateValidator = new DefaultCertificateValidator(trustListManager);

        KeyPair httpsKeyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);

        SelfSignedHttpsCertificateBuilder httpsCertificateBuilder = new SelfSignedHttpsCertificateBuilder(httpsKeyPair);
        httpsCertificateBuilder.setCommonName(HostnameUtil.getHostname());
        HostnameUtil.getHostnames("0.0.0.0").forEach(httpsCertificateBuilder::addDnsName);
        X509Certificate httpsCertificate = httpsCertificateBuilder.build();

        UsernameIdentityValidator identityValidator = new UsernameIdentityValidator(
            true,
            authChallenge -> {
                String username = authChallenge.getUsername();
                String password = authChallenge.getPassword();

                boolean userOk = "user".equals(username) && "password1".equals(password);
                boolean adminOk = "admin".equals(username) && "password2".equals(password);

                return userOk || adminOk;
            }
        );

        X509IdentityValidator x509IdentityValidator = new X509IdentityValidator(c -> true);

        // If you need to use multiple certificates you'll have to be smarter than this.
        X509Certificate certificate = certificateManager.getCertificates()
            .stream()
            .findFirst()
            .orElseThrow(() -> new UaRuntimeException(StatusCodes.Bad_ConfigurationError, "no certificate found"));

        // The configured application URI must match the one in the certificate(s)
        String applicationUri = CertificateUtil
            .getSanUri(certificate)
            .orElseThrow(() -> new UaRuntimeException(
                StatusCodes.Bad_ConfigurationError,
                "certificate is missing the application URI"));

        Set<EndpointConfiguration> endpointConfigurations = createEndpointConfigurations(certificate);

        OpcUaServerConfig serverConfig = OpcUaServerConfig.builder()
            .setApplicationUri(applicationUri)
            .setApplicationName(LocalizedText.english("Eclipse Milo OPC UA Example Server"))
            .setEndpoints(endpointConfigurations)
            .setBuildInfo(
                new BuildInfo(
                    "urn:eclipse:milo:example-server",
                    "eclipse",
                    "eclipse milo example server",
                    OpcUaServer.SDK_VERSION,
                    "", DateTime.now()))
            .setCertificateManager(certificateManager)
            .setTrustListManager(trustListManager)
            .setCertificateValidator(certificateValidator)
            .setHttpsKeyPair(httpsKeyPair)
            .setHttpsCertificate(httpsCertificate)
            .setIdentityValidator(new CompositeValidator(identityValidator, x509IdentityValidator))
            .setProductUri("urn:eclipse:milo:example-server")
            .build();

        server = new OpcUaServer(serverConfig);

        configurableSpace = new Namespace(server);
        configurableSpace.startup();
    }

    private Set<EndpointConfiguration> createEndpointConfigurations(X509Certificate certificate) {
        Set<EndpointConfiguration> endpointConfigurations = new LinkedHashSet<>();

        List<String> bindAddresses = newArrayList();
        bindAddresses.add("0.0.0.0");

        Set<String> hostnames = new LinkedHashSet<>();
        hostnames.add(HostnameUtil.getHostname());
        hostnames.addAll(HostnameUtil.getHostnames("0.0.0.0"));

        for (String bindAddress : bindAddresses) {
            for (String hostname : hostnames) {
                EndpointConfiguration.Builder builder = EndpointConfiguration.newBuilder()
                    .setBindAddress(bindAddress)
                    .setHostname(hostname)
                    .setPath("")
                    .setCertificate(certificate)
                    .addTokenPolicies(
                        USER_TOKEN_POLICY_ANONYMOUS,
                        USER_TOKEN_POLICY_USERNAME,
                        USER_TOKEN_POLICY_X509);


                EndpointConfiguration.Builder noSecurityBuilder = builder.copy()
                    .setSecurityPolicy(SecurityPolicy.None)
                    .setSecurityMode(MessageSecurityMode.None);

                endpointConfigurations.add(buildTcpEndpoint(myPort, noSecurityBuilder));


                /*
                 * It's good practice to provide a discovery-specific endpoint with no security.
                 * It's required practice if all regular endpoints have security configured.
                 *
                 * Usage of the  "/discovery" suffix is defined by OPC UA Part 6:
                 *
                 * Each OPC UA Server Application implements the Discovery Service Set. If the OPC UA Server requires a
                 * different address for this Endpoint it shall create the address by appending the path "/discovery" to
                 * its base address.
                 */
                
                EndpointConfiguration.Builder discoveryBuilder = builder.copy()
                    .setPath("/discovery")
                    .setSecurityPolicy(SecurityPolicy.None)
                    .setSecurityMode(MessageSecurityMode.None);

                endpointConfigurations.add(buildTcpEndpoint(myPort, discoveryBuilder));
                
            }
        }

        return endpointConfigurations;
    }

    private static EndpointConfiguration buildTcpEndpoint(int port, EndpointConfiguration.Builder base) {
        return base.copy()
            .setTransportProfile(TransportProfile.TCP_UASC_UABINARY)
            .setBindPort(port)
            .build();
    }

    public OpcUaServer getServer() {
        return server;
    }

    public CompletableFuture<OpcUaServer> startup() {
        return server.startup();
    }

    public CompletableFuture<OpcUaServer> shutdown() {
        return server.shutdown();
    }
    
    public NodeId addInteger(int namespace, String identifier, String displayName, String browseName) {
    	try {
			NodeId n= configurableSpace.getNewNodeId(identifier);
			BaseVariableNode uaNode = (BaseVariableNode) configurableSpace.getTheNodeFactory().createNode(
					n,
					Identifiers.BaseVariableType,
				false);
			uaNode.setDisplayName(new LocalizedText(displayName));
			uaNode.setBrowseName(new QualifiedName(namespace, browseName));
			uaNode.setDataType(Identifiers.Integer);
			uaNode.setValue(new DataValue(new Variant(0)));
			configurableSpace.getTheNodeManager().addNode(uaNode);
			
			uaNode.addReference(new Reference(
					uaNode.getNodeId(),
		        Identifiers.Organizes,
		        Identifiers.ObjectsFolder.expanded(),
		        false
		    ));
	    	configurableSpace.getTheNodeManager().addNode(uaNode);
			return n;
    	} catch(Exception e) {
    		e.printStackTrace();
    		return null;
    	}
    }

    
    public NodeId addDouble(int namespace, String identifier, String displayName, String browseName) {
    	try {
			NodeId n= configurableSpace.getNewNodeId(identifier);
			BaseVariableNode uaNode = (BaseVariableNode) configurableSpace.getTheNodeFactory().createNode(
					n,
					Identifiers.BaseVariableType,
				false);
			uaNode.setDisplayName(new LocalizedText(displayName));
			uaNode.setBrowseName(new QualifiedName(namespace, browseName));
			uaNode.setDataType(Identifiers.Double);
			uaNode.setValue(new DataValue(new Variant(0.0)));
			configurableSpace.getTheNodeManager().addNode(uaNode);
			
			uaNode.addReference(new Reference(
					uaNode.getNodeId(),
		        Identifiers.Organizes,
		        Identifiers.ObjectsFolder.expanded(),
		        false
		    ));
	    	configurableSpace.getTheNodeManager().addNode(uaNode);
			return n;
    	} catch(Exception e) {
    		e.printStackTrace();
    		return null;
    	}
    }
    
    public NodeId addFloat(int namespace, String identifier, String displayName, String browseName) {
    	try {
			NodeId n= configurableSpace.getNewNodeId(identifier);
			BaseVariableNode uaNode = (BaseVariableNode) configurableSpace.getTheNodeFactory().createNode(
					n,
					Identifiers.BaseVariableType,
				false);
			uaNode.setDisplayName(new LocalizedText(displayName));
			uaNode.setBrowseName(new QualifiedName(namespace, browseName));
			uaNode.setDataType(Identifiers.Float);
			uaNode.setValue(new DataValue(new Variant(0.0f)));
			configurableSpace.getTheNodeManager().addNode(uaNode);
			
			uaNode.addReference(new Reference(
					uaNode.getNodeId(),
		        Identifiers.Organizes,
		        Identifiers.ObjectsFolder.expanded(),
		        false
		    ));
	    	configurableSpace.getTheNodeManager().addNode(uaNode);
			return n;
    	} catch(Exception e) {
    		e.printStackTrace();
    		return null;
    	}
    }
    
    public NodeId addBoolean(int namespace, String identifier, String displayName, String browseName) {
    	try {
			NodeId n= configurableSpace.getNewNodeId(identifier);
			BaseVariableNode uaNode = (BaseVariableNode) configurableSpace.getTheNodeFactory().createNode(
					n,
					Identifiers.BaseVariableType,
				false);
			uaNode.setDisplayName(new LocalizedText(displayName));
			uaNode.setBrowseName(new QualifiedName(namespace, browseName));
			uaNode.setDataType(Identifiers.Boolean);
			uaNode.setValue(new DataValue(new Variant(false)));
			configurableSpace.getTheNodeManager().addNode(uaNode);
			
			uaNode.addReference(new Reference(
					uaNode.getNodeId(),
		        Identifiers.Organizes,
		        Identifiers.ObjectsFolder.expanded(),
		        false
		    ));
	    	configurableSpace.getTheNodeManager().addNode(uaNode);
			return n;
    	} catch(Exception e) {
    		e.printStackTrace();
    		return null;
    	}
    }
    
    public Integer readInteger(NodeId nodeId) {
    	UaNode theNode = configurableSpace.getTheNodeManager().get(nodeId);
    	if(theNode != null) {
    		BaseVariableNode bvn = (BaseVariableNode)theNode;
    		return (Integer)bvn.getValue().getValue().getValue();
    	}
    	return Integer.MIN_VALUE;
    }
    
    public Float readFloat(NodeId nodeId) {
    	UaNode theNode = configurableSpace.getTheNodeManager().get(nodeId);
    	if(theNode != null) {
    		BaseVariableNode bvn = (BaseVariableNode)theNode;
    		return (Float)bvn.getValue().getValue().getValue();
    	}
    	return Float.MIN_VALUE;
    }
    
    public Double readDouble(NodeId nodeId) {
    	UaNode theNode = configurableSpace.getTheNodeManager().get(nodeId);
    	if(theNode != null) {
    		BaseVariableNode bvn = (BaseVariableNode)theNode;
    		return (Double)bvn.getValue().getValue().getValue();
    	}
    	return Double.MIN_VALUE;
    }
    
    public boolean writeInteger(NodeId nodeId, Integer val) {
    	UaNode theNode = configurableSpace.getTheNodeManager().get(nodeId);
    	if(theNode != null) {
    		BaseVariableNode bvn = (BaseVariableNode)theNode;
    		bvn.setValue(new DataValue(new Variant(val)));
    		return true;
    	}
    	return false;
    }
    
    public boolean writeFloat(NodeId nodeId, Float val) {
    	UaNode theNode = configurableSpace.getTheNodeManager().get(nodeId);
    	if(theNode != null) {
    		BaseVariableNode bvn = (BaseVariableNode)theNode;
    		bvn.setValue(new DataValue(new Variant(val)));
    		return true;
    	}
    	return false;
    }
    
    public boolean writeDouble(NodeId nodeId, Double val) {
    	UaNode theNode = configurableSpace.getTheNodeManager().get(nodeId);
    	if(theNode != null) {
    		BaseVariableNode bvn = (BaseVariableNode)theNode;
    		bvn.setValue(new DataValue(new Variant(val)));
    		return true;
    	}
    	return false;
    }
    
    public Namespace getTheNamespace() {
    	return configurableSpace;
    }
}

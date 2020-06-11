package io.oxiles.chain.service.container;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class DefaultBlockchainServiceContainer implements ChainServicesContainer {

    private Map<String, NodeServices> nodeServicesMap;

    public DefaultBlockchainServiceContainer(List<NodeServices> nodeServices) {
        nodeServicesMap = new HashMap<>();
        nodeServices.forEach(ns -> nodeServicesMap.put(ns.getNodeName(), ns));
    }

    @Override
    public NodeServices getNodeServices(String nodeName) {
        return nodeServicesMap.get(nodeName);
    }

    @Override
    public List<String> getNodeNames() {
        return new ArrayList(nodeServicesMap.keySet());
    }
}

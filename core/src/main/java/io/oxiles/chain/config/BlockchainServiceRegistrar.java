package io.oxiles.chain.config;

import lombok.Setter;
import io.oxiles.chain.settings.NodeSettings;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

public class BlockchainServiceRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    @Setter
    private Environment environment;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        final NodeSettings nodeSettings = getNodeSettings();

        nodeSettings.getNodes().forEach((name, node) ->
                getNodeBeanRegistratioStrategy(nodeSettings).register(node, registry));
    }

    protected NodeBeanRegistrationStrategy getNodeBeanRegistratioStrategy(NodeSettings nodeSettings) {
        return new NodeBeanRegistrationStrategy(nodeSettings, new OkHttpClient());

    }

    protected NodeSettings getNodeSettings() {
        return new NodeSettings(environment);
    }
}

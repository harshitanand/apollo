package io.logz.apollo;

import io.logz.apollo.clients.ApolloTestClient;
import io.logz.apollo.dao.DeploymentDao;
import io.logz.apollo.helpers.Common;
import io.logz.apollo.helpers.ModelsGenerator;
import io.logz.apollo.helpers.StandaloneApollo;
import io.logz.apollo.models.DeployableVersion;
import io.logz.apollo.models.Deployment;
import io.logz.apollo.models.Environment;
import io.logz.apollo.models.EnvironmentServiceGroupMap;
import io.logz.apollo.models.Group;
import io.logz.apollo.models.Service;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.script.ScriptException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Java6Assertions.assertThat;

public class UndeployedServicesTest {

    private static StandaloneApollo standaloneApollo;
    private static DeploymentDao deploymentDao;

    @BeforeClass
    public static void beforeClass() throws ScriptException, IOException, SQLException {

        standaloneApollo = StandaloneApollo.getOrCreateServer();
        deploymentDao = standaloneApollo.getInstance(DeploymentDao.class);
    }

    @Test
    public void testUndeployedService() throws Exception {

        ApolloTestClient apolloTestClient = Common.signupAndLogin();
        Environment environment = ModelsGenerator.createAndSubmitEnvironment(apolloTestClient);
        final String availability = environment.getAvailability();
        Service service = ModelsGenerator.createAndSubmitService(apolloTestClient, true);
        Group group = ModelsGenerator.createAndSubmitGroup(apolloTestClient, service.getId(), environment.getId());
        DeployableVersion deployableVersion = ModelsGenerator.createAndSubmitDeployableVersion(apolloTestClient, service);
        Deployment deployment = ModelsGenerator.createAndSubmitDeployment(apolloTestClient, environment, service, deployableVersion, group.getName());
        deploymentDao.updateDeploymentStatus(deployment.getId(), Deployment.DeploymentStatus.DONE);
        Thread.sleep(5000);
        ModelsGenerator.createAndSubmitDeployableVersion(apolloTestClient, service);
        assertThat(isServicePartOfGroupMap(apolloTestClient.getUndeployedServicesByAvailability(availability, TimeUnit.HOURS, 1), service)).isFalse();
        assertThat(isServicePartOfGroupMap(apolloTestClient.getUndeployedServicesByAvailability(availability, TimeUnit.MILLISECONDS, 1), service)).isTrue();
    }

    @Test
    public void neverDeployedService() throws Exception {
        ApolloTestClient apolloTestClient = Common.signupAndLogin();
        Environment environment = ModelsGenerator.createAndSubmitEnvironment(apolloTestClient);
        final String availability = environment.getAvailability();

        Service service = ModelsGenerator.createAndSubmitService(apolloTestClient, true);
        ModelsGenerator.createAndSubmitDeployableVersion(apolloTestClient, service);

        Service serviceWithGroup = ModelsGenerator.createAndSubmitService(apolloTestClient, true);
        ModelsGenerator.createAndSubmitGroup(apolloTestClient, serviceWithGroup.getId(), environment.getId());
        ModelsGenerator.createAndSubmitDeployableVersion(apolloTestClient, serviceWithGroup);

        assertThat(isServicePartOfGroupMap(apolloTestClient.getUndeployedServicesByAvailability(availability, TimeUnit.HOURS, 1), service)).isFalse();
        assertThat(isServicePartOfGroupMap(apolloTestClient.getUndeployedServicesByAvailability(availability, TimeUnit.MILLISECONDS, 1), service)).isFalse();

        assertThat(isServicePartOfGroupMap(apolloTestClient.getUndeployedServicesByAvailability(availability, TimeUnit.HOURS, 1), serviceWithGroup)).isFalse();
        assertThat(isServicePartOfGroupMap(apolloTestClient.getUndeployedServicesByAvailability(availability, TimeUnit.MILLISECONDS, 1), serviceWithGroup)).isFalse();
    }

    private boolean isServicePartOfGroupMap(List<EnvironmentServiceGroupMap> environmentServiceGroupMapList, Service service) {
        return environmentServiceGroupMapList
                .stream()
                .anyMatch(
                        environmentServiceGroupMap ->
                                environmentServiceGroupMap
                                        .getServiceGroupMap().keySet()
                                        .stream()
                                        .anyMatch(serviceKey -> serviceKey.contentEquals(service.getName())));
    }
}
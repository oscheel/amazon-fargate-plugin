package org.jenkinsci.fargate;



import java.io.IOException;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import com.amazonaws.Protocol;
import com.amazonaws.services.ecs.model.*;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsHelper;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;

import hudson.AbortException;
import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;

/**
 * Encapsulates interactions with Amazon ECS.
 *
 * @author Jan Roehrich <jan@roehrich.info>
 *
 */
class ECSService {
    private static final Logger LOGGER = Logger.getLogger(ECSService.class.getName());

    private String credentialsId;

    private String regionName;

    public ECSService(String credentialsId, String regionName) {
        this.credentialsId = credentialsId;
        this.regionName = regionName;
    }

    AmazonECSClient getAmazonECSClient() {
        final AmazonECSClient client;

        ProxyConfiguration proxy = Jenkins.getInstance().proxy;
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        if(proxy != null) {
            clientConfiguration.setProxyHost(proxy.name);
            clientConfiguration.setProxyPort(proxy.port);
            clientConfiguration.setProxyUsername(proxy.getUserName());
            clientConfiguration.setProxyPassword(proxy.getPassword());
        }

        clientConfiguration.setProtocol(Protocol.HTTPS);

        AmazonWebServicesCredentials credentials = getCredentials(credentialsId);
        if (credentials == null) {
            // no credentials provided, rely on com.amazonaws.auth.DefaultAWSCredentialsProviderChain
            // to use IAM Role define at the EC2 instance level ...
            client = new AmazonECSClient(clientConfiguration);
        } else {

            client = new AmazonECSClient(credentials, clientConfiguration);
        }
        client.setRegion(getRegion(regionName));
        LOGGER.log(Level.FINE, "Selected Region: {0}", regionName);
        return client;
    }

    Region getRegion(String regionName) {
        if (StringUtils.isNotEmpty(regionName)) {
            return RegionUtils.getRegion(regionName);
        } else {
            return Region.getRegion(Regions.US_EAST_1);
        }
    }

    @CheckForNull
    private AmazonWebServicesCredentials getCredentials(@Nullable String credentialId) {
        return AWSCredentialsHelper.getCredentials(credentialId, Jenkins.getActiveInstance());
    }

    void deleteTask(String taskArn, String clusterArn) {
        final AmazonECSClient client = getAmazonECSClient();

        LOGGER.log(Level.INFO, "Delete ECS Slave task: {0}", taskArn);
        try {
            client.stopTask(new StopTaskRequest().withTask(taskArn).withCluster(clusterArn));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Couldn't stop task arn " + taskArn + " caught exception: " + e.getMessage(), e);
        }
    }

    ContainerDefinition populateContainerDefintion(final ECSFargateTaskDefinition definition, String familyName, int memory, int cpu){

        ContainerDefinition def = new ContainerDefinition()
                .withName(familyName)
                .withImage(definition.getImage())
                .withEnvironment(definition.getEnvironmentKeyValuePairs())
                .withExtraHosts(definition.getExtraHostEntries())
                .withMountPoints(definition.getMountPointEntries())
/*                .withMemory(memory) */
                .withCpu(0)
                .withPrivileged(definition.isPrivileged())
                .withEssential(true);

        if (!StringUtils.isEmpty(definition.getEntryPoint()))
            def.withEntryPoint(StringUtils.split(definition.getEntryPoint()));

        if (!StringUtils.isEmpty(definition.getJvmArgs()))
            def.withEnvironment(new KeyValuePair()
                    .withName("JAVA_OPTS").withValue(definition.getJvmArgs()))
                    .withEssential(true);

        if (!StringUtils.isEmpty(definition.getLogDriver())) {
            LogConfiguration logConfig = new LogConfiguration();
            logConfig.setLogDriver(definition.getLogDriver());
            logConfig.setOptions(definition.getLogDriverOptionsMap());
            def.withLogConfiguration(logConfig);
        }

        return def;
    }

    /**
     *
     *
     * @param describeTaskDefinition - Result returned from Amazon
     * @param def - Current container def
     * @param fargateTaskDefinition - Current fargate task defintion
     * @return
     */
    boolean matchesSavedDefinition(DescribeTaskDefinitionResult describeTaskDefinition, ContainerDefinition def, ECSFargateTaskDefinition fargateTaskDefinition, int memory, int cpu){

        if(describeTaskDefinition == null){
            return false;
        }


        if(def.equals(describeTaskDefinition.getTaskDefinition().getContainerDefinitions().get(0))){
            LOGGER.log(Level.FINE, "Match on container defintion: template={0}; last={1}", new Object[] {def, describeTaskDefinition.getTaskDefinition().getContainerDefinitions().get(0)});
        }else{
            LOGGER.log(Level.FINE,"Task definition container: "+describeTaskDefinition.getTaskDefinition().getContainerDefinitions().get(0));
            LOGGER.log(Level.FINE,"Cached container def: "+def);
            return false;
        }

         if((fargateTaskDefinition.getVolumeEntries() == null && describeTaskDefinition.getTaskDefinition().getVolumes().isEmpty()) || (ObjectUtils.equals(fargateTaskDefinition.getVolumeEntries(), describeTaskDefinition.getTaskDefinition().getVolumes()))){
             LOGGER.log(Level.FINE, "Match on volumes: template={0}; last={1}", new Object[] {fargateTaskDefinition.getVolumeEntries(), describeTaskDefinition.getTaskDefinition().getVolumes()});
         }else{
            return false;
         }

        TaskDefinition taskDefinition = describeTaskDefinition.getTaskDefinition();
        if(taskDefinition.getCpu().equals(Integer.toString(cpu)) &&
                taskDefinition.getMemory().equals(Integer.toString(memory)) &&
                taskDefinition.getExecutionRoleArn().equals(fargateTaskDefinition.getExecutionRoleArn())){
            LOGGER.log(Level.FINE, "Match on task cpy, memory and execution role: template={0}, taskDefinition={1}", new Object[] {fargateTaskDefinition,taskDefinition});

        }else{
            return false;
        }


        return true;
    }

    /**
     * Looks whether the latest task definition matches the desired one. If yes, returns the ARN of the existing one.
     * If no, register a new task definition with desired parameters and return the new ARN.
     */
    String registerTemplate(final ECSCluster cluster, final ECSFargateTaskDefinition definition) {
        final AmazonECSClient client = getAmazonECSClient();

        int memory = (int)(Double.parseDouble(definition.getMemory())*1024);
        int cpu = (int)(Double.parseDouble(definition.getCpu())*1024);
        String familyName = fullQualifiedTemplateName(cluster.getName(), definition);
        final ContainerDefinition def = populateContainerDefintion(definition,familyName,memory,cpu);


        String lastToken = null;

        ListTaskDefinitionsResult listTaskDefinitions = client.listTaskDefinitions(new ListTaskDefinitionsRequest()
                    .withFamilyPrefix(familyName)
                    .withMaxResults(1)
                    .withSort(SortOrder.DESC)
                    .withNextToken(lastToken));

        LOGGER.log(Level.INFO,"Task definition {0}",listTaskDefinitions.getTaskDefinitionArns());

        DescribeTaskDefinitionResult describeTaskDefinition = null;

        if(listTaskDefinitions.getTaskDefinitionArns().size() > 0){
            describeTaskDefinition = client.describeTaskDefinition((new DescribeTaskDefinitionRequest().withTaskDefinition(listTaskDefinitions.getTaskDefinitionArns().get(0))));
        }

        if(matchesSavedDefinition(describeTaskDefinition,def,definition,memory,cpu)) {
            LOGGER.log(Level.FINE, "Task Definition already exists: {0}", new Object[]{describeTaskDefinition.getTaskDefinition().getTaskDefinitionArn()});
            return describeTaskDefinition.getTaskDefinition().getTaskDefinitionArn();
        } else {
            final RegisterTaskDefinitionRequest request = new RegisterTaskDefinitionRequest()
                    .withFamily(familyName)
                    .withRequiresCompatibilities(Compatibility.FARGATE)
                    .withExecutionRoleArn(definition.getExecutionRoleArn())
                    .withNetworkMode(NetworkMode.Awsvpc)
                    .withCpu(Integer.toString(cpu))
                    .withMemory(Integer.toString(memory))
                    .withVolumes(definition.getVolumeEntries())
                    .withContainerDefinitions(def);

            final RegisterTaskDefinitionResult result = client.registerTaskDefinition(request);
            String taskDefinitionArn = result.getTaskDefinition().getTaskDefinitionArn();
            LOGGER.log(Level.FINE, "Created Task Definition {0}: {1}", new Object[]{taskDefinitionArn, request});
            LOGGER.log(Level.INFO, "Created Task Definition: {0}", new Object[] { taskDefinitionArn });
            return taskDefinitionArn;
        }
    }

    private String fullQualifiedTemplateName(final String cluster, final ECSFargateTaskDefinition definition) {
        return cluster.replaceAll("\\s+","") + '-' + definition.getName();
    }

    private NetworkConfiguration getNetworkConfig(ECSFargateTaskDefinition template){
        return new NetworkConfiguration().withAwsvpcConfiguration(
                    new AwsVpcConfiguration().withSubnets(template.getSubnets().split(","))
                                             .withAssignPublicIp(AssignPublicIp.ENABLED)
                                             .withSecurityGroups(template.getSecurityGroups().split(",")));
    }

    String runEcsTask(final ECSFargateSlave slave, final ECSFargateTaskDefinition template, String clusterArn, String clusterName, Collection<String> command, String taskDefinitionArn, String taskName) throws IOException, AbortException {
        AmazonECSClient client = getAmazonECSClient();

        KeyValuePair envNodeName = new KeyValuePair();
        envNodeName.setName("SLAVE_NODE_NAME");
        envNodeName.setValue(slave.getComputer().getName());

        KeyValuePair envNodeSecret = new KeyValuePair();
        envNodeSecret.setName("SLAVE_NODE_SECRET");
        envNodeSecret.setValue(slave.getComputer().getJnlpMac());

        KeyValuePair jobName = new KeyValuePair();
        jobName.setName("TASK_NAME");
        jobName.setValue(taskName);

        final RunTaskResult runTaskResult = client.runTask(new RunTaskRequest()
                .withTaskDefinition(taskDefinitionArn)
                .withLaunchType(LaunchType.FARGATE)
                .withNetworkConfiguration(getNetworkConfig(template))
                .withOverrides(new TaskOverride()
                        .withExecutionRoleArn(template.getExecutionRoleArn())
                        .withTaskRoleArn(template.getTaskRoleArn())
                        .withContainerOverrides(new ContainerOverride()
                               // .withName(slave.getNodeName())
                                .withName(fullQualifiedTemplateName(clusterName, template))
                                .withCommand(command)
                                .withEnvironment(envNodeName)
                                .withEnvironment(envNodeSecret)
                                .withEnvironment(jobName)))
                .withCluster(clusterArn)
        );

        if (!runTaskResult.getFailures().isEmpty()) {
            LOGGER.log(Level.WARNING, "Slave {0} - Failure to run task with definition {1} on ECS cluster {2}", new Object[]{slave.getNodeName(), taskDefinitionArn, clusterArn});
            for (Failure failure : runTaskResult.getFailures()) {
                LOGGER.log(Level.WARNING, "Slave {0} - Failure reason={1}, arn={2}", new Object[]{slave.getNodeName(), failure.getReason(), failure.getArn()});
            }
            throw new AbortException("Failed to run slave container " + slave.getNodeName());
        }
        return runTaskResult.getTasks().get(0).getTaskArn();
    }

}
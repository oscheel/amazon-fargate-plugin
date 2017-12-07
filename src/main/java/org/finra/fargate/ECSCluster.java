package org.finra.fargate;

import com.amazonaws.AmazonClientException;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.util.StringUtils;
import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsHelper;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.List;

public class ECSCluster extends AbstractDescribableImpl<ECSCluster> {

    private static final Logger LOGGER = Logger.getLogger(ECSCluster.class.getName());
    private final String name;
    private final String credentialId;
    private final String clusterArn;
    private final String region;
    private final String tunnel;
    private int maxSlaves;
    private final List<ECSFargateTaskDefinition> taskDefinitionList;
    ECSService ecsService;

    @DataBoundConstructor
    public ECSCluster(String name,String credentialId, String clusterArn, String region, String tunnel, List<ECSFargateTaskDefinition> taskDefinitionList) {
        this.name = name;
        this.credentialId = credentialId;
        this.clusterArn = clusterArn;
        this.region = region;
        this.tunnel = tunnel;
        this.taskDefinitionList = taskDefinitionList;

        ecsService = new ECSService(credentialId,region);
        if(taskDefinitionList != null){
            for(ECSFargateTaskDefinition ecsFargateTaskDefinition : taskDefinitionList){
                ecsService.registerTemplate(this,ecsFargateTaskDefinition);
            }
        }
    }

    public int getMaxSlaves() {
        return maxSlaves;
    }

    @DataBoundSetter
    public void setMaxSlaves(String maxSlaves) {
        this.maxSlaves = Integer.parseInt(maxSlaves);
    }

    public String getName() {
        return name;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public String getClusterArn() {
        return clusterArn;
    }

    public String getRegion() {
        return region;
    }

    public String getTunnel() {
        return tunnel;
    }

    public List<ECSFargateTaskDefinition> getTaskDefinitionList() {
        return taskDefinitionList;
    }

    public ECSFargateTaskDefinition getTemplateWithName(String name){
        if(taskDefinitionList != null){
            for(ECSFargateTaskDefinition ecsFargateTaskDefinition: taskDefinitionList){
                if(ecsFargateTaskDefinition.getName().equals(name)){
                    return ecsFargateTaskDefinition;
                }
            }
        }


        return null;
    }

    private Collection<String> getDockerRunCommand(ECSFargateSlave slave) {
        Collection<String> command = new ArrayList<String>();
        command.add("-url");
        command.add(Jenkins.getActiveInstance().getRootUrl());
        if (!StringUtils.isNullOrEmpty(tunnel)) {
            command.add("-tunnel");
            command.add(tunnel);
        }
        command.add(slave.getComputer().getJnlpMac());
        command.add(slave.getComputer().getName());
        return command;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ECSCluster>{

        public static String clusterPattern = "[a-z|A-Z|0-9|_|-]{3,50}";

        @Override
        public String getDisplayName() {
            return "ECS Cluster";
        }
        public ListBoxModel doFillCredentialIdItems() {
            return AWSCredentialsHelper.doFillCredentialsIdItems(Jenkins.getActiveInstance());
        }

        public ListBoxModel doFillRegionItems() {
            final ListBoxModel options = new ListBoxModel();
            for (Region region : RegionUtils.getRegions()) {
                options.add(region.getName());
            }
            return options;
        }

        public ListBoxModel doFillCredentialsIdItems() {
            return AWSCredentialsHelper.doFillCredentialsIdItems(Jenkins.getActiveInstance());
        }

        public ListBoxModel doFillClusterArnItems(@QueryParameter String credentialId, @QueryParameter String region) {
            ECSService ecsService = new ECSService(credentialId, region);
            try {
                final AmazonECSClient client = ecsService.getAmazonECSClient();
                final ListBoxModel options = new ListBoxModel();
                for (String arn : client.listClusters().getClusterArns()) {
                    options.add(arn);
                }
                return options;
            } catch (AmazonClientException e) {
                // missing credentials will throw an "AmazonClientException: Unable to load AWS credentials from any provider in the chain"
                LOGGER.log(Level.INFO, "Exception searching clusters for credentials=" + credentialId + ", regionName=" + region + ":" + e);
                LOGGER.log(Level.FINE, "Exception searching clusters for credentials=" + credentialId + ", regionName=" + region, e);
                return new ListBoxModel();
            } catch (RuntimeException e) {
                LOGGER.log(Level.INFO, "Exception searching clusters for credentials=" + credentialId + ", regionName=" + region, e);
                return new ListBoxModel();
            }
        }

        public FormValidation doCheckName(@QueryParameter String value) throws IOException, ServletException {
            if (value != null && value.matches(clusterPattern)) {
                return FormValidation.ok();
            }
            return FormValidation.error("Up to 50 letters (uppercase and lowercase), numbers, hyphens, and underscores are allowed");
        }


    }
}

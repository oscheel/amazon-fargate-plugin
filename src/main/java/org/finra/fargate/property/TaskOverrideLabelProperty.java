package org.finra.fargate.property;

import hudson.Extension;
import hudson.model.*;
import hudson.model.labels.LabelAtomProperty;
import hudson.model.labels.LabelAtomPropertyDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.finra.fargate.ECSCluster;
import org.finra.fargate.ECSFargateConfig;
import org.finra.fargate.ECSFargateTaskDefinition;
import org.finra.fargate.SecurityGroup;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.util.List;

public class TaskOverrideLabelProperty extends JobProperty  {

    private final String taskDefinitionName;
    private final String role;
    private final int memory;
    private final int cpu;
    private final List<SecurityGroup> securityGroups;
    private boolean on;

    @DataBoundConstructor
    public TaskOverrideLabelProperty(String taskDefinitionName, String role, int memory, int cpu, List<SecurityGroup> securityGroups) {
        this.taskDefinitionName = taskDefinitionName;
        this.role = role;
        this.memory = memory;
        this.cpu = cpu;
        this.securityGroups = securityGroups;
    }


    public String getTaskDefinitionName() {
        return taskDefinitionName;
    }

    public String getRole() {
        return role;
    }

    public int getMemory() {
        return memory;
    }

    public int getCpu() {
        return cpu;
    }


    public List<SecurityGroup> getSecurityGroups() {
        return securityGroups;
    }

    public boolean isOn() {
        return on;
    }

    @DataBoundSetter
    public void setOn(boolean on) {
        this.on = on;
    }

    @Extension
    public static class DescriptorImpl extends JobPropertyDescriptor{

        @Override
        public String getDisplayName() {
            return "ECS Fargate Task";
        }

        public AutoCompletionCandidates doAutoCompleteTaskDefinitionName(@QueryParameter String value){
            ECSFargateConfig ecsFargateConfig = ECSFargateConfig.getEcsFargateConfig();
            AutoCompletionCandidates autoCompletionCandidates = new AutoCompletionCandidates();

            if(value != null && !value.isEmpty() && value.length() > 2){
                for(ECSFargateTaskDefinition taskDefinition: ecsFargateConfig.getAllTemplates()){
                    if(taskDefinition.getName().startsWith(value)){
                        autoCompletionCandidates.add(taskDefinition.getName());
                    }
                }
            }


            return autoCompletionCandidates;
        }

        public FormValidation doCheckTaskDefinitionName(@QueryParameter String taskDefinitionName){

            if(taskDefinitionName == null || taskDefinitionName.isEmpty()){
                return FormValidation.error("Please enter a template name.");
            }

            ECSFargateConfig ecsFargateConfig = ECSFargateConfig.getEcsFargateConfig();
            for(ECSCluster cluster : ecsFargateConfig.getClusters()){
                ECSFargateTaskDefinition taskDef = cluster.getTemplateWithName(taskDefinitionName);
                if(taskDef != null){
                    return FormValidation.ok("Name %s matches template in cluster %s",taskDefinitionName,cluster.getName());
                }

            }

            return FormValidation.error("Name %s matches no template definition.",taskDefinitionName);
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return AbstractProject.class.isAssignableFrom(jobType);
        }

    }

    public static class SecurityGroup extends AbstractDescribableImpl<org.finra.fargate.SecurityGroup> {


        private final String securityGroupId;

        public String getSecurityGroupId() {
            return securityGroupId;
        }

        @DataBoundConstructor
        public SecurityGroup(String securityGroupId) {
            this.securityGroupId = securityGroupId;
        }


        @Extension
        public static class DescriptorImpl extends Descriptor<org.finra.fargate.SecurityGroup>{

            @Override
            public String getDisplayName() {
                return "Security Group";
            }
        }
    }
}

package org.jenkinsci.fargate.property;

import hudson.Extension;
import hudson.model.*;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.fargate.ECSCluster;
import org.jenkinsci.fargate.ECSFargateConfig;
import org.jenkinsci.fargate.ECSFargateTaskDefinition;
import org.jenkinsci.fargate.ECSFargateTaskOverrideAction;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.util.List;

public class TaskOverrideProperty extends JobProperty  {

    private final String taskRoleArn;
    private final String role;
    private final String memory;
    private final String cpu;
    private final String securityGroups;
    private boolean on;

    @DataBoundConstructor
    public TaskOverrideProperty(String taskRoleArn, String role, String memory, String cpu, String securityGroups) {
        this.taskRoleArn = taskRoleArn;
        this.role = role;
        this.memory = memory;
        this.cpu = cpu;
        this.securityGroups = securityGroups;
    }


    public String getTaskRoleArn() {
        return taskRoleArn;
    }

    public String getRole() {
        return role;
    }

    public String getMemory() {
        return memory;
    }

    public String getCpu() {
        return cpu;
    }


    public String getSecurityGroups() {
        return securityGroups;
    }

    public boolean isOn() {
        return on;
    }

    public ECSFargateTaskOverrideAction getOverrideAction(){
        return new ECSFargateTaskOverrideAction(getTaskRoleArn(),getMemory(),getCpu(),getSecurityGroups());
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



        public FormValidation doCheckSecurityGroups(@QueryParameter String securityGroups){
            return ECSFargateTaskDefinition.getDescriptr().doCheckSecurityGroups(securityGroups);
        }


        public ListBoxModel doFillMemoryItems(){
            return  ECSFargateTaskDefinition.getDescriptr().doFillMemoryItems();
        }

        public ListBoxModel doFillCpuItems(@QueryParameter String memory){
            return ECSFargateTaskDefinition.getDescriptr().doFillCpuItems(memory);
        }


        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return AbstractProject.class.isAssignableFrom(jobType);
        }

    }
}

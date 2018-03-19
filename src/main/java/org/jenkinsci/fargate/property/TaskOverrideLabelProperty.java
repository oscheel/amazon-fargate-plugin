package org.jenkinsci.fargate.property;

import hudson.Extension;
import hudson.model.*;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.jenkinsci.fargate.ECSCluster;
import org.jenkinsci.fargate.ECSFargateConfig;
import org.jenkinsci.fargate.ECSFargateTaskDefinition;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.util.List;

public class TaskOverrideLabelProperty extends JobProperty  {

    private final String taskRoleArn;
    private final String role;
    private final int memory;
    private final int cpu;
    private final String securityGroups;
    private boolean on;

    @DataBoundConstructor
    public TaskOverrideLabelProperty(String taskRoleArn, String role, int memory, int cpu,String securityGroups) {
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

    public int getMemory() {
        return memory;
    }

    public int getCpu() {
        return cpu;
    }


    public String getSecurityGroups() {
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

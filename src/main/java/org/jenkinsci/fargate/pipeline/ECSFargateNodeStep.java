package org.jenkinsci.fargate.pipeline;

import com.google.common.collect.ImmutableSet;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.Set;

public final class ECSFargateNodeStep extends Step implements Serializable {


    private final @CheckForNull String label;
    private String roleOverride;
    private double cpu;
    private int memory;
    private String securityGroups;

    @DataBoundConstructor
    public ECSFargateNodeStep(String label){
        this.label = label;
    }



    public String getRoleOverride() {
        return roleOverride;
    }

    @DataBoundSetter
    public void setRoleOverride(String roleOverride) {
        this.roleOverride = roleOverride;
    }

    public double getCpu() {
        return cpu;
    }

    @DataBoundSetter
    public void setCpu(double cpu) {
        this.cpu = cpu;
    }

    public int getMemory() {
        return memory;
    }

    @DataBoundSetter
    public void setMemory(int memory) {
        this.memory = memory;
    }

    public String getSecurityGroups() {
        return securityGroups;
    }

    @DataBoundSetter
    public void setSecurityGroups(String securityGroups) {
        this.securityGroups = securityGroups;
    }

    @CheckForNull
    public String getLabel() {
        return label;
    }

    @Override
    public StepExecution start(StepContext stepContext) throws Exception {
        return new ECSFargateNodeStepExecution(stepContext,this);
    }


    @Extension
    public static final class DescriptorImpl extends StepDescriptor{



        @Override public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(TaskListener.class, Run.class, FlowExecution.class, FlowNode.class);
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

        @SuppressWarnings("unchecked")
        @Override public Set<? extends Class<?>> getProvidedContext() {
            return ImmutableSet.of(Executor.class, Computer.class, FilePath.class, EnvVars.class,
                    // TODO ExecutorStepExecution.PlaceholderExecutable.run does not pass these, but DefaultStepContext infers them from Computer:
                    Node.class, Launcher.class);
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Allocates a fargate ECS node to run this task.";
        }

        @Override
        public String getFunctionName() {
            return "fargateNode";
        }
    }
}
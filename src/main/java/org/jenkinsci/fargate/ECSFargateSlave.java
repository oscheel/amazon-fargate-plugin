package org.jenkinsci.fargate;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.*;
import hudson.remoting.Channel;
import hudson.slaves.*;
import org.jenkinsci.plugins.oneshot.DeadComputer;
import org.jenkinsci.plugins.oneshot.OneShotComputer;
import org.jenkinsci.plugins.oneshot.OneShotSlave;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ECSFargateSlave extends OneShotSlave{

    private String taskArn;
    private String templateLabel;
    private final String region;
    private final String clusterArn;
    private final String credentialId;
    private final String taskName;
    private final ECSFargateTaskOverrideAction overrideAction;
    private static Logger LOG = Logger.getLogger(ECSFargateSlave.class.getName());


    public ECSFargateSlave(Queue.BuildableItem queueItem, String nodeDescription, String remoteFS, ECSCluster ecsCluster, ECSFargateTaskOverrideAction ecsFargateTaskOverrideAction) throws Exception{
        super(queueItem,nodeDescription,remoteFS,new ECSFargateLauncher(new JNLPLauncher()),Charset.defaultCharset());
        this.templateLabel = queueItem.getAssignedLabel().toString();
        this.region = ecsCluster.getRegion();
        this.clusterArn = ecsCluster.getClusterArn();
        this.credentialId = ecsCluster.getCredentialId();
        this.taskName = queueItem.task.getFullDisplayName();
        this.overrideAction = ecsFargateTaskOverrideAction == null ? new ECSFargateTaskOverrideAction("","","",""): ecsFargateTaskOverrideAction;
    }

    public ECSFargateTaskOverrideAction getOverrideAction() {
        return overrideAction;
    }

    ECSService getECSService(){
        return new ECSService(credentialId,region);
    }

    public String getClusterArn() {
        return clusterArn;
    }

    public String getTaskName() {
        return taskName;
    }

    public String getTemplateLabel() {
        return templateLabel;
    }

    public String getTaskArn() {
        return taskArn;
    }

    public void setTaskArn(String taskArn) {
        this.taskArn = taskArn;
    }


    @Override
    public Launcher createLauncher(TaskListener listener) {
        setExecutable();
        SlaveComputer c = getComputer();

        Executor exec = Executor.currentExecutor();
        Computer ownerComputer = null;
        if(exec != null){
            ownerComputer = exec.getOwner();
        }

        if (c == null) {
            listener.error("Issue with creating launcher for agent " + name + ". Computer has been disconnected");
            return new DummyLauncher(listener);
            //This is a pipeline task that failed to launch and it will be  handled differently.
        }else if(c instanceof  DeadComputer && ownerComputer != null && ((ECSFargateComputer)ownerComputer).getParentRun() != null){
            return null;
        } else {
            // TODO: ideally all the logic below should be inside the SlaveComputer class with proper locking to prevent race conditions,
            // but so far there is no locks for setNode() hence it requires serious refactoring

            // Ensure that the Computer instance still points to this node
            // Otherwise we may end up running the command on a wrong (reconnected) Node instance.
            Slave node = c.getNode();
            if (node != this) {
                String message = "Issue with creating launcher for agent " + name + ". Computer has been reconnected";
                if (LOG.isLoggable(Level.WARNING)) {
                    LOG.log(Level.WARNING, message, new IllegalStateException("Computer has been reconnected, this Node instance cannot be used anymore"));
                }
                return new DummyLauncher(listener);
            }

            // RemoteLauncher requires an active Channel instance to operate correctly
            final Channel channel = c.getChannel();
            if (channel == null) {
                LOG.log(Level.WARNING, "The agent has not been fully initialized yet: {0}",
                        "No remoting channel to the agent OR it has not been fully initialized yet");
                return new DummyLauncher(listener);
            }
            if (channel.isClosingOrClosed()) {
                LOG.log(Level.WARNING, "The agent is being disconnected: {0}",
                        "Remoting channel is either in the process of closing down or has closed down");
                return new DummyLauncher(listener);
            }
            final Boolean isUnix = c.isUnix();
            if (isUnix == null) {
                // isUnix is always set when the channel is not null, so it should never happen
                LOG.log(Level.WARNING, "The agent has not been fully initialized yet: {0}",
                        "It is an invalid channel state, please report a bug to Jenkins if you see it.");
                return new DummyLauncher(listener);
            }

            return new Launcher.RemoteLauncher(listener, channel, isUnix).decorateFor(this);
        }
    }

    @Override
    public int getNumExecutors() {
        return 1;
    }

    @Override
    public ECSFargateComputer createComputer() {

        return new ECSFargateComputer(this);
    }


    @Extension
    public static class DescriptorImpl extends SlaveDescriptor{ }

    public static class DummyLauncher extends Launcher {

        public DummyLauncher(TaskListener listener) {
            super(listener, null);
        }

        @Override
        public Proc launch(ProcStarter starter) throws IOException {
            throw new IOException("Can not call launch on a dummy launcher.");
        }

        @Override
        public Channel launchChannel(String[] cmd, OutputStream out, FilePath workDir, Map<String, String> envVars) throws IOException, InterruptedException {
            throw new IOException("Can not call launchChannel on a dummy launcher.");
        }

        @Override
        public void kill(Map<String, String> modelEnvVars) throws IOException, InterruptedException {
            // Kill method should do nothing.
        }
    }

}

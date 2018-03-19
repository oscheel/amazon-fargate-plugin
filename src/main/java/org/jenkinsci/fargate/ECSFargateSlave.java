package org.jenkinsci.fargate;

import hudson.Extension;
import hudson.model.*;
import hudson.slaves.*;
import org.jenkinsci.plugins.oneshot.DeadComputer;
import org.jenkinsci.plugins.oneshot.OneShotComputer;
import org.jenkinsci.plugins.oneshot.OneShotSlave;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ECSFargateSlave extends OneShotSlave{

    private String taskArn;
    private String templateLabel;
    private final String region;
    private final String clusterArn;
    private final String credentialId;
    private final String taskName;
    private static Logger LOG = Logger.getLogger(ECSFargateSlave.class.getName());


    public ECSFargateSlave(Queue.BuildableItem queueItem, String nodeDescription, String remoteFS, ECSCluster ecsCluster) throws Exception{
      //  super(queueItem,nodeDescription,remoteFS,new ECSFargateLauncher(new CommandLauncher("java -jar \"C:\\Users\\Otto Scheel\\Downloads\\slave.jar\"")),Charset.defaultCharset());
        super(queueItem,nodeDescription,remoteFS,new ECSFargateLauncher(new JNLPLauncher()),Charset.defaultCharset());
        this.templateLabel = queueItem.getAssignedLabel().toString();
        this.region = ecsCluster.getRegion();
        this.clusterArn = ecsCluster.getClusterArn();
        this.credentialId = ecsCluster.getCredentialId();
        this.taskName = queueItem.task.getFullDisplayName();
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
    public int getNumExecutors() {
        return 1;
    }

    @Override
    public ECSFargateComputer createComputer() {
        return new ECSFargateComputer(this);
    }


    @Extension
    public static class DescriptorImpl extends SlaveDescriptor{ }

}

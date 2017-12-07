package org.finra.fargate;

import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.slaves.*;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class ECSFargateSlave extends Slave implements EphemeralNode {

    private String taskArn;
    private String taskId;

    public ECSFargateSlave(String name, String remoteFS, String labelString) throws Descriptor.FormException, IOException {
        super(name, "", remoteFS, 1, Mode.EXCLUSIVE, labelString, new JNLPLauncher(), RetentionStrategy.NOOP, Collections.EMPTY_LIST);
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
    public SlaveComputer getComputer() {
        return (ECSFargateComputer) super.getComputer();
    }


    @Override
    public Computer createComputer() {
        return new ECSFargateComputer(this);
    }

    @Override
    public Node asNode() {
        return this;
    }
}

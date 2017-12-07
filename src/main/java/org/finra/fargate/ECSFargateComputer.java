package org.finra.fargate;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.Charset;

public class ECSFargateComputer extends SlaveComputer {

    private final ECSFargateSlave ecsFargateSlave;

    public ECSFargateComputer(ECSFargateSlave slave) {
        super(slave);
        this.ecsFargateSlave = slave;
    }


/*
    @Override
    public boolean isOffline() {
        final ECSFargateSlave node = getNode();
        if (node != null) {
            if (node.hasProvisioningFailed()) return true;
            if (!node.hasExecutable()) return false;
        }

        return isActuallyOffline();
    }
    public boolean isActuallyOffline() {
        return super.isOffline();
    }*/



    @Override
    public @Nonnull ECSFargateSlave getNode() {
        return ecsFargateSlave;
    }

    @Override
    protected boolean isAlive() {
        //Only terminate when task has completed and computer is no longer accepting tasks.
        if (getNode().getComputer().isIdle() && !getNode().getComputer().isAcceptingTasks()) {
            // #isAlive is used from removeExecutor to determine if executors should be created to replace a terminated one
            // We hook into this lifecycle implementation detail (sic) to get notified as the build completed
          //  terminate();
        }
        return super.isAlive();
    }

    protected void terminate() {
        try {
            //Terminate Task definition
            Jenkins.getActiveInstance().removeNode(ecsFargateSlave);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /**
     * We only do support Linux docker images, so we assume UTF-8.
     * This let us wait for build log to be created and setup as a
     * ${@link hudson.model.BuildListener} before we actually launch
     */
    @Override
    public Charset getDefaultCharset() {
        return Charset.forName("UTF-8");
    }

    // --- we need this to workaround hudson.slaves.SlaveComputer#taskListener being private
    private TaskListener listener;

    @Extension
    public final static ComputerListener COMPUTER_LISTENER = new ComputerListener() {
        @Override
        public void preLaunch(Computer c, TaskListener listener) throws IOException, InterruptedException {
            if (c instanceof ECSFargateComputer) {
                ((ECSFargateComputer) c).setListener(listener);
            }
        }
    };

    public void setListener(TaskListener listener) {
        this.listener = listener;
    }

    public TaskListener getListener() {
        return listener;
    }
}

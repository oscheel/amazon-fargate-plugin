package org.jenkinsci.fargate;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jenkinsci.plugins.oneshot.OneShotComputer;
import org.kohsuke.stapler.HttpResponse;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ECSFargateComputer extends OneShotComputer<ECSFargateSlave> {

    private Logger LOG = Logger.getLogger(ECSFargateComputer.class.getName());
    private final ECSFargateSlave ecsFargateSlave;
    private boolean isDead = false;
    /**
     * Identifies the run for which this computer was launched.
     */
    private String parentRun;
    /**
     * Identifies the cookie for the running pipeline process.
     */
    private String cookie;

    public ECSFargateComputer(ECSFargateSlave slave) {
        super(slave);
        this.ecsFargateSlave = slave;
    }


    public ECSFargateSlave getEcsFargateSlave() {
        return ecsFargateSlave;
    }


    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        LOG.log(Level.WARNING,"{0} {1} {3}",new Object[]{executor,task,problems});
        super.taskCompletedWithProblems(executor, task, durationMS, problems);
    }

    @Override
    public Charset getDefaultCharset() {
        return super.getDefaultCharset() == null ? Charset.defaultCharset() : super.getDefaultCharset();
    }

    public String getParentRun() {
        return parentRun;
    }

    public void setParentRun(String parentRun) {
        this.parentRun = parentRun;
    }

    public String getCookie() {
        return cookie;
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    public void setIsDead(boolean isDead){
        this.isDead = isDead;
    }

    public boolean isDead(){
        return isDead;
    }


    @Override
    protected void terminate(TaskListener listener) throws Exception {
        super.terminate(listener);
        if(!StringUtils.isEmpty(ecsFargateSlave.getTaskArn())){
            getEcsFargateSlave().getECSService().deleteTask(ecsFargateSlave.getTaskArn(),getEcsFargateSlave().getClusterArn());
        }else{
            LOG.log(Level.WARNING,"Slave was terminated before task was assigned {0}",ecsFargateSlave.getNodeName());
        }
    }

}

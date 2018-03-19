package org.jenkinsci.fargate;

import hudson.Extension;
import hudson.model.Computer;
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

    public ECSFargateComputer(ECSFargateSlave slave) {
        super(slave);
        this.ecsFargateSlave = slave;
    }

    public ECSFargateSlave getEcsFargateSlave() {
        return ecsFargateSlave;
    }

    @Override
    public Charset getDefaultCharset() {
        return super.getDefaultCharset() == null ? Charset.defaultCharset() : super.getDefaultCharset();
    }


    @Override
    protected void terminate(TaskListener listener) throws Exception {
        if(!StringUtils.isEmpty(ecsFargateSlave.getTaskArn())){
            getEcsFargateSlave().getECSService().deleteTask(getEcsFargateSlave().getClusterArn(),ecsFargateSlave.getTaskArn());
        }else{
            LOG.log(Level.WARNING,"Slave was terminated before task was assigned {0}",ecsFargateSlave.getNodeName());
        }
    }

}

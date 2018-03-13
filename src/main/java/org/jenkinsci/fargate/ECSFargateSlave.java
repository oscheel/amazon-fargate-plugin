package org.jenkinsci.fargate;

import hudson.model.*;
import hudson.slaves.*;
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
    private static Logger LOG = Logger.getLogger(ECSFargateSlave.class.getName());


    public ECSFargateSlave(Queue.BuildableItem queueItem, String nodeDescription, String remoteFS) throws Exception{
      //  super(queueItem,nodeDescription,remoteFS,new ECSFargateLauncher(new CommandLauncher("java -jar \"C:\\Users\\Otto Scheel\\Downloads\\slave.jar\"")),Charset.defaultCharset());
        super(queueItem,nodeDescription,remoteFS,new ECSFargateLauncher(new JNLPLauncher()),Charset.defaultCharset());
        this.templateLabel = queueItem.getAssignedLabel().toString();
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



    public static class DescriptorImpl extends SlaveDescriptor{ }

}

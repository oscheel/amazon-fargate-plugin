package org.jenkinsci.fargate;

import com.amazonaws.AmazonServiceException;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jenkinsci.plugins.oneshot.DeadComputer;
import org.jenkinsci.plugins.oneshot.OneShotComputer;
import org.jenkinsci.plugins.oneshot.OneShotComputerLauncher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ECSFargateLauncher extends DelegatingComputerLauncher {

    private static final Logger LOGGER = Logger.getLogger(ECSFargateLauncher.class.getName());

    protected ECSFargateLauncher(ComputerLauncher launcher){
        super(launcher);

    }

    protected ECSFargateLauncher() {
        this(new JNLPLauncher());
    }


    private Collection<String> getDockerRunCommand(ECSFargateSlave slave, ECSCluster cluster) {
        Collection<String> command = new ArrayList<String>();
        command.add("-url");
        command.add(Jenkins.getInstance().getRootUrl());
        if (StringUtils.isNotBlank(cluster.getTunnel())) {
            command.add("-tunnel");
            command.add(cluster.getTunnel());
        }
        command.add(slave.getComputer().getJnlpMac());
        command.add(slave.getComputer().getName());
        return command;
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {


        if(computer.getNode() instanceof ECSFargateSlave){

            LOGGER.log(Level.INFO,"Provisioning Fargate task for this run...");
            ECSFargateConfig ecsFargateConfig = ECSFargateConfig.getEcsFargateConfig();
            ECSFargateSlave ecsFargateSlave = (ECSFargateSlave)computer.getNode();

            Pair<ECSCluster,ECSFargateTaskDefinition> clusterToDefPair = ecsFargateConfig.getTemplate(ecsFargateSlave.getTemplateLabel());

            if(clusterToDefPair == null){
                throw new RuntimeException("Unable to find template to launch this slave.");
            }

            ECSCluster ecsCluster = clusterToDefPair.getKey();
            ECSFargateTaskDefinition taskDefinition = clusterToDefPair.getValue();

            super.launch(computer, listener);

            int retries = 0;
            CONNECT_LOOP: {
                while(retries < ecsCluster.getMaxRetries()){

                    LOGGER.log(Level.INFO,"Launching ECS task for item {0} and template {1}.", new Object[]{ecsFargateSlave.getTaskName(),ecsFargateSlave.getTemplateLabel()});
                    listener.getLogger().println(getDockerRunCommand(ecsFargateSlave,ecsCluster).toString());
                    ECSService ecsService = new ECSService(ecsCluster.getCredentialId(), ecsCluster.getRegion());

                    String taskDefArn = ecsService.registerTemplate(ecsCluster,taskDefinition);
                    try{
                        String taskArn = ecsService.runEcsTask(ecsFargateSlave,taskDefinition,ecsCluster.getClusterArn(),ecsCluster.getName(),getDockerRunCommand(ecsFargateSlave,ecsCluster),taskDefArn,ecsFargateSlave.getTaskName());
                        ecsFargateSlave.setTaskArn(taskArn);
                        Date timeout = new Date(new Date().getTime()+1000*ecsCluster.getSlaveTimeout());

                        while(timeout.after(new Date())){
                            if (ecsFargateSlave.getComputer() == null || ecsFargateSlave.getComputer() instanceof DeadComputer) {
                                throw new IllegalStateException(
                                        "Slave " + ecsFargateSlave.getNodeName() + " - Node was deleted, computer is null");
                            }
                            if (!ecsFargateSlave.getComputer().isActuallyOffline()) {
                                break CONNECT_LOOP;
                            }

                            LOGGER.log(Level.FINE,"Waiting for slave to connect... ");
                            Thread.sleep(1000);
                        }


                    }catch (AmazonServiceException e){
                        listener.getLogger().println("A problem occurred while submitting the request for this task "+e.getMessage());
                        LOGGER.log(Level.WARNING,"A problem occured while submitting a fargate request {0}.",e.getMessage());
                        LOGGER.log(Level.WARNING, ExceptionUtils.getFullStackTrace(e));
                    }

                    retries++;
                    Thread.sleep(1000);
                }
            }

        }

    }
}

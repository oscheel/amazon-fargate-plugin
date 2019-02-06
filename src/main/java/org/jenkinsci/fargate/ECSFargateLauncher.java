package org.jenkinsci.fargate;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodbv2.xspec.L;
import com.google.common.util.concurrent.ListenableFuture;
import hudson.model.Run;
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
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
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

        listener.getLogger().println("Launching Fargate Agent...");
        if(computer.getNode() instanceof ECSFargateSlave){

            LOGGER.log(Level.INFO, "Provisioning Fargate task for this run...");

            ECSFargateConfig ecsFargateConfig = ECSFargateConfig.getEcsFargateConfig();
            ECSFargateSlave ecsFargateSlave = (ECSFargateSlave) computer.getNode();

            Pair<ECSCluster, ECSFargateTaskDefinition> clusterToDefPair = ecsFargateConfig.getTemplate(ecsFargateSlave.getTemplateLabel());

            if (clusterToDefPair == null) {
                throw new RuntimeException("Unable to find template to launch this slave.");
            }

            ECSCluster ecsCluster = clusterToDefPair.getKey();
            ECSFargateTaskDefinition taskDefinition = clusterToDefPair.getValue();
            int retries = 0;

            try {

                super.launch(computer, listener);


                CONNECT_LOOP:
                {
                    ECSService ecsService = new ECSService(ecsCluster.getCredentialId(), ecsCluster.getRegion());
                    String taskDefArn = ecsService.registerTemplate(ecsCluster, taskDefinition, ecsFargateSlave.getOverrideAction());
                    while (retries < ecsCluster.getMaxRetries()) {

                        String taskArn = null;
                        try {
                            LOGGER.log(Level.INFO, "Launching ECS task for item {0} and template {1}.", new Object[]{ecsFargateSlave.getTaskName(), ecsFargateSlave.getTemplateLabel()});

                          taskArn = ecsService.runEcsTask(ecsFargateSlave,
                                                                taskDefinition,
                                                                ecsCluster.getClusterArn(),
                                                                ecsCluster.getName(),
                                                                getDockerRunCommand(ecsFargateSlave,ecsCluster),
                                                                taskDefArn,
                                                                ecsFargateSlave.getTaskName(),
                                                                ecsFargateSlave.getOverrideAction());
                        ecsFargateSlave.setTaskArn(taskArn);
                            Date timeout = new Date(new Date().getTime() + 1000 * ecsCluster.getSlaveTimeout());

                            while (timeout.after(new Date())) {
                                if (ecsFargateSlave.getComputer() == null || ecsFargateSlave.getComputer() instanceof DeadComputer) {
                                    throw new IllegalStateException(
                                            "Slave " + ecsFargateSlave.getNodeName() + " - Node was deleted, computer is null");
                                }
                                if (!ecsFargateSlave.getComputer().isActuallyOffline() || ((ECSFargateComputer)ecsFargateSlave.getComputer()).isDead()) {
                                    break CONNECT_LOOP;
                                }

                                LOGGER.log(Level.FINE, "Waiting for slave to connect... ");
                                Thread.sleep(1000);
                            }


                        } catch (AmazonServiceException e) {
                            listener.getLogger().println("A problem occurred while submitting the request for this task " + e.getMessage());
                            LOGGER.log(Level.WARNING, "A problem occured while submitting a fargate request {0}.", e.getMessage());
                            LOGGER.log(Level.WARNING, ExceptionUtils.getFullStackTrace(e));

                        }finally {
                            //Make sure we clean this task even if it fails.
                            if(taskArn != null && ecsFargateSlave.getComputer().isActuallyOffline()) {
                                LOGGER.log(Level.INFO,"Slave did not launch in a timely manner, terminating task and incrementing retries.");
                                ecsService.deleteTask(taskArn, ecsCluster.getClusterArn());
                            }
                        }
                        retries++;
                    }

                }
            }finally{

                ECSFargateComputer ecsFargateComputer = (ECSFargateComputer) computer;
                if(ecsFargateComputer.isActuallyOffline()) {
                    try{
                        LOGGER.log(Level.INFO,"Failed to provision agent, removing from jenkins {0}.",computer);
                        ecsFargateComputer.terminate(listener);
                        Jenkins.getInstance().removeNode(ecsFargateSlave);
                    }catch (Exception e){
                        LOGGER.log(Level.WARNING, "Failed to disconnect node after failing to provision {0}.",e.getMessage());
                    }

                }


            }
        }

    }
}

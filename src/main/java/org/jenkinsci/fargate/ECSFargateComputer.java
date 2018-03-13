package org.jenkinsci.fargate;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.tuple.Pair;
import org.jenkinsci.plugins.oneshot.OneShotComputer;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.Charset;

public class ECSFargateComputer extends OneShotComputer<ECSFargateSlave> {

    private final ECSFargateSlave ecsFargateSlave;

    public ECSFargateComputer(ECSFargateSlave slave) {
        super(slave);
        this.ecsFargateSlave = slave;
    }

    public ECSFargateSlave getEcsFargateSlave() {
        return ecsFargateSlave;
    }

    /**
     *
     * Add logic to terminate ECS task here before node is officially removed.
     * @param listener
     * @throws Exception
     */
    @Override
    protected void terminate(TaskListener listener) throws Exception {
        super.terminate(listener);
        ECSFargateConfig ecsFargateConfig = ECSFargateConfig.getEcsFargateConfig();
        Pair<ECSCluster,ECSFargateTaskDefinition> clusterToDefPair = ecsFargateConfig.getTemplate(ecsFargateSlave.getTemplateLabel());

        if(clusterToDefPair != null){
            ECSCluster ecsCluster = clusterToDefPair.getKey();

            ECSService ecsService = new ECSService(ecsCluster.getCredentialId(),ecsCluster.getRegion());
            ecsService.deleteTask(ecsCluster.getClusterArn(),ecsFargateSlave.getTaskArn());
        }
    }
}

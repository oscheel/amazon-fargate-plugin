package org.jenkinsci.fargate;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Queue;
import org.apache.commons.lang3.tuple.Pair;
import org.jenkinsci.fargate.pipeline.ECSFargateNodeStepExecution;
import org.jenkinsci.fargate.property.TaskOverrideProperty;
import org.jenkinsci.plugins.oneshot.OneShotProvisioner;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 *  Starts a request for an ECS slave. Since its task specific, we are able to retrieve configured properties and therefore override the original
 *  task definition to include things like memory, role, security groups, etc...
 */
@Extension
public class ECSFargateProvisioner extends OneShotProvisioner<ECSFargateSlave> {

    private static Logger LOG = Logger.getLogger(ECSFargateProvisioner.class.getName());


    @Override
    public boolean usesOneShotExecutor(Queue.Item item) {

        LOG.log(Level.FINE,"Checking {0} to see if we can provision a fargate agent.", item.task);
        ECSFargateConfig ecsFargateConfig = ECSFargateConfig.getEcsFargateConfig();
        if( item.getAssignedLabel() != null &&
                item.getAssignedLabel().isAtom() &&
                ecsFargateConfig.getTemplate(item.getAssignedLabel().toString()) != null &&
                !(item.task instanceof ExecutorStepExecution.PlaceholderTask)){

            LOG.log(Level.FINE,"This project uses a fargate label {0}."+item.getAssignedLabel());
            return true;
        }

        return false;
    }

    @Override
    public boolean canRun(Queue.Item item) {
        return true;
    }

    @Nonnull
    @Override
    public ECSFargateSlave prepareExecutorFor(Queue.BuildableItem buildableItem) throws Exception {

        ECSFargateConfig ecsFargateConfig = ECSFargateConfig.getEcsFargateConfig();

        Pair<ECSCluster,ECSFargateTaskDefinition> taskDefinition = ecsFargateConfig.getTemplate(buildableItem.getAssignedLabel().toString());
        if(taskDefinition == null) {
            throw new RuntimeException("Unable to find template for "+buildableItem.getAssignedLabel().toString());
        }
        ECSFargateTaskOverrideAction taskOverrideAction = getDefinitionOverrides(buildableItem.task);


        if(taskOverrideAction != null){
            LOG.log(Level.INFO,"Preparing ECS slave for task {0} with overrides {1}", new Object[]{buildableItem.task,taskOverrideAction});
        }else{
            LOG.log(Level.INFO,"Preparing ECS slave for task {0}",buildableItem.task);
        }

        return  new ECSFargateSlave(buildableItem,"ECS Fargate Node.",taskDefinition.getValue().getRemoteFSRoot(),taskDefinition.getKey(),taskOverrideAction);
    }




    private ECSFargateTaskOverrideAction getDefinitionOverrides(Queue.Task task){
        if(task instanceof AbstractProject){
            AbstractProject abstractProject = (AbstractProject)task;
            TaskOverrideProperty taskOverrideProperty = (TaskOverrideProperty)abstractProject.getProperty(TaskOverrideProperty.class);
            if(taskOverrideProperty != null && taskOverrideProperty.isOn()){
                return taskOverrideProperty.getOverrideAction();
            }

        }else if(task instanceof ECSFargateNodeStepExecution.PlaceholderTask){
            ECSFargateNodeStepExecution.PlaceholderTask placeholderTask =  (ECSFargateNodeStepExecution.PlaceholderTask) task;

            try{
                return placeholderTask.getNode().getAction(ECSFargateTaskOverrideAction.class);
            }catch (Exception e){
                LOG.log(Level.WARNING,"Failed to fetch override action for placeholder task {0}.",task);
            }
        }

        return null;
    }
}

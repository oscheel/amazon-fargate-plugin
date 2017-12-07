package org.finra.fargate;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Slave;
import hudson.model.queue.QueueListener;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.security.ACL;
import hudson.remoting.Callable;
import jenkins.model.Jenkins;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.finra.fargate.property.TaskOverrideLabelProperty;
import org.jenkinsci.remoting.RoleChecker;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 *  Starts a request for an ECS slave. Since its task specific, we are able to retrieve configured properties and therefore override the original
 *  task definition to include things like memory, role, security groups, etc...
 */
@Extension
public class ECSFargateProvisioner extends QueueListener {

    private static Logger LOG = Logger.getLogger(ECSFargateProvisioner.class.getName());

    @Override
    public void onEnterBuildable(Queue.BuildableItem bi) {
        if(bi.task instanceof AbstractProject){

            AbstractProject project = (AbstractProject) bi.task;
            LOG.log(Level.INFO,"Assigned Label {0}",project.getAssignedLabel());
            final TaskOverrideLabelProperty labelProperty = (TaskOverrideLabelProperty) project.getProperty(TaskOverrideLabelProperty.class);
            if(labelProperty != null && labelProperty.isOn() && project.getAssignedLabel() == null){
                ECSFargateConfig ecsFargateConfig = ECSFargateConfig.getEcsFargateConfig();
                final ECSFargateTaskDefinition taskDefinition = ecsFargateConfig.getTemplate(labelProperty.getTaskDefinitionName());

                if(taskDefinition != null){

                }else{
                    //Fail build since template was not found.
                }
               // Queue.getInstance().cancel(bi);
                if(taskDefinition != null) {


                    try {
                        Node node = ACL.impersonate(ACL.SYSTEM, new Callable<Node, Exception>() {
                            @Override
                            public void checkRoles(RoleChecker checker) throws SecurityException {

                            }

                            @Override
                            public Node call() throws Exception {
                                Slave node = new ECSFargateSlave(taskDefinition.getName(), taskDefinition.getRemoteFSRoot(), "LabelProperty");
                            //    node.createComputer();
                                Jenkins.getActiveInstance().addNode(node);
                                while (Jenkins.getInstance().getNode(node.getNodeName()) == null) {
                                    Thread.sleep(1000);
                                }
                                LOG.log(Level.INFO, node.getComputer().getJnlpMac());

                                return node;
                            }
                        });

                        LOG.log(Level.INFO, "Launched node {0}", node);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, ExceptionUtils.getFullStackTrace(e));
                    }
                }
            }
            LOG.log(Level.INFO,"Task {0} entered runnable state.",project);
/*            if(bi.getAction(ECSFargateSlaveAssigmentAction.class) == null){
                bi.addAction(new ECSFargateSlaveAssigmentAction("master"));
            }*/

        }
    }
}

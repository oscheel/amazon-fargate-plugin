package org.finra.fargate;

import hudson.model.InvisibleAction;
import hudson.model.Label;
import hudson.model.labels.LabelAssignmentAction;
import hudson.model.queue.SubTask;

/**
 *  This action is added to a task so that it forces it to run on a specific node. Used by {@link ECSFargateProvisioner} to launch a slave and
 *  then map it to the task in question.
 */
public class ECSFargateSlaveAssigmentAction extends InvisibleAction implements LabelAssignmentAction {


    private final String nodeName;

    public ECSFargateSlaveAssigmentAction(String name){
        this.nodeName = name;
    }

    @Override
    public Label getAssignedLabel(SubTask task) {
        return Label.get(nodeName);
    }

}

package org.jenkinsci.fargate;

import hudson.model.InvisibleAction;
import org.apache.commons.lang.StringUtils;

import java.io.Serializable;

/**
 *
 * Task overrides that will be passed down to the launcher.
 *
 */
public class ECSFargateTaskOverrideAction extends InvisibleAction implements Serializable {

    private final String taskRoleArn;
    private final String memory;
    private final String cpu;
    private final String securityGroups;

    public ECSFargateTaskOverrideAction(String taskRoleArn, String memory, String cpu, String securityGroups) {
        this.taskRoleArn = taskRoleArn;
        this.memory = memory;
        this.cpu = cpu;
        this.securityGroups = securityGroups;
    }

    public String getTaskRoleArn() {
        return taskRoleArn;
    }


    public String getMemory() {
        return memory;
    }

    public String getCpu() {
        return cpu;
    }

    public String getSecurityGroups() {
        return securityGroups;
    }


    @Override
    public String toString() {
        return "ECSFargateTaskOverrideAction{" +
                "taskRoleArn='" + taskRoleArn + '\'' +
                ", memory='" + memory + '\'' +
                ", cpu='" + cpu + '\'' +
                ", securityGroups='" + securityGroups + '\'' +
                '}';
    }

    public String generateString() {
        StringBuilder sb = new StringBuilder();
        if(StringUtils.isEmpty(taskRoleArn) && StringUtils.isEmpty(memory) && StringUtils.isEmpty(cpu) && StringUtils.isEmpty(securityGroups)){
            sb.append("Launching agent from default template...");
        }

        if(!StringUtils.isEmpty(taskRoleArn)){
            sb.append("Overriding task role with: "+taskRoleArn+"\n");
        }

        if(!StringUtils.isEmpty(cpu) || !StringUtils.isEmpty(memory)){
            sb.append("Overriding memory settings with cpu: "+cpu+"v and memory: "+memory+"GB\n");
        }

        if(!StringUtils.isEmpty(securityGroups)){
            sb.append("Adding SGs: "+securityGroups+"\n");
        }

        return sb.toString();
    }
}

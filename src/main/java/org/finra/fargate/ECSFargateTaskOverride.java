package org.finra.fargate;

import java.util.List;

public class ECSFargateTaskOverride {

    private final String taskDefinitionName;
    private final String role;
    private final int memory;
    private final int cpu;
    private final List<SecurityGroup> securityGroups;

    public ECSFargateTaskOverride(String taskDefinitionName, String role, int memory, int cpu, List<SecurityGroup> securityGroups) {
        this.taskDefinitionName = taskDefinitionName;
        this.role = role;
        this.memory = memory;
        this.cpu = cpu;
        this.securityGroups = securityGroups;
    }


}

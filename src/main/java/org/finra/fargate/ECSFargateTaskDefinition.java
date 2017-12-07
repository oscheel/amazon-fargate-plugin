package org.finra.fargate;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.ecs.model.*;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.DataBoundConstructor;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ECSFargateTaskDefinition extends AbstractDescribableImpl<ECSFargateTaskDefinition> {

    private static Logger LOG = Logger.getLogger(ECSFargateTaskDefinition.class.getName());
    private String name;
    private String roleArn;
    private String memory;
    private String image;
    private String cpu;
    private String remoteFSRoot;
    private boolean priviledged;
    @CheckForNull
    private String logDriver;
    private List<LogDriverOption> logDriverOptions;
    @CheckForNull
    private String jvmArgs;
    private List<MountPointEntry> mountPoints;
    private boolean privileged;

    private List<EnvironmentEntry> environments;
    private List<ExtraHostEntry> extraHosts;
    private String entryPoint;
    private String vpc;
    private List<Subnet> subnets;
    private String securityGroups;

    @DataBoundConstructor
    public ECSFargateTaskDefinition(String name, String roleArn, String memory,String cpu, String image, String remoteFSRoot, boolean priviledged, String logDriver, List<LogDriverOption> logDriverOptions, String jvmArgs, List<MountPointEntry> mountPoints, boolean privileged, List<EnvironmentEntry> environments, List<ExtraHostEntry> extraHosts, String entryPoint) {
        this.name = name;
        this.roleArn = roleArn;
        this.memory = memory;
        this.image = image;
        this.remoteFSRoot = remoteFSRoot;
        this.priviledged = priviledged;
        this.logDriver = logDriver;
        this.logDriverOptions = logDriverOptions;
        this.jvmArgs = jvmArgs;
        this.mountPoints = mountPoints;
        this.privileged = privileged;
        this.environments = environments;
        this.extraHosts = extraHosts;
        this.entryPoint = entryPoint;
        this.cpu=cpu;
    }

    public String getSecurityGroups() {
        return securityGroups;
    }

    @DataBoundSetter
    public void setSecurityGroups(String securityGroups) {
        this.securityGroups = securityGroups;
    }

    public String getVpc() {
        return vpc;
    }

    @DataBoundSetter
    public void setVpc(String vpc) {
        this.vpc = vpc;
    }


    public List<Subnet> getSubnets() {
        return subnets;
    }

    @DataBoundSetter
    public void setSubnets(List<Subnet> subnets) {
        this.subnets = subnets;
    }



    public String getCpu() {
        return cpu;
    }

    public String getEntryPoint() {
        return entryPoint;
    }

    public String getName() {
        return name;
    }

    public String getRoleArn() {
        return roleArn;
    }

    public String getMemory() {
        return memory;
    }

    public String getImage() {
        return image;
    }

    public String getRemoteFSRoot() {
        return remoteFSRoot;
    }

    public boolean isPriviledged() {
        return priviledged;
    }

    @CheckForNull
    public String getLogDriver() {
        return logDriver;
    }

    @CheckForNull
    public String getJvmArgs() {
        return jvmArgs;
    }

    public boolean isPrivileged() {
        return privileged;
    }


    @Extension
    public static class DescriptorImpl extends Descriptor<ECSFargateTaskDefinition>{

        @Override
        public String getDisplayName() {
            return "Fargate Task Definition";
        }


        public FormValidation doCheckSecurityGroups(@QueryParameter String securityGroups,
                                                    @RelativePath("..")@QueryParameter String credentialId,
                                                    @RelativePath("..")@QueryParameter String region,
                                                    @QueryParameter String vpc){

            //Allow a max of 5 security Groups.
            String securityGroupsPattern = "((sg-)[a-zA-Z0-9]{8}(,){0,1}){1,5}";

            if(StringUtils.isEmpty(credentialId) || StringUtils.isEmpty(region) || StringUtils.isEmpty(vpc) || StringUtils.isEmpty(securityGroups)){
                return FormValidation.error("Please select a credential, a vpc and a region to proceed.");
            }

            EC2Service ec2Service = new EC2Service(credentialId,region);


            String[] securityGroupTokens = securityGroups.split(",");
            List<com.amazonaws.services.ec2.model.SecurityGroup> groupList = ec2Service.describeSecurityGroups(securityGroupTokens,vpc);

            if(groupList == null || groupList.size() < 0){
                return FormValidation.error("Unable to validate security groups, please ensure the groups are valid and belong to the selected vpc.");
            }


            return FormValidation.ok("Security groups are valid and match the expected vpc.");
        }

        public ListBoxModel doFillVpcItems(@RelativePath("..")@QueryParameter String credentialId, @RelativePath("..")@QueryParameter String region){

            EC2Service ec2Service = new EC2Service(credentialId,region);
            try {
                final ListBoxModel options = new ListBoxModel();

                for (Vpc vpc : ec2Service.describeVpcs()) {
                    options.add(vpc.getVpcId());
                }
                return options;
            } catch (AmazonClientException e) {
                // missing credentials will throw an "AmazonClientException: Unable to load AWS credentials from any provider in the chain"
                LOG.log(Level.INFO, "Exception searching clusters for credentials=" + credentialId + ", regionName=" + region + ":" + e);
                LOG.log(Level.FINE, "Exception searching clusters for credentials=" + credentialId + ", regionName=" + region, e);

            } catch (RuntimeException e) {
                LOG.log(Level.INFO, "Exception searching VPCs for credentials=" + credentialId + ", regionName=" + region, e);

            }
            return new ListBoxModel();

        }

        public ListBoxModel doFillMemoryItems(){

            ListBoxModel listBoxModel = new ListBoxModel();

            listBoxModel.add("0.5GB",".5");
            listBoxModel.add("1GB","1");
            listBoxModel.add("2GB","2");
            listBoxModel.add("3GB","3");
            listBoxModel.add("4GB","4");
            listBoxModel.add("5GB","5");
            listBoxModel.add("6GB","6");
            listBoxModel.add("7GB","7");
            listBoxModel.add("8GB","8");

            return listBoxModel;
        }

        public ListBoxModel doFillCpuItems(@QueryParameter String memory){
            double d = Double.parseDouble(memory);
            ListBoxModel cpuChoices = new ListBoxModel();
            if(d <= 2){
                cpuChoices.add(".25");
            }

            if(d >=1 && d <= 3){
                cpuChoices.add(".5");
            }

            if(d >=2 && d <= 8){
                cpuChoices.add("1");
            }

            if(d >= 4 && d<=16){
                cpuChoices.add("2");
            }

            return cpuChoices;
        }

        //Uncomment this for cost estimation.
   /*     public FormValidation doCheckMemory(@QueryParameter String memory) throws Exception{

            if(memory != null && !memory.isEmpty()){
                double mem = Double.parseDouble(memory);
                double cpu = ECSFargateTaskDefinition.getCpuFromMemory(memory);

                double estimatedCostPerHour = mem*(0.0127)+cpu*(0.0506);
               // double estimatedCostPerSecond = mem*(0.00000353)+cpu*(0.00001406);
                DecimalFormat fourDForm = new DecimalFormat("#.#####");

                return FormValidation.ok("Estimated cost per hour is %s for this memory settings.",fourDForm.parse(Double.toString(estimatedCostPerHour)));
            }




            return FormValidation.error("Unable to parse memory.");
        }*/


    }

    public static class SecurityGroup extends AbstractDescribableImpl<SecurityGroup>{
        private final String securityGroupId;

        public String getSecurityGroupId() {
            return securityGroupId;
        }

        @DataBoundConstructor
        public SecurityGroup(String securityGroupId) {
            this.securityGroupId = securityGroupId;
        }


        @Extension
        public static class DescriptorImpl extends Descriptor<SecurityGroup>{

            @Override
            public String getDisplayName() {
                return "Security Group";
            }

  /*          public ListBoxModel doFillSecurityGroupIdItems(@RelativePath("..")@QueryParameter String vpc, @RelativePath("../..") @QueryParameter String credentialId,  @RelativePath("../..") @QueryParameter String region){
                ListBoxModel listBoxModel = new ListBoxModel();
                EC2Service ec2Service = new EC2Service(credentialId,region);
                try {
                    final ListBoxModel options = new ListBoxModel();

                    for (com.amazonaws.services.ec2.model.SecurityGroup securityGroup : ec2Service.describeSecurityGroups(vpc)) {
                        options.add(securityGroup.getGroupName(),securityGroup.getGroupId());
                    }
                    return options;
                } catch (AmazonClientException e) {
                    // missing credentials will throw an "AmazonClientException: Unable to load AWS credentials from any provider in the chain"
                    LOG.log(Level.INFO, "Exception searching clusters for credentials=" + credentialId + ", regionName=" + region + ":" + e);
                    LOG.log(Level.FINE, "Exception searching clusters for credentials=" + credentialId + ", regionName=" + region, e);

                } catch (RuntimeException e) {
                    LOG.log(Level.INFO, "Exception searching VPCs for credentials=" + credentialId + ", regionName=" + region, e);
                }
                return listBoxModel;
            }
*/
        }
    }

    public static class Subnet extends AbstractDescribableImpl<Subnet>{
        private final String name;

        @DataBoundConstructor
        public Subnet(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<Subnet>{

            @Override
            public String getDisplayName() {
                return this.clazz.getName();
            }

            public ListBoxModel doFillNameItems(@RelativePath("..")@QueryParameter String vpc, @RelativePath("../..") @QueryParameter String credentialId,  @RelativePath("../..") @QueryParameter String region){
                ListBoxModel listBoxModel = new ListBoxModel();
                EC2Service ec2Service = new EC2Service(credentialId,region);
                try {
                    final ListBoxModel options = new ListBoxModel();

                    for (com.amazonaws.services.ec2.model.Subnet subnet : ec2Service.describeSubnets(vpc)) {
                        options.add(subnet.getSubnetId());
                    }
                    return options;
                } catch (AmazonClientException e) {
                    // missing credentials will throw an "AmazonClientException: Unable to load AWS credentials from any provider in the chain"
                    LOG.log(Level.INFO, "Exception searching clusters for credentials=" + credentialId + ", regionName=" + region + ":" + e);
                    LOG.log(Level.FINE, "Exception searching clusters for credentials=" + credentialId + ", regionName=" + region, e);

                } catch (RuntimeException e) {
                    LOG.log(Level.INFO, "Exception searching VPCs for credentials=" + credentialId + ", regionName=" + region, e);

                }
                return listBoxModel;

            }

        }
    }

    public static class LogDriverOption extends AbstractDescribableImpl<LogDriverOption> {
        public String name, value;

        @DataBoundConstructor
        public LogDriverOption(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String toString() {
            return "LogDriverOption{" + name + ": " + value + "}";
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<LogDriverOption> {
            @Override
            public String getDisplayName() {
                return "logDriverOption";
            }
        }
    }

    public List<LogDriverOption> getLogDriverOptions() {
        return logDriverOptions;
    }

    Map<String,String> getLogDriverOptionsMap() {
        if (null == logDriverOptions || logDriverOptions.isEmpty()) {
            return null;
        }
        Map<String,String> options = new HashMap<String,String>();
        for (LogDriverOption logDriverOption : logDriverOptions) {
            String name = logDriverOption.name;
            String value = logDriverOption.value;
            if (StringUtils.isEmpty(name) || StringUtils.isEmpty(value)) {
                continue;
            }
            options.put(name, value);
        }
        return options;
    }

    public List<EnvironmentEntry> getEnvironments() {
        return environments;
    }

    public List<ExtraHostEntry> getExtraHosts() {
        return extraHosts;
    }

    Collection<KeyValuePair> getEnvironmentKeyValuePairs() {
        if (null == environments || environments.isEmpty()) {
            return null;
        }
        Collection<KeyValuePair> items = new ArrayList<KeyValuePair>();
        for (EnvironmentEntry environment : environments) {
            String name = environment.name;
            String value = environment.value;
            if (StringUtils.isEmpty(name) || StringUtils.isEmpty(value)) {
                continue;
            }
            items.add(new KeyValuePair().withName(name).withValue(value));
        }
        return items;
    }

    Collection<HostEntry> getExtraHostEntries() {
        if (null == extraHosts || extraHosts.isEmpty()) {
            return null;
        }
        Collection<HostEntry> items = new ArrayList<HostEntry>();
        for (ExtraHostEntry extrahost : extraHosts) {
            String ipAddress = extrahost.ipAddress;
            String hostname = extrahost.hostname;
            if (StringUtils.isEmpty(ipAddress) || StringUtils.isEmpty(hostname)) {
                continue;
            }
            items.add(new HostEntry().withIpAddress(ipAddress).withHostname(hostname));
        }
        return items;
    }

    public List<MountPointEntry> getMountPoints() {
        return mountPoints;
    }

    Collection<Volume> getVolumeEntries() {
        Collection<Volume> vols = new LinkedList<Volume>();
        if (null != mountPoints ) {
            for (MountPointEntry mount : mountPoints) {
                String name = mount.name;
                String sourcePath = mount.sourcePath;
                HostVolumeProperties hostVolume = new HostVolumeProperties();
                if (StringUtils.isEmpty(name))
                    continue;
                if (! StringUtils.isEmpty(sourcePath))
                    hostVolume.setSourcePath(sourcePath);
                vols.add(new Volume().withName(name)
                        .withHost(hostVolume));
            }
        }
        return vols;
    }

    Collection<MountPoint> getMountPointEntries() {
        if (null == mountPoints || mountPoints.isEmpty())
            return null;
        Collection<MountPoint> mounts = new ArrayList<MountPoint>();
        for (MountPointEntry mount : mountPoints) {
            String src = mount.name;
            String path = mount.containerPath;
            Boolean ro = mount.readOnly;
            if (StringUtils.isEmpty(src) || StringUtils.isEmpty(path))
                continue;
            mounts.add(new MountPoint().withSourceVolume(src)
                    .withContainerPath(path)
                    .withReadOnly(ro));
        }
        return mounts;
    }

    public static class EnvironmentEntry extends AbstractDescribableImpl<EnvironmentEntry> {
        public String name, value;

        @DataBoundConstructor
        public EnvironmentEntry(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String toString() {
            return "EnvironmentEntry{" + name + ": " + value + "}";
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<EnvironmentEntry> {
            @Override
            public String getDisplayName() {
                return "EnvironmentEntry";
            }
        }
    }

    public static class ExtraHostEntry extends AbstractDescribableImpl<ExtraHostEntry> {
        public String ipAddress, hostname;

        @DataBoundConstructor
        public ExtraHostEntry(String ipAddress, String hostname) {
            this.ipAddress = ipAddress;
            this.hostname = hostname;
        }

        @Override
        public String toString() {
            return "ExtraHostEntry{" + ipAddress + ": " + hostname + "}";
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<ExtraHostEntry> {
            @Override
            public String getDisplayName() {
                return "ExtraHostEntry";
            }
        }
    }

    public static class MountPointEntry extends AbstractDescribableImpl<MountPointEntry> {
        public String name, sourcePath, containerPath;
        public Boolean readOnly;

        @DataBoundConstructor
        public MountPointEntry(String name,
                               String sourcePath,
                               String containerPath,
                               Boolean readOnly) {
            this.name = name;
            this.sourcePath = sourcePath;
            this.containerPath = containerPath;
            this.readOnly = readOnly;
        }

        @Override
        public String toString() {
            return "MountPointEntry{name:" + name +
                    ", sourcePath:" + sourcePath +
                    ", containerPath:" + containerPath +
                    ", readOnly:" + readOnly + "}";
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<MountPointEntry> {
            @Override
            public String getDisplayName() {
                return "MountPointEntry";
            }
        }
    }
}

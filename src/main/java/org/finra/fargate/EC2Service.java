package org.finra.fargate;

import com.amazonaws.AmazonClientException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsHelper;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;
import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import java.util.Collections;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Encapsulates interactions with Amazon EC2.
 *
 * @author Otto Scheel
 *
 */
class EC2Service {

    private static Logger LOG = Logger.getLogger(ECSFargateTaskDefinition.class.getName());
    private String credentialId;
    private String region;

    public EC2Service(String credentialsId, String regionName) {
        this.credentialId = credentialsId;
        this.region = regionName;
    }

    private AmazonEC2 getAmazonEc2(){
        final AmazonEC2 client;

        ProxyConfiguration proxy = Jenkins.getInstance().proxy;
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.setProtocol(Protocol.HTTPS);
        if(proxy != null) {
            LOG.log(Level.INFO,"Setting proxy");
            clientConfiguration.setProxyHost(proxy.name);
            clientConfiguration.setProxyPort(proxy.port);
            clientConfiguration.setProxyUsername(proxy.getUserName());
            clientConfiguration.setProxyPassword(proxy.getPassword());
        }

        AmazonWebServicesCredentials credentials = getCredentials(credentialId);
        if (credentials == null) {
            // no credentials provided, rely on com.amazonaws.auth.DefaultAWSCredentialsProviderChain
            // to use IAM Role define at the EC2 instance level ...
            client = AmazonEC2ClientBuilder.standard().withClientConfiguration(clientConfiguration).build();

        } else {
            if (LOG.isLoggable(Level.FINE)) {
                String awsAccessKeyId = credentials.getCredentials().getAWSAccessKeyId();
                String obfuscatedAccessKeyId = StringUtils.left(awsAccessKeyId, 4) + StringUtils.repeat("*", awsAccessKeyId.length() - (2 * 4)) + StringUtils.right(awsAccessKeyId, 4);
                LOG.log(Level.FINE, "Connect to Amazon ECS with IAM Access Key {1}", new Object[]{obfuscatedAccessKeyId});
            }
            client = AmazonEC2ClientBuilder.standard().withCredentials(credentials)
                    .withRegion(getRegion(region))
                    .withClientConfiguration(clientConfiguration).build();
        }

        return client;
    }

    List<Vpc> describeVpcs(){
        try {
            final AmazonEC2 client = getAmazonEc2();

            return client.describeVpcs().getVpcs();
        } catch (AmazonClientException e) {
            // missing credentials will throw an "AmazonClientException: Unable to load AWS credentials from any provider in the chain"
            LOG.log(Level.INFO, "Exception searching VPCs for credentials=" + credentialId + ", regionName=" + region + ":" + e);
           } catch (RuntimeException e) {
            LOG.log(Level.INFO, "Exception searching VPCs for credentials=" + credentialId + ", regionName=" + region, e);
        }

        return Collections.EMPTY_LIST;
    }

    List<Subnet> describeSubnets(String vpcId){
        try {
            final AmazonEC2 client = getAmazonEc2();

            return client.describeSubnets(new DescribeSubnetsRequest()
                                                .withFilters(new Filter().withName("vpc-id")
                                                                         .withValues(vpcId))).getSubnets();
        } catch (AmazonClientException e) {
            // missing credentials will throw an "AmazonClientException: Unable to load AWS credentials from any provider in the chain"
            LOG.log(Level.INFO, "Exception searching VPCs for credentials=" + credentialId + ", regionName=" + region + ":" + e);
        } catch (RuntimeException e) {
            LOG.log(Level.INFO, "Exception searching VPCs for credentials=" + credentialId + ", regionName=" + region, e);
        }

        return Collections.EMPTY_LIST;
    }

    List<SecurityGroup> describeSecurityGroups(String[] securityGroups, String vpcId){
        try {
            final AmazonEC2 client = getAmazonEc2();


            return client.describeSecurityGroups(new DescribeSecurityGroupsRequest()
                    .withGroupIds(securityGroups)
                    .withFilters(new Filter().withName("vpc-id")
                            .withValues(vpcId))).getSecurityGroups();
        } catch (AmazonClientException e) {
            // missing credentials will throw an "AmazonClientException: Unable to load AWS credentials from any provider in the chain"
            LOG.log(Level.INFO, "Exception searching VPCs for credentials=" + credentialId + ", regionName=" + region + ":" + e);
        } catch (RuntimeException e) {
            LOG.log(Level.INFO, "Exception searching VPCs for credentials=" + credentialId + ", regionName=" + region, e);
        }

        return Collections.EMPTY_LIST;
    }

    @CheckForNull
    private AmazonWebServicesCredentials getCredentials(@Nullable String credentialId) {
        return AWSCredentialsHelper.getCredentials(credentialId, Jenkins.getActiveInstance());
    }

    String getRegion(String regionName) {
        if (StringUtils.isNotEmpty(regionName)) {
            return regionName;
        } else {
            return Regions.US_EAST_1.name();
        }
    }

}

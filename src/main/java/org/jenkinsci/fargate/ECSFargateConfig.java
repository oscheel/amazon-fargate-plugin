package org.jenkinsci.fargate;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class ECSFargateConfig extends GlobalConfiguration {

    private static final Logger LOGGER = Logger.getLogger(ECSFargateConfig.class.getName());

    private List<ECSCluster> clusters;

    public List<ECSCluster> getClusters() {
        return clusters == null ? Collections.EMPTY_LIST : clusters;
    }

    public ECSFargateConfig(){
        load();
    }


    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {

        LOGGER.log(Level.INFO,"Form data {0}",json);
        req.bindJSON(this,json);
        save();

        return super.configure(req,json);
    }

    @DataBoundSetter
    public void setClusters(List<ECSCluster> clusters) {
        this.clusters = clusters;
    }

    @CheckForNull
    public Pair<ECSCluster,ECSFargateTaskDefinition> getTemplate(String templateName){
        if(clusters != null){
            for(ECSCluster ecsCluster : clusters){
                ECSFargateTaskDefinition ecsFargateTaskDefinition = ecsCluster.getTemplateWithName(templateName);
                if(ecsFargateTaskDefinition != null){
                    return new ImmutablePair<ECSCluster,ECSFargateTaskDefinition>(ecsCluster,ecsFargateTaskDefinition);
                }
            }
        }

        return null;
    }

    public List<ECSFargateTaskDefinition> getAllTemplates(){

        List<ECSFargateTaskDefinition> taskDefinitions = new ArrayList<ECSFargateTaskDefinition>();

        if(clusters != null){
            for(ECSCluster ecsCluster : clusters){
               taskDefinitions.addAll(ecsCluster.getTaskDefinitionList());
            }
        }

        return taskDefinitions;
    }

/*
    @Override
    public void start() throws Exception {
        clusters = new ArrayList<ECSCluster>();
        load();
    }

    @Override
    public void configure(StaplerRequest req, JSONObject formData) throws IOException, ServletException, Descriptor.FormException {
        req.bindJSON(this,formData);
        LOGGER.log(Level.INFO,"Form data {0}",formData);
        super.configure(req, formData);
    }
*/



    public static ECSFargateConfig getEcsFargateConfig(){
        return GlobalConfiguration.all().get(ECSFargateConfig.class);
    }


/*    public static class DescriptorImpl extends GlobalConfiguration<ECSFargateConfig> {

        @Override
        public String getDisplayName() {
            return "ECS Fargate Node Configuration";
        }
    }*/
}

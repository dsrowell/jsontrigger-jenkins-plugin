package org.jenkinsci.plugins.jsontrigger;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.List;
import java.util.logging.Logger;

/** Build trigger specifying criteria to match against incoming webhooks. */
public class JsonTrigger extends Trigger<AbstractProject<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(JsonTrigger.class.getName());

    private String userAgent;
    
    @DataBoundConstructor
    public JsonTrigger() {}

    public String getUserAgent() {
    	return userAgent;
    }
    
    @DataBoundSetter
    public void setUserAgent(final String userAgent) {
    	this.userAgent = userAgent;
    }
    
    /** @return {@code true} if the given webhook matches the criteria configured for this instance. */
    public boolean accepts(AbstractProject<?, ?> job, TriggerWebhook hook) {

    	if (hook.getUserAgent().contains(userAgent)) {
    		return true;
    	}
    	
    	return false;
    }

    @Extension
    public static class DescriptorImpl extends TriggerDescriptor {

        @Override
        public boolean isApplicable(Item item) {
            return item instanceof AbstractProject;
        }

        @Override
        public String getDisplayName() {
            return Messages.TriggerDisplayName();
        }

    }

}

package org.jenkinsci.plugins.jsontrigger;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.UnprotectedRootAction;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static hudson.Util.fixEmptyAndTrim;

/** HTTP endpoint for incoming webhooks. */
@Extension
public class TriggerEndpoint implements UnprotectedRootAction {

    private static final Logger LOGGER = Logger.getLogger(TriggerEndpoint.class.getName());

    @Override
    public String getUrlName() {
        return "webhook";
    }

    @RequirePOST
    public HttpResponse doTrigger(StaplerRequest req) throws IOException, ServletException {
        // Grab webhook payload from request body
        TriggerWebhook hook;
        try {
            LOGGER.info(String.format("Incoming webhook from user agent %s.", req.getHeader("User-agent")));
            hook = new ObjectMapper().readValue(req.getInputStream(), TriggerWebhook.class);
            hook.setUserAgent(req.getHeader("User-agent"));
        } catch (JsonParseException e) {
            LOGGER.warning("Received hook without JSON body. " + e.getMessage());
            return HttpResponses.errorWithoutStack(400, "This endpoint expects a POST request with JSON body.");
        } catch (IOException e) {
            LOGGER.warning("Failed to read webhook payload from request body: "+ e.getMessage());
            return HttpResponses.errorWithoutStack(400, "Failed to read webhook payload from request body.");
        }

        // The Content-Type header should contain info about what type of webhook this is
        String contentType = fixEmptyAndTrim(req.getHeader("Content-Type"));
        if (contentType == null) {
            LOGGER.warning("Received hook without Content-Type header.");
            return HttpResponses.errorWithoutStack(415, "Could not determine hook type from Content-Type header.");
        }

        // Search for enabled jobs that should be triggered for the given hook
        List<AbstractProject<?, ?>> jobs = findJobsToTriggerForWebhook(hook);
        LOGGER.fine(String.format("Incoming webhook %s triggered %d job(s).", hook, jobs.size()));

        // Schedule a build for each of the jobs that matched
        for (AbstractProject<?, ?> job : jobs) {
            job.scheduleBuild2(0, new JsonTriggerCause(), new JsonTriggerBuildAction(hook));
        }

        if (jobs.size() > 0) {
        	return HttpResponses.plainText(Messages.TriggeredBuilds(jobs.size()));
        } else {
        	return HttpResponses.errorWithoutStack(501, Messages.TriggeredBuilds(jobs.size()));
        }
    }

    /** @return A list of jobs which should be triggered by the given webhook. */
    @Nonnull
    private static List<AbstractProject<?, ?>> findJobsToTriggerForWebhook(TriggerWebhook hook) {
        List<AbstractProject<?, ?>> jobsToTrigger = new ArrayList<AbstractProject<?, ?>>();

        // Run this block with system privileges so we can find and launch jobs that may require privileged user access
        SecurityContext old = ACL.impersonate(ACL.SYSTEM);
        try {
            for (AbstractProject<?, ?> job : Jenkins.getInstance().getAllItems(AbstractProject.class)) {
                // We're only interested in jobs configured with the JSON trigger
                JsonTrigger trigger = job.getTrigger(JsonTrigger.class);
                if (trigger == null) {
                    continue;
                }

                // Ignore disabled or not-yet-configured jobs
                if (!job.isBuildable()) {
                    continue;
                }

                // Check whether the given webhook satisfies the trigger's criteria
                if (trigger.accepts(job, hook)) {
                    jobsToTrigger.add(job);
                }
            }
        } finally {
            SecurityContextHolder.setContext(old);
        }

        return jobsToTrigger;
    }

    // Not needed; this is not a UI-facing Action

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }
}
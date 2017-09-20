package org.jenkinsci.plugins.webhooktrigger;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.UnprotectedRootAction;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.io.IOUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static hudson.Util.fixEmptyAndTrim;

/** HTTP endpoint for incoming webhooks. */
@Extension
public class TriggerEndpoint implements UnprotectedRootAction {

    private static final Logger LOGGER = Logger.getLogger(TriggerEndpoint.class.getName());
    
    private static final String APPLICATION_JSON = "application/json";
    private static final String APPLICATION_X_FORM_URLENCODED = "application/x-www-form-urlencoded";

    @Override
    public String getUrlName() {
        return "webhook";
    }

    @RequirePOST
    public HttpResponse doTrigger(final StaplerRequest req) throws IOException, ServletException {
        // Grab webhook payload from request body
    	
        // The Content-Type header should contain info about what type of webhook this is
        final String contentType = fixEmptyAndTrim(req.getHeader("Content-Type"));
        final String userAgent = req.getHeader("User-agent");
        if (contentType == null) {
            LOGGER.warning("Received hook without Content-Type header.");
            return HttpResponses.errorWithoutStack(415, "Could not determine hook type from Content-Type header.");
        }

        TriggerWebhook hook;
        try {
            LOGGER.info(String.format("Incoming webhook from user agent %s.", userAgent));
            LOGGER.info(String.format("Incoming webhook content-type: %s.", contentType));
            hook = decodeStream(req.getInputStream(), contentType);
            hook.setUserAgent(userAgent);
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Received hook with unsupported content type. " + e.getMessage());
            return HttpResponses.errorWithoutStack(400, "This endpoint expects a POST request with JSON body.");
        } catch (JsonParseException e) {
            LOGGER.warning("Received hook without JSON body. " + e.getMessage());
            return HttpResponses.errorWithoutStack(400, "This endpoint expects a POST request with JSON body.");
        } catch (IOException e) {
            LOGGER.warning("Failed to read webhook payload from request body: "+ e.getMessage());
            return HttpResponses.errorWithoutStack(400, "Failed to read webhook payload from request body.");
        }

        // Search for enabled jobs that should be triggered for the given hook
        final List<AbstractProject<?, ?>> jobs = findJobsToTriggerForWebhook(hook);
        LOGGER.fine(String.format("Incoming webhook %s triggered %d job(s).", hook, jobs.size()));

        // Schedule a build for each of the jobs that matched
        for (final AbstractProject<?, ?> job : jobs) {
            job.scheduleBuild2(0, new WebhookTriggerCause(), new WebhookTriggerBuildAction(hook));
        }

        if (jobs.size() > 0) {
        	return HttpResponses.plainText(Messages.TriggeredBuilds(jobs.size()));
        } else {
        	return HttpResponses.errorWithoutStack(501, Messages.TriggeredBuilds(jobs.size()));
        }
    }

    /** @return A list of jobs which should be triggered by the given webhook. */
    @Nonnull
    private static List<AbstractProject<?, ?>> findJobsToTriggerForWebhook(final TriggerWebhook hook) {
        final List<AbstractProject<?, ?>> jobsToTrigger = new ArrayList<AbstractProject<?, ?>>();

        // Run this block with system privileges so we can find and launch jobs that may require privileged user access
        final SecurityContext old = ACL.impersonate(ACL.SYSTEM);
        try {
            for (final AbstractProject<?, ?> job : Jenkins.getInstance().getAllItems(AbstractProject.class)) {
                // We're only interested in jobs configured with the JSON trigger
                final WebhookTrigger trigger = job.getTrigger(WebhookTrigger.class);
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
    
    /**
     * Based on contentType, decode the stream into a TriggerWebhook object.  IllegalArgumentException is thrown
     * if the content type is not supported.  JsonParseException is thrown if content type is "application/json"
     * and the data is poorly formed.
     * @param in Data stream from webhook
     * @param contentType Content type of stream
     * @return TriggerWebhook with decoded data from stream
     * @throws JsonParseException
     * @throws IOException
     * @throws IllegalArgumentException
     */
    public TriggerWebhook decodeStream(final InputStream in, final String contentType) throws JsonParseException, IOException, IllegalArgumentException {
    	TriggerWebhook hook;
    	final String encoding = "UTF-8";
    	final Charset charset = Charset.forName(encoding);
    	
    	final String parsedType = contentType.split(";")[0];
    	
    	if (APPLICATION_JSON.equals(parsedType)) {
    	    
    		hook = new ObjectMapper().readValue(in, TriggerWebhook.class);
    		
    	} else if (APPLICATION_X_FORM_URLENCODED.equals(parsedType)) {
    	    
    		hook = new TriggerWebhook();
    		
    		final List<NameValuePair> data = URLEncodedUtils.parse(IOUtils.toString(in, charset), charset);

    		for (final NameValuePair pair : data) {
    			hook.setOtherValue(pair.getName(), pair.getValue());
    		}
    		
    	} else {
    		throw new IllegalArgumentException(Messages.UnsupportedContentType(contentType));
    	}

        return hook;
    }
}
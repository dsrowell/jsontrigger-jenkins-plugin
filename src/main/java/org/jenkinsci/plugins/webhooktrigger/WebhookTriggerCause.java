package org.jenkinsci.plugins.webhooktrigger;

import hudson.model.Cause;

public class WebhookTriggerCause extends Cause {

	@Override
	public String getShortDescription() {
        // Text shown in the badge on the build page
		return Messages.Cause();
	}

}

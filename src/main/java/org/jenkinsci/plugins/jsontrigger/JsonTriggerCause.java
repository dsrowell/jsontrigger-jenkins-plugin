package org.jenkinsci.plugins.jsontrigger;

import hudson.model.Cause;

public class JsonTriggerCause extends Cause {

	@Override
	public String getShortDescription() {
        // Text shown in the badge on the build page
		return Messages.Cause();
	}

}

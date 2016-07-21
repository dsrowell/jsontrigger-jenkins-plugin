package org.jenkinsci.plugins.jsontrigger;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.HashMap;
import java.util.Map;

/** Represents a webhook sent from a external service in order to trigger Jenkins builds. */
public class TriggerWebhook {

	private String userAgent;
    private Map<String, Object> map;

    public TriggerWebhook() {
        this.map = new HashMap<String, Object>();
    }
    
    public String getUserAgent() {
    	return userAgent;
    }
    
    public void setUserAgent(final String userAgent) {
    	this.userAgent = userAgent;
    }
    
    /** Allows Jackson to set hook key/values we're not explicitly interested in. */
    @JsonAnySetter
    public void setOtherValue(String name, Object value) {
        map.put(name, value);
    }

    /** @return A map of the payload fields we're not explicitly interested in. */
    public Map<String, Object> getOtherValues() {
        return map;
    }

    @Override
    public String toString() {
        return String.format("Webhook{userAgent=%s}", userAgent);
    }

}

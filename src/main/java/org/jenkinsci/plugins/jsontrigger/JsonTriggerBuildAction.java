package org.jenkinsci.plugins.jsontrigger;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static hudson.Util.fixNull;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CaseFormat;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.EnvironmentContributingAction;

/** Contains the data required for a Webhook-triggered build, and exports it to the build environment. */
public class JsonTriggerBuildAction implements EnvironmentContributingAction {

    /** Prefix to apply to all environment variables this action exports. */
    static final String ENV_VAR_PREFIX = "HOOK_";

    private final TriggerWebhook hook;

    public JsonTriggerBuildAction(final TriggerWebhook hook) {
        this.hook = hook;
    }

    /** @return The webhook that triggered the build to which this action is attached. */
    public TriggerWebhook getHook() {
        return hook;
    }

    @Override
    public void buildEnvVars(final AbstractBuild<?, ?> build, final EnvVars env) {
        // Recursively export all other key/value pairs in the hook payload
        exportHookValues(env, hook.getOtherValues());
        final Map<String, Object> payload = new HashMap<String, Object>();
        try {
			payload.put("Payload", (new ObjectMapper()).writeValueAsString(getHook().getOtherValues()));
	        exportHookValues(env, payload);
		} catch (JsonProcessingException e) {
			// TODO
		}
    }

    private static void exportHookValues(final EnvVars env, final Map<String, Object> values) {
        exportHookValues(env, null, values);
    }

    /**
     * Recursively adds all key/values from the given map to the environment.
     *
     * @param env Environment to add to.
     * @param nestingPrefix Optional prefix to add to each key, if the values map contains a nested map.
     * @param values Key/value pairs to be added to the environment.
     */
    @SuppressWarnings("unchecked")
    private static void exportHookValues(final EnvVars env, String nestingPrefix, final Map<String, Object> values) {
        if (env == null || values == null) {
            return;
        }

        nestingPrefix = fixNull(nestingPrefix).trim();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                exportHookValues(env, nestingPrefix + entry.getKey() + "_", (Map<String, Object>) value);
            } else {
                env.put(getEnvKey(entry.getKey(), nestingPrefix), String.valueOf(value));
            }
        }
    }

    /**
     * Turns a key and optional prefix into an environment variable name.
     * <p/>
     * Values in {@code camelCase} will be converted to be {@code UNDERSCORE_SEPARATED}.
     *
     * @param key Value, possibly camel-cased.
     * @param nestingPrefix Optional string prefix, possibly camel-cased, e.g. {@code artifact} or {@code artifactInfo}.
     * @return A value prefixed with {@link #ENV_VAR_PREFIX}, e.g. given the parameters {@code key=buildId} and
     *         {@code nestingPrefix=artifact}, {@code DDB_ARTIFACT_BUILD_ID} would be returned.
     */
    private static String getEnvKey(final String key, String nestingPrefix) {
        nestingPrefix = fixNull(nestingPrefix).trim();
        return ENV_VAR_PREFIX + LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, nestingPrefix + key);
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

    @Override
    public String getUrlName() {
        return null;
    }

}

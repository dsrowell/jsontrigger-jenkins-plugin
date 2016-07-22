package jsontrigger;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

import org.jenkinsci.plugins.jsontrigger.TriggerEndpoint;
import org.jenkinsci.plugins.jsontrigger.TriggerWebhook;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;

public class TriggerEndpointTest {

	@Test(expected=IllegalArgumentException.class)
	public void testDecodeStreamNoContentType() throws JsonParseException, IOException {
		
		new TriggerEndpoint().decodeStream(new ByteArrayInputStream("stream".getBytes()), "");
	}

	@Test
	public void testDecodeStreamValidJson() throws JsonParseException, IOException {
		
		final TriggerEndpoint triggerEndpoint = new TriggerEndpoint();

		TriggerWebhook hook = triggerEndpoint.decodeStream(this.getClass().getResourceAsStream("/payload.json"), "application/json");

		final Map<String, Object> values = hook.getOtherValues();
		assertTrue("Expected json to contain object", values.keySet().contains("object"));
	}

	@Test
	public void testDecodeStreamValidFormData() throws JsonParseException, IOException {
		
		final TriggerEndpoint triggerEndpoint = new TriggerEndpoint();

		TriggerWebhook hook = triggerEndpoint.decodeStream(this.getClass().getResourceAsStream("/payload.form"), "application/x-www-form-urlencoded");

		final Map<String, Object> values = hook.getOtherValues();
		for (final String key : values.keySet()) {
			System.out.println(key + ": " + values.get(key));
		}
		assertTrue("Expected form to contain subject", values.keySet().contains("subject"));
	}

}

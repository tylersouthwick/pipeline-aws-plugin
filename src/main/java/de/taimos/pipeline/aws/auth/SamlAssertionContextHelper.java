package de.taimos.pipeline.aws.auth;

import de.taimos.pipeline.aws.AWSClientFactory;

import java.util.HashMap;
import java.util.Map;

public class SamlAssertionContextHelper implements AuthContextUpdater {

	@Override
	public Map<String, String> environmentVariables() {
		return new HashMap<String, String>() {
			{
				put(AWSClientFactory.AWS_ACCESS_KEY_ID, "access_key_not_used_will_pass_through_SAML_assertion");
				put(AWSClientFactory.AWS_SECRET_ACCESS_KEY, "secret_access_key_not_used_will_pass_through_SAML_assertion");
			}
		};
	}

}

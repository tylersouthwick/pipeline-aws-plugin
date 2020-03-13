package de.taimos.pipeline.aws.auth;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.services.securitytoken.model.GetFederationTokenRequest;
import com.amazonaws.services.securitytoken.model.GetFederationTokenResult;
import de.taimos.pipeline.aws.AWSClientFactory;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class FederatedUserIdCredentialProvider implements AWSCredentialsProvider  {

	@NonNull
	AWSSecurityTokenService sts;
	@NonNull
	Integer duration;
	@NonNull
	String federatedUserId;

	private static final String ALLOW_ALL_POLICY = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Action\":\"*\","
			+ "\"Effect\":\"Allow\",\"Resource\":\"*\"}]}";

	@Override
	public AWSCredentials getCredentials() {
		GetFederationTokenRequest getFederationTokenRequest = new GetFederationTokenRequest();
		getFederationTokenRequest.setDurationSeconds(this.getDuration());
		getFederationTokenRequest.setName(this.getFederatedUserId());
		getFederationTokenRequest.setPolicy(ALLOW_ALL_POLICY);

		GetFederationTokenResult federationTokenResult = sts.getFederationToken(getFederationTokenRequest);

		Credentials credentials = federationTokenResult.getCredentials();
		return new AWSSessionCredentials() {
			@Override
			public String getSessionToken() {
				return credentials.getSessionToken();
			}

			@Override
			public String getAWSAccessKeyId() {
				return credentials.getAccessKeyId();
			}

			@Override
			public String getAWSSecretKey() {
				return credentials.getSecretAccessKey();
			}
		};
	}

	@Override
	public void refresh() {

	}
}

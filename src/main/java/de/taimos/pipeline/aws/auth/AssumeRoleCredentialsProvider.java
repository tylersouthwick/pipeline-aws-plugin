package de.taimos.pipeline.aws.auth;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.utils.AssumedRole;
import hudson.EnvVars;
import hudson.model.TaskListener;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import java.util.function.Supplier;

@Value
@Builder
public class AssumeRoleCredentialsProvider implements AuthContextProvider {

	@NonNull
	AssumedRole.AssumeRole assumeRole;
	@NonNull
	AWSSecurityTokenService sts;

	@Override
	public AWSCredentialsProvider getCredentialsProvider(StepContext stepContext, EnvVars localEnv) throws Exception {
		stepContext.get(TaskListener.class).getLogger().format("Requesting assume role");
		AssumedRole assumedRole = assumeRole.assumedRole(sts);
		stepContext.get(TaskListener.class).getLogger().format("Assumed role %s with id %s %n ", assumedRole.getAssumedRoleUser().getArn(), assumedRole.getAssumedRoleUser().getAssumedRoleId());

		localEnv.override(AWSClientFactory.AWS_ACCESS_KEY_ID, assumedRole.getCredentials().getAccessKeyId());
		localEnv.override(AWSClientFactory.AWS_SECRET_ACCESS_KEY, assumedRole.getCredentials().getSecretAccessKey());
		localEnv.override(AWSClientFactory.AWS_SESSION_TOKEN, assumedRole.getCredentials().getSessionToken());

		AssumedRole assumedRole = assumeRole.assumedRole(this.sts);
		return new AWSSessionCredentials() {
			@Override
			public String getSessionToken() {
				return assumedRole.getCredentials().getSessionToken();
			}

			@Override
			public String getAWSAccessKeyId() {
				return assumedRole.getCredentials().getAccessKeyId();
			}

			@Override
			public String getAWSSecretKey() {
				return assumedRole.getCredentials().getSecretAccessKey();
			}
		};
	}
}

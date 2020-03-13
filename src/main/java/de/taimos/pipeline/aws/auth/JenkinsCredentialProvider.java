package de.taimos.pipeline.aws.auth;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.util.StringUtils;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.model.Run;
import hudson.model.TaskListener;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.io.PrintStream;
import java.util.Collections;

@Value
@Builder
public class JenkinsCredentialProvider implements AWSCredentialsProvider {

	@NonNull
	String credentialsId;
	@NonNull
	Run<?, ?> run;
	@NonNull
	PrintStream logger;
	String iamMfaToken;

	@Override
	public AWSCredentials getCredentials() {
		StandardUsernamePasswordCredentials usernamePasswordCredentials = CredentialsProvider.findCredentialById(this.getCredentialsId(),
				StandardUsernamePasswordCredentials.class, run, Collections.emptyList());

		AmazonWebServicesCredentials amazonWebServicesCredentials = CredentialsProvider.findCredentialById(this.getCredentialsId(),
				AmazonWebServicesCredentials.class, run, Collections.emptyList());
		if (usernamePasswordCredentials != null) {
			return new BasicAWSCredentials(usernamePasswordCredentials.getUsername(), usernamePasswordCredentials.getPassword().getPlainText());
		} else if (amazonWebServicesCredentials != null) {
			if (StringUtils.isNullOrEmpty(this.getIamMfaToken())) {
				this.getLogger().format("Constructing AWS Credentials");
				return amazonWebServicesCredentials.getCredentials();
			} else {
				// Since the getCredentials does its own roleAssumption, this is all it takes to get credentials
				// with this token.
				this.getLogger().format("Constructing AWS Credentials utilizing MFA Token");
				return amazonWebServicesCredentials.getCredentials(this.getIamMfaToken());
			}
		} else {
			throw new RuntimeException("Cannot find a Username with password credential with the ID " + this.getCredentials());
		}
	}

	@Override
	public void refresh() {

	}
}

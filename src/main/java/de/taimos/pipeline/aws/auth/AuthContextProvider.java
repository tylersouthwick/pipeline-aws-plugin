package de.taimos.pipeline.aws.auth;

import com.amazonaws.auth.AWSCredentialsProvider;
import hudson.EnvVars;
import org.jenkinsci.plugins.workflow.steps.StepContext;

public interface AuthContextProvider {

    AWSCredentialsProvider getCredentialsProvider(StepContext stepContext, EnvVars envVars) throws Exception;
}

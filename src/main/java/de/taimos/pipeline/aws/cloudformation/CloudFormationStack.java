/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2016 Taimos GmbH
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package de.taimos.pipeline.aws.cloudformation;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException;
import com.amazonaws.services.cloudformation.model.Capability;
import com.amazonaws.services.cloudformation.model.ChangeSetType;
import com.amazonaws.services.cloudformation.model.CreateChangeSetRequest;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.DeleteChangeSetRequest;
import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.cloudformation.model.DescribeChangeSetRequest;
import com.amazonaws.services.cloudformation.model.DescribeChangeSetResult;
import com.amazonaws.services.cloudformation.model.DescribeStackDriftDetectionStatusRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackDriftDetectionStatusResult;
import com.amazonaws.services.cloudformation.model.DescribeStackResourceDriftsRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackResourceDriftsResult;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.DetectStackDriftRequest;
import com.amazonaws.services.cloudformation.model.DetectStackDriftResult;
import com.amazonaws.services.cloudformation.model.ExecuteChangeSetRequest;
import com.amazonaws.services.cloudformation.model.OnFailure;
import com.amazonaws.services.cloudformation.model.Output;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.RollbackConfiguration;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackDriftStatus;
import com.amazonaws.services.cloudformation.model.StackResourceDrift;
import com.amazonaws.services.cloudformation.model.StackResourceDriftStatus;
import com.amazonaws.services.cloudformation.model.Tag;
import com.amazonaws.services.cloudformation.model.UpdateStackRequest;
import com.amazonaws.waiters.Waiter;
import hudson.model.TaskListener;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class CloudFormationStack {

	private final AmazonCloudFormation client;
	private final String stack;
	private final TaskListener listener;
	private final CustomCloudformationWaiters waiters;

	public CloudFormationStack(AmazonCloudFormation client, String stack, TaskListener listener) {
		this.client = client;
		this.stack = stack;
		this.listener = listener;
		this.waiters = CustomCloudformationWaiters.of(client);
	}

	public boolean exists() {
		try {
			DescribeStacksResult result = this.client.describeStacks(new DescribeStacksRequest().withStackName(this.stack));
			return !result.getStacks().isEmpty();
		} catch (AmazonCloudFormationException e) {
			if ("AccessDenied".equals(e.getErrorCode())) {
				this.listener.getLogger().format("Got error from describeStacks: %s %n", e.getErrorMessage());
				throw e;
			} else if ("ValidationError".equals(e.getErrorCode()) && e.getErrorMessage().contains("does not exist")) {
				return false;
			} else {
				throw e;
			}
		}
	}

	public boolean changeSetExists(String changeSetName) {
		try {
			this.client.describeChangeSet(new DescribeChangeSetRequest().withStackName(this.stack).withChangeSetName(changeSetName));
			return true;
		} catch (AmazonCloudFormationException e) {
			if ("AccessDenied".equals(e.getErrorCode())) {
				this.listener.getLogger().format("Got error from describeStacks: %s %n", e.getErrorMessage());
				throw e;
			}
			return false;
		}
	}

	private boolean changeSetHasChanges(String changeSetName) {
		DescribeChangeSetResult result = this.client.describeChangeSet(new DescribeChangeSetRequest().withStackName(this.stack).withChangeSetName(changeSetName));
		return !result.getChanges().isEmpty();
	}

	public Map<String, String> describeOutputs() {
		DescribeStacksResult result = this.client.describeStacks(new DescribeStacksRequest().withStackName(this.stack));
		Stack cfnStack = result.getStacks().get(0);
		Map<String, String> map = new HashMap<>();
		for (Output output : cfnStack.getOutputs()) {
			map.put(output.getOutputKey(), output.getOutputValue());
		}
		return map;
	}

	public void create(String templateBody, String templateUrl, Collection<Parameter> params, Collection<Tag> tags, PollConfiguration pollConfiguration, String roleArn, String onFailure, Boolean enableTerminationProtection) throws ExecutionException {
		if ((templateBody == null || templateBody.isEmpty()) && (templateUrl == null || templateUrl.isEmpty())) {
			throw new IllegalArgumentException("Either a file or url for the template must be specified");
		}

		CreateStackRequest req = new CreateStackRequest();
		req.withStackName(this.stack).withCapabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM).withEnableTerminationProtection(enableTerminationProtection);
		req.withTemplateBody(templateBody).withTemplateURL(templateUrl).withParameters(params).withTags(tags)
				.withTimeoutInMinutes(pollConfiguration.getTimeout() == null ? null : (int) pollConfiguration.getTimeout().toMinutes())
				.withRoleARN(roleArn)
				.withOnFailure(OnFailure.valueOf(onFailure));
		this.client.createStack(req);

		new EventPrinter(this.client, this.listener).waitAndPrintStackEvents(this.stack, this.client.waiters().stackCreateComplete(), pollConfiguration);
	}


	public void update(String templateBody, String templateUrl, Collection<Parameter> params, Collection<Tag> tags, PollConfiguration pollConfiguration, String roleArn, RollbackConfiguration rollbackConfig) throws ExecutionException {
		try {
			UpdateStackRequest req = new UpdateStackRequest();
			req.withStackName(this.stack).withCapabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM);

			if (templateBody != null && !templateBody.isEmpty()) {
				req.setTemplateBody(templateBody);
			} else if (templateUrl != null && !templateUrl.isEmpty()) {
				req.setTemplateURL(templateUrl);
			} else {
				req.setUsePreviousTemplate(true);
			}

			req.withRollbackConfiguration(rollbackConfig);

			req.withParameters(params).withTags(tags).withRoleARN(roleArn);

			this.client.updateStack(req);

			new EventPrinter(this.client, this.listener).waitAndPrintStackEvents(this.stack, this.client.waiters().stackUpdateComplete(), pollConfiguration);

			this.listener.getLogger().format("Updated CloudFormation stack %s %n", this.stack);

		} catch (AmazonCloudFormationException e) {
			if (e.getMessage().contains("No updates are to be performed")) {
				this.listener.getLogger().format("No updates were needed for CloudFormation stack %s %n", this.stack);
				return;
			}
			this.listener.getLogger().format("Failed to update CloudFormation stack %s %n", this.stack);
			throw e;
		}
	}

	public void createChangeSet(String changeSetName, String templateBody, String templateUrl, Collection<Parameter> params, Collection<Tag> tags, PollConfiguration pollConfiguration, ChangeSetType changeSetType, String roleArn, RollbackConfiguration rollbackConfig) throws ExecutionException {
		try {
			CreateChangeSetRequest req = new CreateChangeSetRequest();
			req.withChangeSetName(changeSetName).withStackName(this.stack).withCapabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM).withChangeSetType(changeSetType);

			if (ChangeSetType.CREATE.equals(changeSetType)) {
				this.listener.getLogger().format("Creating CloudFormation change set %s for new stack %s %n", changeSetName, this.stack);
				if ((templateBody == null || templateBody.isEmpty()) && (templateUrl == null || templateUrl.isEmpty())) {
					throw new IllegalArgumentException("Either a file or url for the template must be specified");
				}
				req.withTemplateBody(templateBody).withTemplateURL(templateUrl);
			} else if (ChangeSetType.UPDATE.equals(changeSetType)) {
				this.listener.getLogger().format("Creating CloudFormation change set %s for existing stack %s %n", changeSetName, this.stack);
				if (templateBody != null && !templateBody.isEmpty()) {
					req.setTemplateBody(templateBody);
				} else if (templateUrl != null && !templateUrl.isEmpty()) {
					req.setTemplateURL(templateUrl);
				} else {
					req.setUsePreviousTemplate(true);
				}
			} else {
				throw new IllegalArgumentException("Cannot create a CloudFormation change set without a valid change set type.");
			}

			req.withParameters(params).withTags(tags).withRoleARN(roleArn).withRollbackConfiguration(rollbackConfig);

			this.client.createChangeSet(req);

			new EventPrinter(this.client, this.listener).waitAndPrintChangeSetEvents(this.stack, changeSetName, this.client.waiters().changeSetCreateComplete(), pollConfiguration);

			this.listener.getLogger().format("Created CloudFormation change set %s for stack %s %n", changeSetName, this.stack);

		} catch (ExecutionException e) {
			try {
				if (this.changeSetExists(changeSetName) && !this.changeSetHasChanges(changeSetName)) {
					// Ignore the failed creation of a change set with no changes.
					this.listener.getLogger().format("Created empty change set %s for stack %s %n", changeSetName, this.stack);
					return;
				}
			} catch (Throwable throwable) {
				e.addSuppressed(throwable);
			}
			this.listener.getLogger().format("Failed to create CloudFormation change set %s for stack %s %n", changeSetName, this.stack);
			throw e;
		}
	}

	public void executeChangeSet(String changeSetName, PollConfiguration pollConfiguration) throws ExecutionException {
		if (!this.changeSetHasChanges(changeSetName) || !this.exists()) {
			// If the change set has no changes or the stack was not prepared we should simply delete it.
			this.listener.getLogger().format("Deleting empty change set %s for stack %s %n", changeSetName, this.stack);
			DeleteChangeSetRequest req = new DeleteChangeSetRequest().withChangeSetName(changeSetName).withStackName(this.stack);
			this.client.deleteChangeSet(req);
		} else {
			this.listener.getLogger().format("Executing change set %s for stack %s %n", changeSetName, this.stack);

			final Waiter<DescribeStacksRequest> waiter;
			if (this.isInReview()) {
				waiter = this.client.waiters().stackCreateComplete();
			} else {
				waiter = this.client.waiters().stackUpdateComplete();
			}

			ExecuteChangeSetRequest req = new ExecuteChangeSetRequest().withChangeSetName(changeSetName).withStackName(this.stack);
			this.client.executeChangeSet(req);
			new EventPrinter(this.client, this.listener).waitAndPrintStackEvents(this.stack, waiter, pollConfiguration);
			this.listener.getLogger().format("Executed change set %s for stack %s %n", changeSetName, this.stack);
		}
	}

	public void delete(PollConfiguration pollConfiguration) throws ExecutionException {
		this.client.deleteStack(new DeleteStackRequest().withStackName(this.stack));
		new EventPrinter(this.client, this.listener).waitAndPrintStackEvents(this.stack, this.client.waiters().stackDeleteComplete(), pollConfiguration);
	}

	public DescribeChangeSetResult describeChangeSet(String changeSet) {
		return this.client.describeChangeSet(new DescribeChangeSetRequest()
				.withStackName(this.stack)
				.withChangeSetName(changeSet)
		);
	}

	DescribeStackDriftDetectionStatusResult findStackDrift(PollConfiguration pollConfiguration) {
		DetectStackDriftResult result = this.client.detectStackDrift(new DetectStackDriftRequest()
				.withStackName(this.stack)
		);
		new EventPrinter(this.client, this.listener).waitForDriftDetection(result.getStackDriftDetectionId(), this.waiters.stackDriftComplete(), pollConfiguration);
		DescribeStackDriftDetectionStatusResult statusResult = this.client.describeStackDriftDetectionStatus(new DescribeStackDriftDetectionStatusRequest().withStackDriftDetectionId(result.getStackDriftDetectionId()));
		if (statusResult.getStackDriftStatus().equals(StackDriftStatus.DRIFTED.name())) {
			this.listener.getLogger().format("%s has drifted. Found %d resources in a drifted state %n", this.stack, statusResult.getDriftedStackResourceCount());
			for (StackResourceDrift stackResourceDrift : findStackResourceDrift()) {
				if (!stackResourceDrift.getStackResourceDriftStatus().equals(StackResourceDriftStatus.IN_SYNC.name())) {
					this.listener.getLogger().format("logicalId=%s status=%s", stackResourceDrift.getLogicalResourceId(), stackResourceDrift.getStackResourceDriftStatus());
				}
			}
		}
		return statusResult;
	}

	List<StackResourceDrift> findStackResourceDrift() {
		List<StackResourceDrift> list = new LinkedList<>();
		String nextToken = null;
		do {
			DescribeStackResourceDriftsResult resourceDriftsResult = this.client.describeStackResourceDrifts(new DescribeStackResourceDriftsRequest().withStackName(this.stack).withNextToken(nextToken));
			nextToken = resourceDriftsResult.getNextToken();
			list.addAll(resourceDriftsResult.getStackResourceDrifts());
		} while (nextToken != null);
		return list;
	}

	private boolean isInReview() {
		if (this.exists()) {
			DescribeStacksResult result = this.client.describeStacks(new DescribeStacksRequest().withStackName(this.stack));
			return !result.getStacks().isEmpty() && result.getStacks().get(0).getStackStatus().equals("REVIEW_IN_PROGRESS");
		}
		return false;
	}
}

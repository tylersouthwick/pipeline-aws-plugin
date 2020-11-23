package de.taimos.pipeline.aws.iam;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.AccessKey;
import com.amazonaws.services.identitymanagement.model.AccessKeyMetadata;
import com.amazonaws.services.identitymanagement.model.CreateAccessKeyRequest;
import com.amazonaws.services.identitymanagement.model.DeleteAccessKeyRequest;
import com.amazonaws.services.identitymanagement.model.ListAccessKeysRequest;
import com.amazonaws.services.identitymanagement.model.ListAccessKeysResult;
import groovy.lang.Closure;
import hudson.model.TaskListener;
import lombok.NonNull;
import lombok.Value;

import java.util.Optional;

import static java.util.Comparator.comparing;

@Value
public class CredentialsRotater {

	@NonNull
	AmazonIdentityManagement client;
	@NonNull
	TaskListener taskListener;

	void rotateCredentials(String username, Closure<Void> closure) {
		Optional<String> oldAccessKeyId = validateOnlyOneCredential(username);

		AccessKey newAccessKey = client.createAccessKey(new CreateAccessKeyRequest().withUserName(username)).getAccessKey();

		try {
			closure.call(newAccessKey);
		} catch (Exception e) {
			taskListener.error("Unable to update new access key. Deleting new access key", e);
			client.deleteAccessKey(new DeleteAccessKeyRequest().withUserName(username).withAccessKeyId(newAccessKey.getAccessKeyId()));
			return;
		}
		taskListener.getLogger().println("Updated new accessKey=" + newAccessKey.getAccessKeyId() + ".");

		if (oldAccessKeyId.isPresent()) {
			taskListener.getLogger().println("Deleting oldAccessKey=" + oldAccessKeyId);
			client.deleteAccessKey(new DeleteAccessKeyRequest().withUserName(username).withAccessKeyId(oldAccessKeyId.get()));
		}
	}

	private Optional<String> validateOnlyOneCredential(String username) {
		ListAccessKeysResult result = client.listAccessKeys(new ListAccessKeysRequest().withUserName(username));
		if (result.getAccessKeyMetadata().size() == 2) {
				String accessKeyId = result.getAccessKeyMetadata().stream().min(comparing(AccessKeyMetadata::getCreateDate))
					.map(AccessKeyMetadata::getAccessKeyId)
					.get();
				taskListener.getLogger().println("Found 2 active access keys for " + username + ". Deleting accessKeyId=" + accessKeyId);
				client.deleteAccessKey(new DeleteAccessKeyRequest().withAccessKeyId(accessKeyId).withUserName(username));
				return result.getAccessKeyMetadata().stream().max(comparing(AccessKeyMetadata::getCreateDate))
						.map(AccessKeyMetadata::getAccessKeyId);
		} else {
			return result.getAccessKeyMetadata().stream().findFirst().map(AccessKeyMetadata::getAccessKeyId);
		}
	}
}

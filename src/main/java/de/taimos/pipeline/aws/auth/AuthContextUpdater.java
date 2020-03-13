package de.taimos.pipeline.aws.auth;

import java.util.Map;

public interface AuthContextUpdater {
	Map<String, String> environmentVariables();
}

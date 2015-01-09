package org.junit.contrib.java.lang.system;

import static java.lang.System.getSecurityManager;
import static org.assertj.core.api.Assertions.assertThat;

import java.security.Permission;

import org.junit.Test;
import org.junit.runners.model.Statement;

public class ProvideSecurityManagerTest {
	private static final SecurityManager MANAGER = new SecurityManager() {
		@Override
		public void checkPermission(Permission perm) {
			// everything is allowed
		}
	};
	private static final Statement STATEMENT = new Statement() {
		@Override
		public void evaluate() throws Throwable {
			assertThat(getSecurityManager()).isSameAs(MANAGER);
		}
	};

	public ProvideSecurityManager rule = new ProvideSecurityManager(MANAGER);

	@Test
	public void provideProperty() throws Throwable {
		evaluateRuleWithStatement();
	}

	@Test
	public void restoreOriginalSecurityManager() throws Throwable {
		SecurityManager originalManager = getSecurityManager();
		evaluateRuleWithStatement();
		assertThat(getSecurityManager()).isSameAs(originalManager);
	}

	private void evaluateRuleWithStatement() throws Throwable {
		rule.apply(STATEMENT, null).evaluate();
	}
}

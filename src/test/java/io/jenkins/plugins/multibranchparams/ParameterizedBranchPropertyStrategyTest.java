package io.jenkins.plugins.multibranchparams;

import hudson.model.StringParameterDefinition;
import hudson.model.BooleanParameterDefinition;
import jenkins.branch.BranchProperty;
import jenkins.scm.api.SCMHead;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

/**
 * Pure unit tests for {@link ParameterizedBranchPropertyStrategy}.
 *
 * <p>No {@link org.jvnet.hudson.test.JenkinsRule} required – these run as plain JUnit tests
 * so they are very fast and suitable for CI pre-checks.
 */
public class ParameterizedBranchPropertyStrategyTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Creates a minimal anonymous {@link SCMHead} with the given name. */
    private static SCMHead head(String name) {
        return new SCMHead(name) { };
    }

    /** Creates a configured strategy with the given mode and pattern. */
    private ParameterizedBranchPropertyStrategy strategy(
            ParameterizedBranchPropertyStrategy.FilterMode mode, String pattern) {
        ParameterizedBranchPropertyStrategy s = new ParameterizedBranchPropertyStrategy();
        s.setFilterMode(mode);
        s.setBranchPattern(pattern);
        s.setParameterDefinitions(List.of(
                new StringParameterDefinition("ENV", "test", "Environment")));
        return s;
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    public void defaults_filterModeIsAll() {
        ParameterizedBranchPropertyStrategy s = new ParameterizedBranchPropertyStrategy();
        assertEquals(ParameterizedBranchPropertyStrategy.FilterMode.ALL, s.getFilterMode());
    }

    @Test
    public void defaults_patternIsMatchAll() {
        ParameterizedBranchPropertyStrategy s = new ParameterizedBranchPropertyStrategy();
        assertEquals(".*", s.getBranchPattern());
    }

    @Test
    public void defaults_policyIsReplace() {
        ParameterizedBranchPropertyStrategy s = new ParameterizedBranchPropertyStrategy();
        assertEquals(ParameterPolicy.REPLACE, s.getParameterPolicy());
    }

    // -------------------------------------------------------------------------
    // FilterMode.ALL
    // -------------------------------------------------------------------------

    @Test
    public void allMode_matchesMain() {
        assertThat(strategy(ParameterizedBranchPropertyStrategy.FilterMode.ALL, "n/a")
                .getPropertiesFor(head("main")), hasSize(1));
    }

    @Test
    public void allMode_matchesFeatureBranch() {
        assertThat(strategy(ParameterizedBranchPropertyStrategy.FilterMode.ALL, "n/a")
                .getPropertiesFor(head("feature/my-feature")), hasSize(1));
    }

    @Test
    public void allMode_matchesArbitraryName() {
        assertThat(strategy(ParameterizedBranchPropertyStrategy.FilterMode.ALL, "n/a")
                .getPropertiesFor(head("dependabot/npm_and_yarn/lodash-4.17.21")), hasSize(1));
    }

    // -------------------------------------------------------------------------
    // FilterMode.INCLUDE_PATTERN
    // -------------------------------------------------------------------------

    @Test
    public void includePattern_matchesMain() {
        assertThat(strategy(ParameterizedBranchPropertyStrategy.FilterMode.INCLUDE_PATTERN,
                "main|develop").getPropertiesFor(head("main")), hasSize(1));
    }

    @Test
    public void includePattern_matchesDevelop() {
        assertThat(strategy(ParameterizedBranchPropertyStrategy.FilterMode.INCLUDE_PATTERN,
                "main|develop").getPropertiesFor(head("develop")), hasSize(1));
    }

    @Test
    public void includePattern_doesNotMatchFeature() {
        assertThat(strategy(ParameterizedBranchPropertyStrategy.FilterMode.INCLUDE_PATTERN,
                "main|develop").getPropertiesFor(head("feature/login")), is(empty()));
    }

    @Test
    public void includePattern_featureWildcard_matchesFeature() {
        assertThat(strategy(ParameterizedBranchPropertyStrategy.FilterMode.INCLUDE_PATTERN,
                "feature/.*").getPropertiesFor(head("feature/checkout")), hasSize(1));
    }

    @Test
    public void includePattern_featureWildcard_doesNotMatchMain() {
        assertThat(strategy(ParameterizedBranchPropertyStrategy.FilterMode.INCLUDE_PATTERN,
                "feature/.*").getPropertiesFor(head("main")), is(empty()));
    }

    @Test
    public void includePattern_releaseWildcard_matchesRelease() {
        assertThat(strategy(ParameterizedBranchPropertyStrategy.FilterMode.INCLUDE_PATTERN,
                "release/.*").getPropertiesFor(head("release/1.5.0")), hasSize(1));
    }

    // -------------------------------------------------------------------------
    // FilterMode.EXCLUDE_PATTERN
    // -------------------------------------------------------------------------

    @Test
    public void excludePattern_excludesFeature() {
        assertThat(strategy(ParameterizedBranchPropertyStrategy.FilterMode.EXCLUDE_PATTERN,
                "feature/.*").getPropertiesFor(head("feature/login")), is(empty()));
    }

    @Test
    public void excludePattern_keepMain() {
        assertThat(strategy(ParameterizedBranchPropertyStrategy.FilterMode.EXCLUDE_PATTERN,
                "feature/.*").getPropertiesFor(head("main")), hasSize(1));
    }

    @Test
    public void excludePattern_keepHotfix() {
        assertThat(strategy(ParameterizedBranchPropertyStrategy.FilterMode.EXCLUDE_PATTERN,
                "feature/.*").getPropertiesFor(head("hotfix/critical-bug")), hasSize(1));
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    public void nullFilterMode_treatedAsAll() {
        ParameterizedBranchPropertyStrategy s = new ParameterizedBranchPropertyStrategy();
        s.setFilterMode(null);
        s.setParameterDefinitions(List.of(new StringParameterDefinition("X", "y", "")));
        assertThat(s.getPropertiesFor(head("anything")), hasSize(1));
    }

    @Test
    public void nullPattern_treatedAsMatchAll() {
        ParameterizedBranchPropertyStrategy s = new ParameterizedBranchPropertyStrategy();
        s.setFilterMode(ParameterizedBranchPropertyStrategy.FilterMode.INCLUDE_PATTERN);
        s.setBranchPattern(null);  // should default to ".*"
        s.setParameterDefinitions(List.of(new StringParameterDefinition("X", "y", "")));
        assertThat(s.getPropertiesFor(head("any-branch")), hasSize(1));
    }

    @Test
    public void malformedRegex_doesNotThrow() {
        // An invalid regex must not throw – strategy falls back to matching all
        ParameterizedBranchPropertyStrategy s = strategy(
                ParameterizedBranchPropertyStrategy.FilterMode.INCLUDE_PATTERN,
                "[invalid regex (((");
        List<BranchProperty> props = s.getPropertiesFor(head("anything"));
        // fallback = match-all, so INCLUDE_PATTERN → returns params
        assertThat(props, hasSize(1));
    }

    // -------------------------------------------------------------------------
    // Injected property payload
    // -------------------------------------------------------------------------

    @Test
    public void injectedProperty_isParameterizedBranchProperty() {
        ParameterizedBranchPropertyStrategy s =
                strategy(ParameterizedBranchPropertyStrategy.FilterMode.ALL, ".*");
        List<BranchProperty> props = s.getPropertiesFor(head("main"));
        assertThat(props.get(0), instanceOf(ParameterizedBranchProperty.class));
    }

    @Test
    public void injectedProperty_carriesCorrectParams() {
        ParameterizedBranchPropertyStrategy s = new ParameterizedBranchPropertyStrategy();
        s.setFilterMode(ParameterizedBranchPropertyStrategy.FilterMode.ALL);
        s.setParameterDefinitions(List.of(
                new StringParameterDefinition("DEPLOY_ENV", "staging", "Env"),
                new BooleanParameterDefinition("DRY_RUN", false, "Dry run")));
        s.setParameterPolicy(ParameterPolicy.MERGE_PLUGIN_WINS);

        List<BranchProperty> props = s.getPropertiesFor(head("main"));
        assertThat(props, hasSize(1));

        ParameterizedBranchProperty injected = (ParameterizedBranchProperty) props.get(0);
        assertThat(injected.getParameterDefinitions(), hasSize(2));
        assertEquals("DEPLOY_ENV", injected.getParameterDefinitions().get(0).getName());
        assertEquals("DRY_RUN", injected.getParameterDefinitions().get(1).getName());
        assertEquals(ParameterPolicy.MERGE_PLUGIN_WINS, injected.getParameterPolicy());
    }

    @Test
    public void noParamsDefined_returnsPropertyWithEmptyList() {
        ParameterizedBranchPropertyStrategy s = new ParameterizedBranchPropertyStrategy();
        s.setFilterMode(ParameterizedBranchPropertyStrategy.FilterMode.ALL);
        // No params set
        List<BranchProperty> props = s.getPropertiesFor(head("main"));
        assertThat(props, hasSize(1));
        ParameterizedBranchProperty injected = (ParameterizedBranchProperty) props.get(0);
        assertThat(injected.getParameterDefinitions(), is(empty()));
    }
}

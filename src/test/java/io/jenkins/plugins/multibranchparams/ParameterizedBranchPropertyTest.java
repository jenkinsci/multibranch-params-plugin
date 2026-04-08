package io.jenkins.plugins.multibranchparams;

import hudson.model.BooleanParameterDefinition;
import hudson.model.ChoiceParameterDefinition;
import hudson.model.ParameterDefinition;
import hudson.model.StringParameterDefinition;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.plugins.git.GitSCMSource;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link ParameterizedBranchProperty}.
 *
 * <p>Uses {@link WithJenkins} which spins up a real, temporary Jenkins instance.
 * These tests verify descriptor registration, config round-trips, and that
 * the property is correctly persisted and reloaded.
 */
@WithJenkins
class ParameterizedBranchPropertyTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private WorkflowMultiBranchProject createProject(JenkinsRule j,
            ParameterizedBranchProperty property) throws Exception {
        WorkflowMultiBranchProject project =
                j.createProject(WorkflowMultiBranchProject.class, "test-multibranch");

        GitSCMSource source = new GitSCMSource("https://github.com/example/repo.git");
        BranchSource branchSource = new BranchSource(source);
        branchSource.setStrategy(
                new DefaultBranchPropertyStrategy(new BranchProperty[]{property}));
        project.getSourcesList().add(branchSource);
        return project;
    }

    private ParameterizedBranchProperty roundTrip(JenkinsRule j,
            ParameterizedBranchProperty property) throws Exception {
        WorkflowMultiBranchProject project = createProject(j, property);
        j.configRoundtrip(project);

        DefaultBranchPropertyStrategy strategy =
                (DefaultBranchPropertyStrategy) project.getSourcesList().get(0).getStrategy();
        return (ParameterizedBranchProperty) strategy.getProps().get(0);
    }

    // -------------------------------------------------------------------------
    // Descriptor tests
    // -------------------------------------------------------------------------

    @Test
    void descriptor_isRegistered(JenkinsRule j) {
        ParameterizedBranchProperty.DescriptorImpl desc =
                j.jenkins.getDescriptorByType(ParameterizedBranchProperty.DescriptorImpl.class);
        assertNotNull(desc, "DescriptorImpl must be registered as a Jenkins extension");
    }

    @Test
    void descriptor_displayName(JenkinsRule j) {
        ParameterizedBranchProperty.DescriptorImpl desc =
                j.jenkins.getDescriptorByType(ParameterizedBranchProperty.DescriptorImpl.class);
        assertEquals("Branch Parameters", desc.getDisplayName());
    }

    @Test
    void descriptor_exposesParameterTypes(JenkinsRule j) {
        ParameterizedBranchProperty.DescriptorImpl desc =
                j.jenkins.getDescriptorByType(ParameterizedBranchProperty.DescriptorImpl.class);

        List<ParameterDefinition.ParameterDescriptor> paramDescs = desc.getParameterDescriptors();

        assertThat(paramDescs, is(not(empty())));
        assertTrue(paramDescs.stream().anyMatch(d -> d.clazz == StringParameterDefinition.class),
                "StringParameterDefinition must be listed");
        assertTrue(paramDescs.stream().anyMatch(d -> d.clazz == BooleanParameterDefinition.class),
                "BooleanParameterDefinition must be listed");
        assertTrue(paramDescs.stream().anyMatch(d -> d.clazz == ChoiceParameterDefinition.class),
                "ChoiceParameterDefinition must be listed");
    }

    // -------------------------------------------------------------------------
    // Config round-trip tests
    // -------------------------------------------------------------------------

    @Test
    void roundTrip_noParams(JenkinsRule j) throws Exception {
        ParameterizedBranchProperty prop = new ParameterizedBranchProperty();
        ParameterizedBranchProperty rt = roundTrip(j, prop);

        assertThat("Empty param list survives round-trip", rt.getParameterDefinitions(), is(empty()));
        assertEquals(ParameterPolicy.REPLACE, rt.getParameterPolicy(),
                "parameterPolicy defaults to REPLACE");
    }

    @Test
    void roundTrip_stringParam(JenkinsRule j) throws Exception {
        ParameterizedBranchProperty prop = new ParameterizedBranchProperty();
        prop.setParameterDefinitions(List.of(
                new StringParameterDefinition("DEPLOY_ENV", "staging", "Target environment")));
        prop.setParameterPolicy(ParameterPolicy.MERGE_PLUGIN_WINS);

        ParameterizedBranchProperty rt = roundTrip(j, prop);

        assertThat(rt.getParameterDefinitions(), hasSize(1));
        StringParameterDefinition param =
                (StringParameterDefinition) rt.getParameterDefinitions().get(0);
        assertEquals("DEPLOY_ENV", param.getName());
        assertEquals("staging", param.getDefaultValue());
        assertEquals("Target environment", param.getDescription());
        assertEquals(ParameterPolicy.MERGE_PLUGIN_WINS, rt.getParameterPolicy());
    }

    @Test
    void roundTrip_booleanParam(JenkinsRule j) throws Exception {
        ParameterizedBranchProperty prop = new ParameterizedBranchProperty();
        prop.setParameterDefinitions(List.of(
                new BooleanParameterDefinition("DRY_RUN", true, "Skip deployment")));

        ParameterizedBranchProperty rt = roundTrip(j, prop);

        assertThat(rt.getParameterDefinitions(), hasSize(1));
        BooleanParameterDefinition param =
                (BooleanParameterDefinition) rt.getParameterDefinitions().get(0);
        assertEquals("DRY_RUN", param.getName());
        assertTrue(param.isDefaultValue());
    }

    @Test
    void roundTrip_choiceParam(JenkinsRule j) throws Exception {
        ParameterizedBranchProperty prop = new ParameterizedBranchProperty();
        prop.setParameterDefinitions(List.of(
                new ChoiceParameterDefinition("REGION",
                        new String[]{"us-east-1", "eu-west-1", "ap-southeast-1"},
                        "AWS region")));

        ParameterizedBranchProperty rt = roundTrip(j, prop);

        assertThat(rt.getParameterDefinitions(), hasSize(1));
        ChoiceParameterDefinition param =
                (ChoiceParameterDefinition) rt.getParameterDefinitions().get(0);
        assertEquals("REGION", param.getName());
        assertThat(param.getChoices(), hasSize(3));
    }

    @Test
    void roundTrip_multipleParamTypes(JenkinsRule j) throws Exception {
        ParameterizedBranchProperty prop = new ParameterizedBranchProperty();
        prop.setParameterDefinitions(Arrays.asList(
                new StringParameterDefinition("VERSION", "1.0.0", ""),
                new BooleanParameterDefinition("DRY_RUN", false, ""),
                new ChoiceParameterDefinition("ENV", new String[]{"dev", "staging", "prod"}, "")
        ));

        ParameterizedBranchProperty rt = roundTrip(j, prop);

        List<ParameterDefinition> params = rt.getParameterDefinitions();
        assertThat(params, hasSize(3));
        assertThat(params.get(0), instanceOf(StringParameterDefinition.class));
        assertThat(params.get(1), instanceOf(BooleanParameterDefinition.class));
        assertThat(params.get(2), instanceOf(ChoiceParameterDefinition.class));
    }

    // -------------------------------------------------------------------------
    // jobDecorator tests (no Jenkins instance needed)
    // -------------------------------------------------------------------------

    @Test
    void jobDecorator_isNullWhenNoParams() {
        ParameterizedBranchProperty prop = new ParameterizedBranchProperty();
        assertThat(prop.jobDecorator(org.jenkinsci.plugins.workflow.job.WorkflowJob.class),
                is(nullValue()));
    }

    @Test
    void jobDecorator_isNonNullWhenParamsDefined() {
        ParameterizedBranchProperty prop = new ParameterizedBranchProperty();
        prop.setParameterDefinitions(List.of(
                new StringParameterDefinition("X", "y", "")));
        assertThat(prop.jobDecorator(org.jenkinsci.plugins.workflow.job.WorkflowJob.class),
                is(notNullValue()));
    }

    // -------------------------------------------------------------------------
    // Project-level registration test
    // -------------------------------------------------------------------------

    @Test
    void property_attachesToProject(JenkinsRule j) throws Exception {
        ParameterizedBranchProperty prop = new ParameterizedBranchProperty();
        prop.setParameterDefinitions(List.of(
                new StringParameterDefinition("MY_PARAM", "hello", "")));

        WorkflowMultiBranchProject project = createProject(j, prop);

        DefaultBranchPropertyStrategy strategy =
                (DefaultBranchPropertyStrategy) project.getSourcesList().get(0).getStrategy();

        assertThat(strategy.getProps(), hasSize(1));
        assertThat(strategy.getProps().get(0), instanceOf(ParameterizedBranchProperty.class));
    }
}
